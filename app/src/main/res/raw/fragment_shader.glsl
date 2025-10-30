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
uniform vec2 u_shadowOffset;
uniform float u_shadowSoftness;

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

vec3 blur9(sampler2D tex, vec2 uv, vec2 offset, vec2 texelSize, float radius){
    const float kernelSize   = 3.0;
    const float halfSize     = 1.0;
    const float coefficient = 1.0/(kernelSize*kernelSize);

    vec3 screenColor = vec3(0.0);
    vec2 dx = vec2(1.0,0.0)*texelSize*radius;
    vec2 dy = vec2(0.0,1.0)*texelSize*radius;

    for(int y = -1; y <= 1; ++y){
        for(int x = -1; x <= 1; ++x){
            vec2 sampleUV = uv + offset + vec2(float(x),float(y))*(dx + dy);
            sampleUV = clamp(sampleUV, vec2(0.001), vec2(0.999));
            screenColor += texture2D(tex, sampleUV).rgb;
        }
    }
    return screenColor * coefficient;
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

    float refractionDistance = min(u_glassSize.x, u_glassSize.y) * 0.35;
    float edgeProximity = clamp(edgeDistance / refractionDistance, 0.0, 1.0);
    float depthFalloff = 1.0 - smoothstep(0.0, 1.0, edgeProximity);

    vec2 gradient = computeSurfaceGradient(v_shapeCoord, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    vec3 surfaceNormal3D = normalize(vec3(-gradient.x * u_normalStrength, -gradient.y * u_normalStrength, 1.0));

    if (u_showNormals == 1) {
        gl_FragColor = vec4(surfaceNormal3D * 0.5 + 0.5, opacity);
        return;
    }

    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    float fresnelFactor = fresnel(surfaceNormal3D, viewDir, u_ior);

    vec2 centeredCoord = v_shapeCoord - vec2(0.5);
    float distFromCenter = length(centeredCoord);

    float refractionStrength = depthFalloff * (0.8 + fresnelFactor * 0.2);

    vec3 refractedIn = refract(-viewDir, surfaceNormal3D, 1.0 / u_ior);
    vec3 refractedOut = refract(refractedIn, -surfaceNormal3D, u_ior);

    vec2 refractionOffset = refractedOut.xy * u_glassThickness * refractionStrength;
    vec2 baseOffset = (refractionOffset / u_resolution) * u_displacementScale;

    float chromaIntensity = u_chromaticAberration * 0.001 * depthFalloff;
    vec2 refractionDir = length(baseOffset) > 0.0001 ? normalize(baseOffset) : vec2(0.0);

    vec3 refractR_in = refract(-viewDir, surfaceNormal3D, 1.0 / (u_ior - 0.012));
    vec3 refractR_out = refract(refractR_in, -surfaceNormal3D, u_ior - 0.012);
    vec2 offsetR = (refractR_out.xy * u_glassThickness * refractionStrength / u_resolution) * u_displacementScale;
    offsetR -= refractionDir * chromaIntensity;

    vec2 offsetG = baseOffset;

    vec3 refractB_in = refract(-viewDir, surfaceNormal3D, 1.0 / (u_ior + 0.012));
    vec3 refractB_out = refract(refractB_in, -surfaceNormal3D, u_ior + 0.012);
    vec2 offsetB = (refractB_out.xy * u_glassThickness * refractionStrength / u_resolution) * u_displacementScale;
    offsetB += refractionDir * chromaIntensity;

    vec2 texelSize = 1.0 / u_resolution;
    float blur = u_blurRadius * 0.75;

    vec3 cR = blur9(u_backgroundTexture, v_screenTexCoord, offsetR, texelSize, blur);
    vec3 cG = blur9(u_backgroundTexture, v_screenTexCoord, offsetG, texelSize, blur);
    vec3 cB = blur9(u_backgroundTexture, v_screenTexCoord, offsetB, texelSize, blur);

    vec3 finalColor = vec3(cR.r, cG.g, cB.b) * u_brightness;

    float height_val = getHeightFromSDF(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float overlayStrength = height_val * 0.06 * (1.0 - depthFalloff * 0.3);
    vec3 colorWithOverlay = mix(finalColor, u_overlayColor.rgb, overlayStrength);

    float highlight_dist = abs(dist_for_shape_boundary);
    float highlight_alpha = (1.0 - smoothstep(0.0, u_highlightWidth, highlight_dist));

    float normalDot = dot(surfaceNormal3D.xy, normalize(centeredCoord + vec2(0.3, 0.3)));
    float directionalFactor = 0.5 + 0.5 * normalDot;

    float finalHighlightAlpha = highlight_alpha * directionalFactor * (0.6 + fresnelFactor * 0.4);

    vec3 highlightColor = vec3(1.0);
    vec3 finalMixed = mix(colorWithOverlay, highlightColor, finalHighlightAlpha * 0.7);

    gl_FragColor = vec4(finalMixed, opacity);
}