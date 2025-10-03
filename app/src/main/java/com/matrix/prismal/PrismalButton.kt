package com.matrix.prismal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import kotlin.math.sin

class LiquidGlassButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), View.OnTouchListener {

    private val rendererImpl = Renderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setZOrderOnTop(true)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_CONTINUOUSLY
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

    private class Renderer(val context: Context) : GLSurfaceView.Renderer {

        private val quadCoords = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            1f,  1f, 0f, 1f, 1f,
            -1f,  1f, 0f, 0f, 1f
        )
        private val indices = shortArrayOf(0,1,2, 0,2,3)

        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var indexBuffer: ShortBuffer

        private var texBackground = IntArray(1)
        private var texNormal = IntArray(1)

        private var program = 0
        private var uTimeLoc = -1
        private var uBackgroundLoc = -1
        private var uNormalMapLoc = -1
        private var startTime = System.currentTimeMillis().toFloat()

        @Volatile private var backgroundBitmap: Bitmap? = null
        @Volatile private var normalMapBitmap: Bitmap? = null
        @Volatile private var backgroundPending = false
        @Volatile private var normalPending = false

        private val touches = mutableListOf<Pair<Float, Float>>()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glClearColor(0f,0f,0f,0f)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            vertexBuffer.put(quadCoords).position(0)

            indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            indexBuffer.put(indices).position(0)

            GLES30.glGenTextures(1, texBackground, 0)
            GLES30.glGenTextures(1, texNormal, 0)

            program = GLUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            uTimeLoc = GLES30.glGetUniformLocation(program, "uTime")
            uBackgroundLoc = GLES30.glGetUniformLocation(program, "uBackground")
            uNormalMapLoc = GLES30.glGetUniformLocation(program, "uNormalMap")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0,0,width,height)
            if (backgroundBitmap != null) backgroundPending = true
            if (normalMapBitmap != null) normalPending = true
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            if (backgroundPending && backgroundBitmap != null) {
                uploadTexture(texBackground[0], backgroundBitmap!!)
                backgroundPending = false
            }
            if (normalPending && normalMapBitmap != null) {
                uploadTexture(texNormal[0], normalMapBitmap!!)
                normalPending = false
            }

            GLES30.glUseProgram(program)

            val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
            val uvLoc = GLES30.glGetAttribLocation(program, "aUV")

            vertexBuffer.position(0)
            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc,3,GLES30.GL_FLOAT,false,5*4,vertexBuffer)
            vertexBuffer.position(3)
            GLES30.glEnableVertexAttribArray(uvLoc)
            GLES30.glVertexAttribPointer(uvLoc,2,GLES30.GL_FLOAT,false,5*4,vertexBuffer)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texBackground[0])
            if (uBackgroundLoc >= 0) GLES30.glUniform1i(uBackgroundLoc, 0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texNormal[0])
            if (uNormalMapLoc >= 0) GLES30.glUniform1i(uNormalMapLoc, 1)

            val time = (System.currentTimeMillis() - startTime)/1000f
            if (uTimeLoc >= 0) GLES30.glUniform1f(uTimeLoc, time)

            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

            GLES30.glDisableVertexAttribArray(posLoc)
            GLES30.glDisableVertexAttribArray(uvLoc)
        }

        fun setBackground(bitmap: Bitmap) {
            // keep a copy in ARGB_8888 to avoid format issues
            backgroundBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            backgroundPending = true
        }

        fun setNormalMap(bitmap: Bitmap) {
            normalMapBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            normalPending = true
        }

        fun addTouch(x: Float, y: Float) { touches.add(x to y) }

        private fun uploadTexture(tex: Int, bmp: Bitmap) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            val bmpToUpload = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmpToUpload, 0)
            if (bmpToUpload !== bmp) bmpToUpload.recycle()

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
    }

    private object GLUtil {
        fun createProgram(vs:String, fs:String):Int{
            val vert = loadShader(GLES30.GL_VERTEX_SHADER, vs)
            val frag = loadShader(GLES30.GL_FRAGMENT_SHADER, fs)
            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vert)
            GLES30.glAttachShader(prog, frag)
            GLES30.glLinkProgram(prog)
            val status = IntArray(1)
            GLES30.glGetProgramiv(prog,GLES30.GL_LINK_STATUS,status,0)
            if(status[0]==0) throw RuntimeException(GLES30.glGetProgramInfoLog(prog))
            return prog
        }
        private fun loadShader(type:Int, src:String):Int{
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, src)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,compiled,0)
            if(compiled[0]==0) throw RuntimeException(GLES30.glGetShaderInfoLog(shader))
            return shader
        }
    }

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec2 aUV;
            out vec2 vUV;
            void main() {
                vUV = aUV;
                gl_Position = vec4(aPosition,1.0);
            }"""

        private const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec2 vUV;
            out vec4 fragColor;

            uniform sampler2D uBackground;
            uniform sampler2D uNormalMap;
            uniform float uTime;

            void main() {
                // Animate normal map subtly
                vec2 uvAnim = vUV + vec2(sin(uTime*1.5)*0.005, cos(uTime*1.7)*0.005);

                vec3 n = texture(uNormalMap, uvAnim).rgb;
                vec2 offset = (n.xy * 2.0 - 1.0) * 0.03;

                // clamp to avoid sampling outside the texture
                vec2 uv = clamp(vUV + offset, 0.0, 1.0);

                // sample background
                vec4 bg = texture(uBackground, uv);

                // tint and alpha
                vec3 tint = vec3(0.95,0.97,1.0);
                fragColor = vec4(mix(bg.rgb, tint, 0.12), 0.7);
            }"""
    }
}
