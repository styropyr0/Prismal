package com.matrix.prismal

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class LiquidGlassButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), View.OnTouchListener {

    private val rendererImpl = Renderer()

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(true)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_WHEN_DIRTY
        setOnTouchListener(this)
    }

    fun setBackgroundBitmap(bitmap: Bitmap) = rendererImpl.setBackground(bitmap)
    fun setNormalMap(bitmap: Bitmap) = rendererImpl.setNormalMap(bitmap)

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        event ?: return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            rendererImpl.addTouch(event.x / width, 1f - event.y / height)
        }
        return true
    }

    private class Renderer : GLSurfaceView.Renderer {

        private val quadCoords = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f,
            1f, -1f, 0f, 1f, 1f,
            1f, 1f, 0f, 1f, 0f,
            -1f, 1f, 0f, 0f, 0f
        )

        private val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var indexBuffer: ShortBuffer

        private var programBlurH = 0
        private var programBlurV = 0
        private var programGlass = 0

        private var fboScene = IntArray(1)
        private var texScene = IntArray(1)
        private var fboBlur = IntArray(1)
        private var texBlur = IntArray(1)
        private var fboTemp = IntArray(1)
        private var texTemp = IntArray(1)

        private var texNormal = IntArray(1)

        private var width = 0
        private var height = 0
        private var startTime = System.currentTimeMillis().toFloat()

        @Volatile
        private var backgroundBitmap: Bitmap? = null
        @Volatile
        private var normalMapBitmap: Bitmap? = null
        @Volatile
        private var backgroundPending = false
        @Volatile
        private var normalPending = false

        private val touches = mutableListOf<Pair<Float, Float>>()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            vertexBuffer.put(quadCoords).position(0)

            indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            indexBuffer.put(indices).position(0)

            programBlurH = GLUtil.createProgram(VERTEX_SHADER, BLUR_FRAGMENT_H)
            programBlurV = GLUtil.createProgram(VERTEX_SHADER, BLUR_FRAGMENT_V)
            programGlass = GLUtil.createProgram(VERTEX_SHADER, GLASS_FRAGMENT)

            GLES30.glGenTextures(1, texNormal, 0)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES30.glViewport(0, 0, width, height)

            createFBO(fboScene, texScene, width, height)
            createFBO(fboBlur, texBlur, width, height)
            createFBO(fboTemp, texTemp, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            if (backgroundPending && backgroundBitmap != null) {
                uploadTexture(texScene[0], backgroundBitmap!!)
                backgroundPending = false
            }
            if (normalPending && normalMapBitmap != null) {
                uploadTexture(texNormal[0], normalMapBitmap!!)
                normalPending = false
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboTemp[0])
            GLES30.glUseProgram(programBlurH)
            drawQuad(texScene[0], programBlurH, 1.0f / width, 0f)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboBlur[0])
            GLES30.glUseProgram(programBlurV)
            drawQuad(texTemp[0], programBlurV, 0f, 1.0f / height)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glUseProgram(programGlass)
            val time = (System.currentTimeMillis() - startTime) / 1000f
            val uTime = GLES30.glGetUniformLocation(programGlass, "uTime")
            GLES30.glUniform1f(uTime, time)
            drawQuadWithNormal(texBlur[0], texNormal[0], programGlass)
        }

        private fun drawQuad(texture: Int, program: Int, dx: Float, dy: Float) {
            val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
            val uvLoc = GLES30.glGetAttribLocation(program, "aUV")
            val uTexLoc = GLES30.glGetUniformLocation(program, "uTexture")
            val uDirLoc = GLES30.glGetUniformLocation(program, "uDirection")

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glUniform1i(uTexLoc, 0)
            GLES30.glUniform2f(uDirLoc, dx, dy)

            vertexBuffer.position(0)
            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)
            vertexBuffer.position(3)
            GLES30.glEnableVertexAttribArray(uvLoc)
            GLES30.glVertexAttribPointer(uvLoc, 2, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)

            GLES30.glDrawElements(
                GLES30.GL_TRIANGLES,
                indices.size,
                GLES30.GL_UNSIGNED_SHORT,
                indexBuffer
            )

            GLES30.glDisableVertexAttribArray(posLoc)
            GLES30.glDisableVertexAttribArray(uvLoc)
        }

        private fun drawQuadWithNormal(bgTex: Int, normalTex: Int, program: Int) {
            val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
            val uvLoc = GLES30.glGetAttribLocation(program, "aUV")

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bgTex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uBackground"), 0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, normalTex)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uNormalMap"), 1)

            vertexBuffer.position(0)
            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)
            vertexBuffer.position(3)
            GLES30.glEnableVertexAttribArray(uvLoc)
            GLES30.glVertexAttribPointer(uvLoc, 2, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)

            GLES30.glDrawElements(
                GLES30.GL_TRIANGLES,
                indices.size,
                GLES30.GL_UNSIGNED_SHORT,
                indexBuffer
            )
            GLES30.glDisableVertexAttribArray(posLoc)
            GLES30.glDisableVertexAttribArray(uvLoc)
        }

        private fun createFBO(fbo: IntArray, tex: IntArray, width: Int, height: Int) {
            GLES30.glGenFramebuffers(1, fbo, 0)
            GLES30.glGenTextures(1, tex, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                tex[0],
                0
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }

        private fun uploadTexture(tex: Int, bmp: Bitmap) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_LINEAR
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }

        fun setBackground(bitmap: Bitmap) {
            backgroundBitmap = bitmap
            backgroundPending = true
        }

        fun setNormalMap(bitmap: Bitmap) {
            normalMapBitmap = bitmap
            normalPending = true
        }

        fun addTouch(x: Float, y: Float) {
            touches.add(x to y)
        }

        companion object {
            private const val VERTEX_SHADER = """#version 300 es
                layout(location=0) in vec3 aPosition;
                layout(location=1) in vec2 aUV;
                out vec2 vUV;
                void main(){
                    vUV = aUV;
                    gl_Position = vec4(aPosition,1.0);
                }
            """

            private const val BLUR_FRAGMENT_H = """#version 300 es
                precision mediump float;
                in vec2 vUV;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform vec2 uDirection;

                void main(){
                    float weights[5];
                    weights[0]=0.204164; weights[1]=0.304005; weights[2]=0.093913;
                    weights[3]=0.025; weights[4]=0.012;
                    vec4 sum = texture(uTexture, vUV) * weights[0];
                    for(int i=1;i<5;i++){
                        sum += texture(uTexture, vUV + uDirection * float(i)) * weights[i];
                        sum += texture(uTexture, vUV - uDirection * float(i)) * weights[i];
                    }
                    fragColor = sum;
                }
            """

            private const val BLUR_FRAGMENT_V = BLUR_FRAGMENT_H

            private const val GLASS_FRAGMENT = """#version 300 es
                precision mediump float;
                in vec2 vUV;
                out vec4 fragColor;

                uniform sampler2D uBackground;
                uniform sampler2D uNormalMap;
                uniform float uTime;

                void main(){
                    vec3 n = texture(uNormalMap, vUV + vec2(uTime*0.02, uTime*0.015)).rgb * 2.0 - 1.0;
                    float refrStrength = 0.03;

                    vec2 uvR = vUV + n.xy * refrStrength * 0.9;
                    vec2 uvG = vUV + n.xy * refrStrength;
                    vec2 uvB = vUV + n.xy * refrStrength * 1.1;

                    float r = texture(uBackground, uvR).r;
                    float g = texture(uBackground, uvG).g;
                    float b = texture(uBackground, uvB).b;
                    vec3 refr = vec3(r,g,b);

                    float spec = pow(clamp(dot(n, vec3(0.0,0.0,1.0)),0.0,1.0),50.0);

                    vec3 tint = vec3(0.95,0.97,1.0);
                    vec3 col = mix(refr, tint, 0.08) + spec * 0.15;
                    col = pow(col, vec3(0.8)) * 1.1;

                    fragColor = vec4(col, 0.8);
                }
            """
        }
    }

    private object GLUtil {
        fun createProgram(vs: String, fs: String): Int {
            val vert = loadShader(GLES30.GL_VERTEX_SHADER, vs)
            val frag = loadShader(GLES30.GL_FRAGMENT_SHADER, fs)
            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vert)
            GLES30.glAttachShader(prog, frag)
            GLES30.glLinkProgram(prog)
            val status = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) throw RuntimeException(GLES30.glGetProgramInfoLog(prog))
            return prog
        }

        private fun loadShader(type: Int, src: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, src)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) throw RuntimeException(GLES30.glGetShaderInfoLog(shader))
            return shader
        }
    }
}
