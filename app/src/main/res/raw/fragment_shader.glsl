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

void main() {
    float actualCornerRadius = min(u_cornerRadius, min(u_glassSize.x, u_glassSize.y) / 2.0);
    vec2 current_p_pixel = v_shapeCoord * u_glassSize;
    vec2 glass_half_size_pixel = u_glassSize / 2.0;

    float dist_for_shape_boundary = sdRoundedBoxSmooth(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing);
    if (dist_for_shape_boundary > 0.001) discard;

    vec2 pixel_step_in_norm_space = vec2(1.0 / u_glassSize.x, 1.0 / u_glassSize.y);
    float norm_step_x1 = pixel_step_in_norm_space.x * 0.75;
    float norm_step_y1 = pixel_step_in_norm_space.y * 0.75;
    float norm_step_x2 = pixel_step_in_norm_space.x * 1.5;
    float norm_step_y2 = pixel_step_in_norm_space.y * 1.5;

    float h_px1 = getHeightFromSDF((v_shapeCoord + vec2(norm_step_x1, 0.0)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_nx1 = getHeightFromSDF((v_shapeCoord - vec2(norm_step_x1, 0.0)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_px2 = getHeightFromSDF((v_shapeCoord + vec2(norm_step_x2, 0.0)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_nx2 = getHeightFromSDF((v_shapeCoord - vec2(norm_step_x2, 0.0)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);

    float grad_x1 = (h_px1 - h_nx1) / (2.0 * norm_step_x1 * u_glassSize.x);
    float grad_x2 = (h_px2 - h_nx2) / (2.0 * norm_step_x2 * u_glassSize.x);
    float delta_x = mix(grad_x1, grad_x2, 0.5);

    float h_py1 = getHeightFromSDF((v_shapeCoord + vec2(0.0, norm_step_y1)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_ny1 = getHeightFromSDF((v_shapeCoord - vec2(0.0, norm_step_y1)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_py2 = getHeightFromSDF((v_shapeCoord + vec2(0.0, norm_step_y2)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    float h_ny2 = getHeightFromSDF((v_shapeCoord - vec2(0.0, norm_step_y2)) * u_glassSize, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);

    float grad_y1 = (h_py1 - h_ny1) / (2.0 * norm_step_y1 * u_glassSize.y);
    float grad_y2 = (h_py2 - h_ny2) / (2.0 * norm_step_y2 * u_glassSize.y);
    float delta_y = mix(grad_y1, grad_y2, 0.5);

    vec3 surfaceNormal3D = normalize(vec3(-delta_x * u_normalStrength, -delta_y * u_normalStrength, 1.0));

    if (u_showNormals == 1) {
        gl_FragColor = vec4(surfaceNormal3D * 0.5 + 0.5, 1.0);
        return;
    }

    vec3 viewDir = normalize(vec3(0.0, 0.0, -1.0));

    float fresnelFactor = fresnel(surfaceNormal3D, viewDir, u_ior);

    float dist_to_edge = -dist_for_shape_boundary;
    float ramp_width = 20.0;
    float ramp_start = u_refractionInset;
    float edgeFactor = smoothstep(ramp_start, ramp_start - ramp_width, dist_to_edge);
    edgeFactor = clamp(edgeFactor, 0.0, 1.0);
    float refractionMultiplier = 1.0 + edgeFactor * fresnelFactor * 0.5;

    vec3 refractedIntoGlass = refract(viewDir, surfaceNormal3D, 1.0 / u_ior);
    vec3 refractedOutOfGlass = refract(refractedIntoGlass, -surfaceNormal3D, u_ior);

    vec2 offset_in_pixels = refractedOutOfGlass.xy * u_glassThickness * refractionMultiplier;
    vec2 baseOffset = (offset_in_pixels / u_resolution) * u_displacementScale;

    vec2 texelSize = 1.0 / u_resolution;
    float chromaStrength = u_chromaticAberration * 0.001;

    vec3 refractR = refract(viewDir, surfaceNormal3D, 1.0 / (u_ior - 0.02));
    vec3 refractOutR = refract(refractR, -surfaceNormal3D, u_ior - 0.02);
    vec2 offsetR = (refractOutR.xy * u_glassThickness * refractionMultiplier / u_resolution) * u_displacementScale;
    offsetR -= baseOffset * chromaStrength;

    vec2 offsetG = baseOffset;

    vec3 refractB = refract(viewDir, surfaceNormal3D, 1.0 / (u_ior + 0.02));
    vec3 refractOutB = refract(refractB, -surfaceNormal3D, u_ior + 0.02);
    vec2 offsetB = (refractOutB.xy * u_glassThickness * refractionMultiplier / u_resolution) * u_displacementScale;
    offsetB += baseOffset * chromaStrength;

    float blurPixelRadius = u_blurRadius;
    vec3 finalColor = vec3(0.0);

    float totalWeight = 0.0;

    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 blurOffset = vec2(float(x), float(y)) * texelSize * u_blurRadius * 0.5;

            float wx = (x == -2 || x == 2) ? 0.06 : (x == -1 || x == 1) ? 0.24 : 0.4;
            float wy = (y == -2 || y == 2) ? 0.06 : (y == -1 || y == 1) ? 0.24 : 0.4;
            float weight = wx * wy;
            totalWeight += weight;

            vec2 coordR = clamp(v_screenTexCoord + offsetR + blurOffset, 0.001, 0.999);
            vec2 coordG = clamp(v_screenTexCoord + offsetG + blurOffset, 0.001, 0.999);
            vec2 coordB = clamp(v_screenTexCoord + offsetB + blurOffset, 0.001, 0.999);

            finalColor.r += texture2D(u_backgroundTexture, coordR).r * weight;
            finalColor.g += texture2D(u_backgroundTexture, coordG).g * weight;
            finalColor.b += texture2D(u_backgroundTexture, coordB).b * weight;
        }
    }

    finalColor /= totalWeight;

    finalColor *= u_brightness;

    float height_val = getHeightFromSDF(current_p_pixel, glass_half_size_pixel, actualCornerRadius, u_sminSmoothing, u_heightTransitionWidth);
    vec4 colorWithOverlay = vec4(mix(finalColor, u_overlayColor.rgb, height_val * 0.12), 1.0);

    float highlight_dist = abs(dist_for_shape_boundary);
    float highlight_alpha = 1.0 - smoothstep(0.0, u_highlightWidth, highlight_dist);
    highlight_alpha = max(0.0, highlight_alpha);

    float directionalFactor = (surfaceNormal3D.x * surfaceNormal3D.y + 1.0) * 0.5;
    float finalHighlightAlpha = highlight_alpha * directionalFactor * (0.7 + fresnelFactor * 0.3);

    gl_FragColor = mix(colorWithOverlay, vec4(1.0, 1.0, 1.0, 1.0), finalHighlightAlpha * 0.8);
}