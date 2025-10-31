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

// NOTE: we keep a simple 3x3 blur but ensure dx/dy are per-axis (no diagonal-only sampling)
vec3 blur9_fixed(sampler2D tex, vec2 uv, vec2 offset, vec2 texelSize, float radius){
    const float coeff = 1.0 / 9.0;
    vec3 accum = vec3(0.0);
    vec2 dx = vec2(texelSize.x * radius, 0.0);
    vec2 dy = vec2(0.0, texelSize.y * radius);
    for(int y = -1; y <= 1; ++y){
        for(int x = -1; x <= 1; ++x){
            vec2 sampleUV = uv + offset + float(x) * dx + float(y) * dy;
            sampleUV = clamp(sampleUV, vec2(0.001), vec2(0.999));
            accum += texture2D(tex, sampleUV).rgb;
        }
    }
    return accum * coeff;
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
    float distFromCenter = length(centeredCoord);

    float refractionStrength = depthFalloff * (0.8 + fresnelFactor * 0.2);

    vec3 refractedIn = refract(-viewDir, surfaceNormal3D, 1.0 / u_ior);
    vec3 refractedOut = refract(refractedIn, -surfaceNormal3D, u_ior);

    vec2 refractionOffset = refractedOut.xy * u_glassThickness * refractionStrength;
    vec2 baseOffset = (refractionOffset / u_resolution) * u_displacementScale;

    // --- SHADOW / CHROMA MASK COMPUTATION (robust) ---
    float minDim = min(u_glassSize.x, u_glassSize.y);
    float shadowDistancePx;
    if (u_shadowSoftness <= 1.0) {
        // treat as fraction of half-min-dimension
        shadowDistancePx = clamp(u_shadowSoftness * (minDim * 0.5), 1.0, minDim * 0.5);
    } else {
        // treat as pixel amount
        shadowDistancePx = clamp(u_shadowSoftness, 1.0, minDim * 0.5);
    }

    // shadowMask: 1.0 near inner edge, fades smoothly to 0.0 toward center
    float shadowMask = 1.0 - smoothstep(0.0, shadowDistancePx, edgeDistance);
    shadowMask = clamp(shadowMask, 0.0, 1.0);

    // optional extra feathering for chroma (slightly softer than shadow)
    float chromaFeather = 1.15; // >1.0 makes chroma fade a bit more gradually

// Chromatic aberration with smooth falloff: ramp up from edge, then ramp down toward center
    float chromaFeatherDist = shadowDistancePx * 1.5; // Transition zone

    // Ramp up from outer edge
    float chromaRampUp = smoothstep(0.0, chromaFeatherDist, edgeDistance);

    // Ramp down as you go toward center
    float chromaRampDown = 1.0 - smoothstep(chromaFeatherDist, minDim * 0.35, edgeDistance);

    // Combine: starts at 0, peaks in middle zone, fades back to 0 at center
    float chromaMask = chromaRampUp * chromaRampDown;
    chromaMask = clamp(chromaMask, 0.0, 1.0);

    // Chromatic aberration base intensity scaled by depthFalloff
    float chromaIntensity = u_chromaticAberration * 0.003 * depthFalloff;

    vec2 refractionDir = length(baseOffset) > 0.0001 ? normalize(baseOffset) : vec2(0.0);

    vec3 refractR_in = refract(-viewDir, surfaceNormal3D, 1.0 / (u_ior - 0.012));
    vec3 refractR_out = refract(refractR_in, -surfaceNormal3D, u_ior - 0.012);
    vec2 offsetR_raw = (refractR_out.xy * u_glassThickness * refractionStrength / u_resolution) * u_displacementScale;
    offsetR_raw -= refractionDir * chromaIntensity;

    vec2 offsetG = baseOffset;

    vec3 refractB_in = refract(-viewDir, surfaceNormal3D, 1.0 / (u_ior + 0.012));
    vec3 refractB_out = refract(refractB_in, -surfaceNormal3D, u_ior + 0.012);
    vec2 offsetB_raw = (refractB_out.xy * u_glassThickness * refractionStrength / u_resolution) * u_displacementScale;
    offsetB_raw += refractionDir * chromaIntensity;

    // Blend chroma offsets toward the green/base offset using chromaMask to avoid visible bound
    vec2 offsetR = mix(offsetG, offsetR_raw, chromaMask);
    vec2 offsetB = mix(offsetG, offsetB_raw, chromaMask);

    // BLUR sampling (fixed)
    vec2 texelSize = 1.0 / u_resolution;
    float blur = u_blurRadius * 0.75;

    // Build shadow tint (applied to sampled background)
    vec3 shadowTint = mix(vec3(1.0), u_shadowColor.rgb, shadowMask * u_shadowColor.a);

    // Sample (3x3) with per-axis steps using our fixed blur helper
    vec3 cR = blur9_fixed(u_backgroundTexture, v_screenTexCoord, offsetR, texelSize, blur) * shadowTint;
    vec3 cG = blur9_fixed(u_backgroundTexture, v_screenTexCoord, offsetG, texelSize, blur) * shadowTint;
    vec3 cB = blur9_fixed(u_backgroundTexture, v_screenTexCoord, offsetB, texelSize, blur) * shadowTint;

    vec3 finalColor = vec3(cR.r, cG.g, cB.b) * u_brightness;

    // Height-based overlay
    float height_val = getHeightFromSDF(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float overlayStrength = height_val * 0.06 * (1.0 - depthFalloff * 0.3);
    vec3 colorWithOverlay = mix(finalColor, u_overlayColor.rgb, overlayStrength);

    // Highlight
    float highlight_dist = abs(dist_for_shape_boundary);
    float highlight_alpha = (1.0 - smoothstep(0.0, u_highlightWidth, highlight_dist));

    float normalDot = dot(surfaceNormal3D.xy, normalize(centeredCoord + vec2(0.3, 0.3)));
    float directionalFactor = 0.5 + 0.5 * normalDot;

    float finalHighlightAlpha = highlight_alpha * directionalFactor * (0.6 + fresnelFactor * 0.4);

    vec3 highlightColor = vec3(1.0);
    vec3 finalMixed = mix(colorWithOverlay, highlightColor, finalHighlightAlpha * 0.7);

    gl_FragColor = vec4(finalMixed, opacity);
}
