precision highp float;
uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_sigma;
varying vec2 v_texCoord;

void main() {
    float s = max(u_sigma, 0.5);
    float s2 = s * s * 2.0;
    float norm = 0.0;
    vec3 col = vec3(0.0);

    // 31-tap Gaussian kernel, handles sigma up to ~5 cleanly
    for (float i = -15.0; i <= 15.0; i += 1.0) {
        float w = exp(-i * i / s2);
        vec2 uv = clamp(v_texCoord + vec2(i * u_texelSize.x, 0.0), 0.0, 1.0);
        col += texture2D(u_texture, uv).rgb * w;
        norm += w;
    }

    gl_FragColor = vec4(col / norm, 1.0);
}