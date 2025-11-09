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
uniform float u_blurRadius;
uniform float u_chromaticAberration;
uniform float u_refractionInset;

varying vec2 v_screenTexCoord;
varying vec2 v_shapeCoord;

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
    float normalized_dist = -dist_sample / transition_w;
    const float steepness_factor = 3.0;
    float height = 1.0 / (1.0 + exp(-normalized_dist * steepness_factor));
    float edgeFalloff = smoothstep(transition_w * 0.5, -transition_w * 0.5, dist_sample);
    height *= edgeFalloff;
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

vec3 gaussianBlurHorizontal(sampler2D tex, vec2 uv, vec2 offset, vec2 texelSize, float radiusPx, vec2 glassSize, vec2 halfSize, float cornerRadius, float smoothing, float inset, vec2 currentShapeCoord) {
    float r = min(radiusPx, 10.0);
    float rr = r * r;
    float w0 = 0.3780 / pow(r, 1.975);

    vec3 col = vec3(0.0);
    float totalWeight = 0.0;

    for (float x = -r; x <= r; x += 1.0) {
        float xx = x * x;

        if (xx <= rr) {
            vec2 sampleOffset = vec2(x * texelSize.x, 0.0);
            vec2 sampleUV = uv + offset + sampleOffset;
            sampleUV = clamp(sampleUV, vec2(0.0), vec2(1.0));

            vec2 offsetInShapeSpace = (sampleOffset / texelSize) / glassSize;
            vec2 sampleShapeCoord = currentShapeCoord + offsetInShapeSpace;

            float sampleOpacity = getShapeOpacity(sampleShapeCoord, glassSize, halfSize, cornerRadius, smoothing, inset);

            if (sampleOpacity > 0.0001) {
                float w = w0 * exp(-xx / (2.0 * rr));
                w *= sampleOpacity;
                vec3 src = texture2D(tex, sampleUV).rgb;
                col += src * w;
                totalWeight += w;
            }
        }
    }

    if (totalWeight <= 0.0001) {
        return texture2D(tex, clamp(uv + offset, vec2(0.0), vec2(1.0))).rgb;
    }

    return col / totalWeight;
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

    float height_val = getHeightFromSDF(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    vec2 gradient = computeSurfaceGradient(v_shapeCoord, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    vec3 surfaceNormal3D = normalize(vec3(-gradient.x * u_normalStrength, -gradient.y * u_normalStrength, 1.0));

    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    float fresnelFactor = fresnel(surfaceNormal3D, viewDir, u_ior);
    float refractionStrength = height_val * (0.5 + fresnelFactor * 0.3);

    vec3 refractedIn = refract(-viewDir, surfaceNormal3D, 1.0 / u_ior);
    vec3 refractedOut = refract(refractedIn, -surfaceNormal3D, u_ior);

    vec2 refractionOffset = refractedOut.xy * u_glassThickness * refractionStrength;
    vec2 baseOffset = (refractionOffset / u_resolution) * u_displacementScale;

    float minDim = min(u_glassSize.x, u_glassSize.y);
    float shadowExtent = mix(0.15, 0.60, clamp(0.2, 0.0, 1.0));
    float innerShadowFalloff = minDim * shadowExtent;

    float chromaEdgeFactor = smoothstep(innerShadowFalloff * 0.5, 0.0, edgeDistance);
    chromaEdgeFactor = pow(chromaEdgeFactor, 1.5);
    float chromaIntensity = u_chromaticAberration * 0.003 * chromaEdgeFactor;
    vec2 refractionDir = length(baseOffset) > 0.0001 ? normalize(baseOffset) : vec2(0.0);

    vec2 offsetR = baseOffset - refractionDir * chromaIntensity;
    vec2 offsetG = baseOffset;
    vec2 offsetB = baseOffset + refractionDir * chromaIntensity;

    vec2 texelSize = 1.0 / u_resolution;
    float blur = max(u_blurRadius, 1.0);

    vec3 cR = gaussianBlurHorizontal(u_backgroundTexture, v_screenTexCoord, offsetR, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);
    vec3 cG = gaussianBlurHorizontal(u_backgroundTexture, v_screenTexCoord, offsetG, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);
    vec3 cB = gaussianBlurHorizontal(u_backgroundTexture, v_screenTexCoord, offsetB, texelSize, blur, u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, inset, v_shapeCoord);

    vec3 refractedColor = vec3(cR.r, cG.g, cB.b);

    gl_FragColor = vec4(refractedColor, opacity);
}