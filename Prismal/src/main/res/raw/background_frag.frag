precision highp float;
uniform sampler2D u_backgroundTexture;
uniform vec2 u_bgUvOffset;
uniform vec2 u_bgUvScale;
varying vec2 v_texCoord;
void main() {
    gl_FragColor = texture2D(u_backgroundTexture, u_bgUvOffset + v_texCoord * u_bgUvScale);
}