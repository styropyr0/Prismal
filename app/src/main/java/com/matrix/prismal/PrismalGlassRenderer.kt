package com.matrix.prismal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.matrix.prismal.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

class PrismalGlassRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "PrismalGlassRenderer"
    }

    private var bgProgram = 0
    private var glassProgram = 0

    private var bgPosAttrib = -1
    private var bgBgTextureUniform = -1

    private var positionAttrib = -1
    private var uResolution = -1
    private var uMousePos = -1
    private var uGlassSize = -1
    private var uCornerRadius = -1
    private var uIOR = -1
    private var uGlassThickness = -1
    private var uNormalStrength = -1
    private var uDisplacementScale = -1
    private var uHeightTransitionWidth = -1
    private var uSminSmoothing = -1
    private var uShowNormals = -1
    private var uBlurRadius = -1
    private var uOverlayColor = -1
    private var uHighlightWidth = -1
    private var uChromaticAberration = -1
    private var uBrightness = -1
    private var backgroundTextureUniform = -1
    private var uShadowColor = 0
    private var uShadowOffset = 0
    private var uShadowSoftness = 0

    private lateinit var quadBuffer: FloatBuffer
    private lateinit var bgQuadBuffer: FloatBuffer

    private var backgroundTexture = 0

    private var screenWidth = 1f
    private var screenHeight = 1f

    private var mouseX = 0f
    private var mouseY = 0f
    private var isTouching = false

    private var glassWidth = 400f
    private var glassHeight = 260f
    private var cornerRadius = 3f
    private var ior = 1.5f
    private var glassThickness = 15f
    private var normalStrength = 1.2f
    private var displacementScale = 1.0f
    private var heightBlurFactor = 8f
    private var sminSmoothing = 1.0f
    private var blurRadius = 2.5f
    private var highlightWidth = 4.0f
    private var chromaticAberration = 2.0f
    private var brightness = 1.15f
    private var showNormals = false
    private var refractionInset = 20f
    private var shadowColor = floatArrayOf(0f, 0f, 0f, 0.8f)
    private var shadowOffset = floatArrayOf(8f, -8f)
    private var shadowSoftness = 10f
    private var uRefractionInset = -1

    override fun onSurfaceCreated(glUnused: GL10?, config: EGLConfig?) {
        val vertexCode = ShaderUtils.loadShaderSource(context, R.raw.vertex_shader)
        val fragmentCode = ShaderUtils.loadShaderSource(context, R.raw.fragment_shader)
        val backgroundVertexCode = ShaderUtils.loadShaderSource(context, R.raw.background_vert)
        val backgroundFragmentCode = ShaderUtils.loadShaderSource(context, R.raw.background_frag)

        val quad = floatArrayOf(
            -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        )
        quadBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        quadBuffer.put(quad).position(0)

        val bgQuad = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f,
            -1f, 1f, 1f, -1f, 1f, 1f
        )
        bgQuadBuffer = ByteBuffer.allocateDirect(bgQuad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        bgQuadBuffer.put(bgQuad).position(0)

        bgProgram = linkProgram(backgroundVertexCode, backgroundFragmentCode)
        if (bgProgram == 0) throw RuntimeException("Failed to create bg program")

        glassProgram = linkProgram(vertexCode, fragmentCode)
        if (glassProgram == 0) throw RuntimeException("Failed to create glass program")

        bgPosAttrib = glGetAttribLocation(bgProgram, "a_position")
        bgBgTextureUniform = glGetUniformLocation(bgProgram, "u_backgroundTexture")

        positionAttrib = glGetAttribLocation(glassProgram, "a_position")
        uResolution = glGetUniformLocation(glassProgram, "u_resolution")
        uMousePos = glGetUniformLocation(glassProgram, "u_mousePos")
        uGlassSize = glGetUniformLocation(glassProgram, "u_glassSize")
        uCornerRadius = glGetUniformLocation(glassProgram, "u_cornerRadius")
        uIOR = glGetUniformLocation(glassProgram, "u_ior")
        uGlassThickness = glGetUniformLocation(glassProgram, "u_glassThickness")
        uNormalStrength = glGetUniformLocation(glassProgram, "u_normalStrength")
        uDisplacementScale = glGetUniformLocation(glassProgram, "u_displacementScale")
        uHeightTransitionWidth = glGetUniformLocation(glassProgram, "u_heightTransitionWidth")
        uSminSmoothing = glGetUniformLocation(glassProgram, "u_sminSmoothing")
        uShowNormals = glGetUniformLocation(glassProgram, "u_showNormals")
        uBlurRadius = glGetUniformLocation(glassProgram, "u_blurRadius")
        uOverlayColor = glGetUniformLocation(glassProgram, "u_overlayColor")
        uHighlightWidth = glGetUniformLocation(glassProgram, "u_highlightWidth")
        uChromaticAberration = glGetUniformLocation(glassProgram, "u_chromaticAberration")
        uBrightness = glGetUniformLocation(glassProgram, "u_brightness")
        backgroundTextureUniform = glGetUniformLocation(glassProgram, "u_backgroundTexture")
        uRefractionInset = glGetUniformLocation(glassProgram, "u_refractionInset")
        uShadowColor = glGetUniformLocation(glassProgram, "u_shadowColor")
        uShadowOffset = glGetUniformLocation(glassProgram, "u_shadowOffset")
        uShadowSoftness = glGetUniformLocation(glassProgram, "u_shadowSoftness")

        glClearColor(0.1f, 0.1f, 0.1f, 1f)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        backgroundTexture = createPlaceholderTexture()
    }

    fun setRefractionInset(v: Float) {
        refractionInset = v
    }

    override fun onSurfaceChanged(glUnused: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        screenWidth = max(1, width).toFloat()
        screenHeight = max(1, height).toFloat()
        mouseX = screenWidth * 0.5f
        mouseY = screenHeight * 0.5f
    }

    override fun onDrawFrame(glUnused: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        glUseProgram(bgProgram)
        bgQuadBuffer.position(0)
        glEnableVertexAttribArray(bgPosAttrib)
        glVertexAttribPointer(bgPosAttrib, 2, GL_FLOAT, false, 0, bgQuadBuffer)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, backgroundTexture)
        glUniform1i(bgBgTextureUniform, 0)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glDisableVertexAttribArray(bgPosAttrib)

        glUseProgram(glassProgram)
        quadBuffer.position(0)
        glEnableVertexAttribArray(positionAttrib)
        glVertexAttribPointer(positionAttrib, 2, GL_FLOAT, false, 0, quadBuffer)

        glUniform2f(uResolution, screenWidth, screenHeight)
        val shaderMouseY = screenHeight - mouseY
        glUniform2f(uMousePos, mouseX, shaderMouseY)
        glUniform2f(uGlassSize, glassWidth, glassHeight)
        glUniform1f(uCornerRadius, cornerRadius)
        glUniform1f(uIOR, ior)
        glUniform1f(uGlassThickness, glassThickness)
        glUniform1f(uNormalStrength, normalStrength)
        glUniform1f(uDisplacementScale, displacementScale)
        glUniform1f(uHeightTransitionWidth, heightBlurFactor)
        glUniform1f(uSminSmoothing, sminSmoothing)
        glUniform1i(uShowNormals, if (showNormals) 1 else 0)
        glUniform1f(uBlurRadius, blurRadius)
        glUniform4f(uOverlayColor, 1f, 1f, 1f, 1f)
        glUniform1f(uHighlightWidth, highlightWidth)
        glUniform1f(uChromaticAberration, chromaticAberration)
        glUniform1f(uBrightness, brightness)
        glUniform1f(uRefractionInset, refractionInset)
        glUniform4f(uShadowColor, shadowColor[0], shadowColor[1], shadowColor[2], shadowColor[3])
        glUniform2f(uShadowOffset, shadowOffset[0], shadowColor[1])
        glUniform1f(uShadowSoftness, shadowSoftness)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, backgroundTexture)
        glUniform1i(backgroundTextureUniform, 0)

        glDrawArrays(GL_TRIANGLES, 0, 6)
        glDisableVertexAttribArray(positionAttrib)
    }

    fun setMousePosition(x: Float, y: Float) {
        mouseX = x; mouseY = y
    }

    fun setTouching(t: Boolean) {
        isTouching = t
    }

    fun setGlassSize(w: Float, h: Float) {
        glassWidth = w; glassHeight = h
    }

    fun setCornerRadius(v: Float) {
        cornerRadius = v
    }

    fun setIOR(v: Float) {
        ior = v
    }

    fun setGlassThickness(v: Float) {
        glassThickness = v
    }

    fun setNormalStrength(v: Float) {
        normalStrength = v
    }

    fun setDisplacementScale(v: Float) {
        displacementScale = v
    }

    fun setHeightBlurFactor(v: Float) {
        heightBlurFactor = v
    }

    fun setSminSmoothing(v: Float) {
        sminSmoothing = v
    }

    fun setBlurRadius(v: Float) {
        blurRadius = v
    }

    fun setHighlightWidth(v: Float) {
        highlightWidth = v
    }

    fun setChromaticAberration(v: Float) {
        chromaticAberration = v
    }

    fun setBrightness(v: Float) {
        brightness = v
    }

    fun setShowNormals(v: Boolean) {
        showNormals = v
    }

    fun setShadowProperties(color: Int, offset: FloatArray, softness: Float) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val a = Color.alpha(color) / 255f

        shadowColor = floatArrayOf(r, g, b, a)
        shadowOffset = offset
        shadowSoftness = softness
    }

    fun setBackgroundTexture(bitmap: Bitmap) {
        if (backgroundTexture != 0) {
            val tmp = IntArray(1)
            tmp[0] = backgroundTexture
            glDeleteTextures(1, tmp, 0)
        }
        backgroundTexture = loadTextureFromBitmap(bitmap)
        bitmap.recycle()
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        val compiled = IntArray(1)
        glGetShaderiv(shader, GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = glGetShaderInfoLog(shader)
            Log.e(TAG, "Could not compile shader $type: $info")
            glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun linkProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = glCreateProgram()
        glAttachShader(prog, vs)
        glAttachShader(prog, fs)
        glLinkProgram(prog)
        val linkStatus = IntArray(1)
        glGetProgramiv(prog, GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GL_TRUE) {
            val info = glGetProgramInfoLog(prog)
            Log.e(TAG, "Could not link program: $info")
            glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun createPlaceholderTexture(): Int {
        val tex = IntArray(1)
        glGenTextures(1, tex, 0)
        glBindTexture(GL_TEXTURE_2D, tex[0])
        val pixel = intArrayOf(0xFF3366FF.toInt())
        val bmp = Bitmap.createBitmap(pixel, 1, 1, Bitmap.Config.ARGB_8888)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bmp, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        bmp.recycle()
        return tex[0]
    }

    private fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val tex = IntArray(1)
        glGenTextures(1, tex, 0)
        glBindTexture(GL_TEXTURE_2D, tex[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        return tex[0]
    }
}