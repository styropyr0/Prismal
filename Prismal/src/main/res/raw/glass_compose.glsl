// Prismal - glass compose pass (uses the layers baked by fragment_shader.glsl)

precision highp float;

uniform sampler2D u_backgroundTexture;
uniform vec2      u_bgUvOffset;
uniform vec2      u_bgUvScale;
uniform sampler2D u_blurredTexture;
uniform int       u_useBlurredTexture;

uniform sampler2D u_cacheOffset;
uniform sampler2D u_cacheWeights;
uniform sampler2D u_cacheLight;
uniform sampler2D u_cacheRefl;

uniform vec2  u_resolution;
uniform vec2  u_glassSize;
uniform vec2  u_backdropSampleScale;

uniform float u_chromaticAberration;
uniform float u_chromaMax;
uniform float u_dispersionR;
uniform float u_dispersionB;

uniform float u_vibrancy;
uniform float u_fresnelReflect;
uniform float u_brightness;
uniform vec4  u_glassColor;
uniform vec4  u_shadowColor;
uniform float u_transmittance;

varying vec2 v_screenTexCoord;
varying vec2 v_shapeCoord;

float unpack16(vec2 p) {
    return (floor(p.x * 255.0 + 0.5) * 256.0 + floor(p.y * 255.0 + 0.5)) / 65535.0;
}

vec3 applyVibrancy(vec3 rgb, float sat) {
    if (sat <= 1.001) return rgb;
    float L = dot(rgb, vec3(0.213, 0.715, 0.072));
    return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);
}

vec3 sampleBackdrop(vec2 uv) {
    if (u_useBlurredTexture == 1) return texture2D(u_blurredTexture, uv).rgb;
    return texture2D(u_backgroundTexture, u_bgUvOffset + uv * u_bgUvScale).rgb;
}

void main() {
    vec2 fc = gl_FragCoord.xy / u_resolution;
    vec4 weights = texture2D(u_cacheWeights, fc);
    float opacity = weights.a;
    if (opacity < 0.001) discard;

    vec4 off = texture2D(u_cacheOffset, fc);
    vec2 baseOffset = (vec2(unpack16(off.rg), unpack16(off.ba)) - 0.5) * 8.0;
    vec4 light = texture2D(u_cacheLight, fc);
    vec4 refl = texture2D(u_cacheRefl, fc);
    vec2 reflDelta = (vec2(unpack16(refl.rg), unpack16(refl.ba)) - 0.5) * 1024.0 / u_resolution;

    vec2 s = max(u_backdropSampleScale, vec2(0.01));
    vec2 scaled = (v_screenTexCoord - 0.5) / s + 0.5;
    vec2 uvCenter = clamp(scaled + baseOffset, vec2(0.0), vec2(1.0));

    vec3 color;
    if (max(u_chromaticAberration, 0.0) < 0.02) {
        color = sampleBackdrop(uvCenter);
    } else {
        vec2 pPx = v_shapeCoord * u_glassSize;
        vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);
        vec2 chromaPush = dispDir * light.a * u_chromaMax;
        vec2 uvR = clamp(scaled + baseOffset + chromaPush * u_dispersionR, vec2(0.0), vec2(1.0));
        vec2 uvB = clamp(scaled + baseOffset - chromaPush * u_dispersionB, vec2(0.0), vec2(1.0));
        color = vec3(sampleBackdrop(uvR).r, sampleBackdrop(uvCenter).g, sampleBackdrop(uvB).b);
    }

    color = applyVibrancy(color, u_vibrancy);

    vec2 reflUv = clamp(v_screenTexCoord + baseOffset + reflDelta, vec2(0.0), vec2(1.0));
    color = mix(color, sampleBackdrop(reflUv), weights.r);

    float fresCtl = clamp(u_fresnelReflect, 0.0, 5.0);
    vec3 skyHaze = vec3(0.88, 0.93, 1.02);
    color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), weights.g);

    color *= u_brightness;
    color = mix(color, color * u_glassColor.rgb, u_glassColor.a);
    color = mix(color, u_shadowColor.rgb * 0.25, weights.b);

    gl_FragColor = vec4(color + light.rgb, opacity * u_transmittance);
}
