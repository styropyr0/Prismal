precision highp float;
attribute vec2 a_position;
uniform vec2 u_resolution;
uniform vec2 u_mousePos;
uniform vec2 u_glassSize;
varying vec2 v_screenTexCoord;
varying vec2 v_shapeCoord;
void main() {
    vec2 screenPos = u_mousePos + a_position * u_glassSize;
    vec2 clipSpacePos = (screenPos / u_resolution) * 2.0 - 1.0;
    gl_Position = vec4(clipSpacePos, 0.0, 1.0);
    v_screenTexCoord = screenPos / u_resolution;
    v_screenTexCoord.y = 1.0 - v_screenTexCoord.y;
    v_shapeCoord = a_position;
}