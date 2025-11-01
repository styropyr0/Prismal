precision highp float;
uniform sampler2D u_backgroundTexture;
uniform vec2 u_resolution;
uniform vec2 u_glassSize;
uniform float u_cornerRadius;
uniform float u_ior;
uniform float u_glassThickness;
uniform float u_normalStrength;
uniform float u_displacementScale;
uniform float u_heightTransitionWidth;
uniform float u_sminSmoothing;
uniform int u_showNormals;
uniform float u_blurRadius;
uniform vec4 u_overlayColor;
uniform float u_highlightWidth;
uniform float u_chromaticAberration;
uniform float u_brightness;
varying vec2 v_screenTexCoord;
varying vec2 v_shapeCoord;
uniform float u_refractionInset;
uniform vec4 u_shadowColor;
uniform float u_shadowSoftness;
uniform float u_edgeRefractionFalloff;

float smin_polynomial(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax_polynomial(float a, float b, float k) {
    if (k <= 0.0) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
    return mix(b, a, h) + k * h * (1.0 - h);
}

float sdRoundedBoxSharp(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float sdRoundedBoxSmooth(vec2 p, vec2 b, float r, float k_smooth) {
    if (k_smooth <= 0.0) return sdRoundedBoxSharp(p,b,r);
    vec2 q = abs(p) - b + r;
    float termA_smooth = smax_polynomial(q.x, q.y, k_smooth);
    float termB_smooth = smin_polynomial(termA_smooth, 0.0, k_smooth * 0.5);
    vec2 q_for_length_smooth = vec2(
        smax_polynomial(q.x, 0.0, k_smooth),
        smax_polynomial(q.y, 0.0, k_smooth)
    );
    float termC_smooth = length(q_for_length_smooth);
    return termB_smooth + termC_smooth - r;
}

float getHeightFromSDF(vec2 p_pixel_space, vec2 b_pixel_space, float r_pixel, float k_s, float transition_w) {
    float dist_sample = sdRoundedBoxSmooth(p_pixel_space, b_pixel_space, r_pixel, k_s);
    float normalized_dist = dist_sample / transition_w;
    const float steepness_factor = 6.0;
    float height = 1.0 - (1.0 / (1.0 + exp(-normalized_dist * steepness_factor)));
    return clamp(height, 0.0, 1.0);
}

float fresnel(vec3 normal, vec3 viewDir, float ior) {
    float cosTheta = abs(dot(normal, viewDir));
    float r0 = pow((1.0 - ior) / (1.0 + ior), 2.0);
    return r0 + (1.0 - r0) * pow(1.0 - cosTheta, 5.0);
}

vec2 computeSurfaceGradient(vec2 shapeCoord, vec2 glassSize, vec2 halfSize, float radius, float smoothing, float transition) {
    vec2 pixelStep = 1.0 / glassSize;

    float h_px = getHeightFromSDF((shapeCoord + vec2(pixelStep.x, 0.0)) * glassSize, halfSize, radius, smoothing, transition);
    float h_nx = getHeightFromSDF((shapeCoord - vec2(pixelStep.x, 0.0)) * glassSize, halfSize, radius, smoothing, transition);
    float h_py = getHeightFromSDF((shapeCoord + vec2(0.0, pixelStep.y)) * glassSize, halfSize, radius, smoothing, transition);
    float h_ny = getHeightFromSDF((shapeCoord - vec2(0.0, pixelStep.y)) * glassSize, halfSize, radius, smoothing, transition);

    float grad_x = (h_px - h_nx) / (2.0 * pixelStep.x * glassSize.x);
    float grad_y = (h_py - h_ny) / (2.0 * pixelStep.y * glassSize.y);

    return vec2(grad_x, grad_y);
}

float getShapeOpacity(vec2 sampleCoord, vec2 glassSize, vec2 halfSize, float radius, float smoothing, float inset) {
    vec2 samplePixel = sampleCoord * glassSize;
    float dist = sdRoundedBoxSmooth(samplePixel, halfSize, radius, smoothing);
    float edgeDist = -dist;

    float opacity = 1.0 - smoothstep(-inset, 0.0, dist);
    float aa_feather = 1.5;
    opacity = mix(opacity, 1.0, smoothstep(0.0, aa_feather, edgeDist));

    return opacity;
}

vec3 blur9(sampler2D tex, vec2 uv, vec2 offset, vec2 texelSize, float radius, vec2 glassSize, vec2 halfSize, float cornerRadius, float smoothing, float inset, vec2 currentShapeCoord){
    vec3 accum = vec3(0.0);
    float weightSum = 0.0;

    vec2 dx = vec2(texelSize.x * radius, 0.0);
    vec2 dy = vec2(0.0, texelSize.y * radius);

    for(int y = -1; y <= 1; ++y){
        for(int x = -1; x <= 1; ++x){
            vec2 sampleOffset = float(x) * dx + float(y) * dy;
            vec2 sampleUV = uv + offset + sampleOffset;
            sampleUV = clamp(sampleUV, vec2(0.001), vec2(0.999));

            vec2 offsetInShapeSpace = sampleOffset / texelSize * u_resolution / glassSize;
            vec2 sampleShapeCoord = currentShapeCoord + offsetInShapeSpace;

            float sampleOpacity = getShapeOpacity(sampleShapeCoord, glassSize, halfSize, cornerRadius, smoothing, inset);

            if(sampleOpacity > 0.001) {
                accum += texture2D(tex, sampleUV).rgb * sampleOpacity;
                weightSum += sampleOpacity;
            }
        }
    }

    return weightSum > 0.001 ? accum / weightSum : vec3(0.0);
}

void main() {
    float actualCornerRadius = min(u_cornerRadius, min(u_glassSize.x, u_glassSize.y) / 2.0);

    vec2 current_p_pixel = v_shapeCoord * u_glassSize;
    vec2 glass_half_size_pixel = u_glassSize / 2.0;

    float dist_for_shape_boundary = sdRoundedBoxSmooth(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing);
    float edgeDistance = -dist_for_shape_boundary;

    float inset = max(u_refractionInset, 2.0);

    float opacity = 1.0 - smoothstep(-inset, 0.0, dist_for_shape_boundary);
    float aa_feather = 1.5;
    opacity = mix(opacity, 1.0, smoothstep(0.0, aa_feather, edgeDistance));

    if (opacity < 0.001) discard;

    float refractionDistance = min(u_glassSize.x, u_glassSize.y) * 0.4;
    float edgeProximity = clamp(edgeDistance / refractionDistance, 0.0, 1.0);
    float depthFalloff = 1.0 - pow(edgeProximity, u_edgeRefractionFalloff);

    vec2 gradient = computeSurfaceGradient(v_shapeCoord, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    vec3 surfaceNormal3D = normalize(vec3(-gradient.x * u_normalStrength, -gradient.y * u_normalStrength, 1.0));

    if (u_showNormals == 1) {
        gl_FragColor = vec4(surfaceNormal3D * 0.5 + 0.5, opacity);
        return;
    }

    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    float fresnelFactor = fresnel(surfaceNormal3D, viewDir, u_ior);

    vec2 centeredCoord = v_shapeCoord - vec2(0.5);

    float refractionStrength = depthFalloff * (0.3 + fresnelFactor * 0.1);

    vec3 refractedIn = refract(-viewDir, surfaceNormal3D, 1.0 / u_ior);
    vec3 refractedOut = refract(refractedIn, -surfaceNormal3D, u_ior);

    vec2 refractionOffset = refractedOut.xy * u_glassThickness * refractionStrength * 0.5;
    vec2 baseOffset = (refractionOffset / u_resolution) * u_displacementScale;

    float height_val = getHeightFromSDF(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);

    float minDim = min(u_glassSize.x, u_glassSize.y);

    float shadowExtent = mix(0.15, 0.60, clamp(u_shadowSoftness, 0.0, 1.0));
    float innerShadowFalloff = minDim * shadowExtent;

    float innerShadow = 1.0 - smoothstep(0.0, innerShadowFalloff, edgeDistance);
    innerShadow = pow(innerShadow, 2.0);
    innerShadow *= 0.85;
    innerShadow *= (0.3 + height_val * 0.7);

    float chromaIntensity = u_chromaticAberration * 0.002 * depthFalloff;
    vec2 refractionDir = length(baseOffset) > 0.0001 ? normalize(baseOffset) : vec2(0.0);

    vec2 offsetR = baseOffset - refractionDir * chromaIntensity;
    vec2 offsetG = baseOffset;
    vec2 offsetB = baseOffset + refractionDir * chromaIntensity;

    vec2 texelSize = 1.0 / u_resolution;
    float blur = u_blurRadius * 0.5;

    vec3 cR = blur9(u_backgroundTexture, v_screenTexCoord, offsetR, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);
    vec3 cG = blur9(u_backgroundTexture, v_screenTexCoord, offsetG, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);
    vec3 cB = blur9(u_backgroundTexture, v_screenTexCoord, offsetB, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);

    vec3 refractedColor = vec3(cR.r, cG.g, cB.b);

    vec3 brightenedColor = refractedColor * u_brightness;

    vec3 shadowColor = u_shadowColor.rgb * 0.3;
    float shadowAmount = innerShadow * u_shadowColor.a;
    vec3 finalColor = mix(brightenedColor, shadowColor, shadowAmount);

    float overlayStrength = height_val * 0.03;
    vec3 colorWithOverlay = mix(finalColor, u_overlayColor.rgb, overlayStrength);

    float outerEdgeHighlight = smoothstep(3.0, 0.0, edgeDistance) * smoothstep(-1.0, 0.0, dist_for_shape_boundary);
    outerEdgeHighlight *= (0.5 + fresnelFactor * 0.5);

    vec2 lightDir = normalize(vec2(-1.0, -1.0));
    float lightDot = dot(surfaceNormal3D.xy, lightDir);
    float directionalHighlight = smoothstep(-0.3, 0.5, lightDot) * outerEdgeHighlight;

    vec3 highlightColor = vec3(1.0);
    vec3 finalMixed = mix(colorWithOverlay, highlightColor, directionalHighlight * 0.5);

    gl_FragColor = vec4(finalMixed, opacity);
}