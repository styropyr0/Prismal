precision highp float;
uniform sampler2D u_backgroundTexture;
varying vec2 v_texCoord;
void main() {
    gl_FragColor = texture2D(u_backgroundTexture, v_texCoord);
}