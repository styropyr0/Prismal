precision highp float;
uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_sigma;
uniform float u_radius;
uniform vec2 u_bgUvOffset;
uniform vec2 u_bgUvScale;
varying vec2 v_texCoord;

vec3 sampleSrc(vec2 uv) {
    return texture2D(u_texture, u_bgUvOffset + clamp(uv, 0.0, 1.0) * u_bgUvScale).rgb;
}

void main() {
    float s = max(u_sigma, 0.5);
    float s2 = s * s * 2.0;
    vec3 col = sampleSrc(v_texCoord);
    float norm = 1.0;

    // Gaussian kernel, up to 31 taps
    for (float i = 1.0; i <= 15.0; i += 1.0) {
        if (i > u_radius) break;
        float w = exp(-i * i / s2);
        vec2 off = vec2(i * u_texelSize.x, 0.0);
        col += sampleSrc(v_texCoord + off) * w;
        col += sampleSrc(v_texCoord - off) * w;
        norm += 2.0 * w;
    }

    gl_FragColor = vec4(col / norm, 1.0);
}