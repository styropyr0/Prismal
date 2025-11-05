package com.matrix.prismal.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.matrix.prismal.R
import com.matrix.prismal.filters.PrismalFilter
import com.matrix.prismal.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * ## PrismalGlassRenderer
 *
 * `PrismalGlassRenderer` is the core OpenGL ES 2.0 renderer responsible for rendering the
 * glass-like prismal effect within the **Prismal** framework. It draws both the background
 * and the interactive glass layer with refraction, blurring, shadow, and highlight effects.
 *
 * This renderer manages all OpenGL shader programs, uniforms, and textures needed to simulate
 * realistic refractive glass and lighting behaviors, including chromatic aberration, blur,
 * shadow softness, and optical thickness adjustments.
 *
 * ### Rendering Overview
 * The rendering process occurs in two main stages:
 * 1. **Background Rendering:**
 *    A fullscreen textured quad is drawn to represent the background scene.
 *    The background can be set dynamically using `setBackgroundTexture()`.
 *
 * 2. **Glass Rendering:**
 *    The glass effect is drawn on top using a separate shader program.
 *    Parameters like refraction index, corner radius, brightness, shadow softness,
 *    and chromatic aberration are adjustable through dedicated setter methods.
 *
 * ### Key Features
 * - Configurable glass size, shape, and corner radius.
 * - Adjustable Index of Refraction (IOR) and glass thickness for realism.
 * - Blur-based refraction and light dispersion simulation.
 * - Customizable shadow and highlight effects.
 * - Dynamic background texture binding.
 * - Touch interaction support for mouse-like movement effects.
 * - Option to visualize surface normals for debugging (`showNormals`).
 *
 * ### Shader Programs
 * - **Background Shader:** Draws the static or dynamic background texture.
 * - **Glass Shader:** Implements refraction, shadowing, blurring, and chromatic aberration effects.
 *
 * ### Uniform Variables (Fragment Shader)
 * The following key uniforms are handled and updated by this renderer:
 * - `u_resolution` → Screen dimensions (in pixels).
 * - `u_mousePos` → Touch/mouse interaction coordinates.
 * - `u_glassSize` → Width and height of the rendered glass area.
 * - `u_cornerRadius` → Radius of rounded glass corners.
 * - `u_ior` → Index of refraction controlling light bending.
 * - `u_glassThickness` → Simulated thickness of the glass surface.
 * - `u_normalStrength` → Strength of the normal map effect.
 * - `u_displacementScale` → Depth distortion scale.
 * - `u_blurRadius` → Gaussian blur radius for refraction.
 * - `u_overlayColor` → Overlay tint applied to the glass.
 * - `u_chromaticAberration` → RGB channel displacement for dispersion.
 * - `u_brightness` → Final color multiplier for the glass.
 * - `u_refractionInset` → Pixel inset to avoid edge artifacts during refraction.
 * - `u_shadowColor`, `u_shadowSoftness` → Shadow rendering parameters.
 * - `u_edgeRefractionFalloff` → Gradient falloff at the glass edges.
 *
 * ### Texture Management
 * The renderer creates and maintains one OpenGL texture:
 * - **Background Texture:** The main texture that gets refracted through the glass.
 *   Can be replaced at runtime with a new `Bitmap` via `setBackgroundTexture()`.
 *
 * A placeholder texture (solid blue color) is generated during initialization
 * if no background texture is yet provided.
 *
 * ### Public Configuration Methods
 * - `setGlassSize(w, h)` → Defines the dimensions of the glass.
 * - `setCornerRadius(v)` → Sets the rounded corner radius.
 * - `setIOR(v)` → Adjusts the optical refraction index.
 * - `setGlassThickness(v)` → Changes simulated glass thickness.
 * - `setNormalStrength(v)` → Modifies normal map intensity.
 * - `setBlurRadius(v)` → Controls blur strength.
 * - `setBrightness(v)` → Alters the brightness of the glass reflection.
 * - `setShadowProperties(color, softness)` → Customizes shadow tint and blur.
 * - `setChromaticAberration(v)` → Adjusts dispersion strength.
 * - `setShowNormals(v)` → Toggles normal visualization.
 * - `setRefractionInset(v)` → Reduces edge refraction bleeding.
 * - `setBackgroundTexture(bitmap)` → Updates the background source.
 *
 * ### Touch & Interaction
 * - `setMousePosition(x, y)` → Updates current pointer/touch location.
 * - `setTouching(t)` → Enables or disables interaction-driven updates.
 *
 * ### Internal Responsibilities
 * - Compiles and links shaders using `compileShader()` and `linkProgram()`.
 * - Manages OpenGL texture creation and deletion to prevent memory leaks.
 * - Sets and updates all relevant uniforms per frame.
 * - Renders geometry through two quads (background and glass).
 *
 * ### Usage Notes
 * - This class is **internal** and designed for use within the Prismal engine only.
 * - To use it externally, wrap it within a `GLSurfaceView` (e.g., `PrismalFrameLayout`).
 * - All OpenGL calls must be executed on the GL thread.
 *
 * ### Example Lifecycle
 * ```
 * val renderer = PrismalGlassRenderer(context)
 * glSurfaceView.setRenderer(renderer)
 *
 * renderer.setGlassSize(400f, 260f)
 * renderer.setIOR(1.45f)
 * renderer.setBlurRadius(2.0f)
 * renderer.setShadowProperties(Color.BLACK, 0.25f)
 * ```
 *
 * @author Saurav Sajeev
 * Licensed under the MIT License
 */

internal class PrismalGlassRenderer(private val context: Context) : GLSurfaceView.Renderer {

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
    private var uRefractionInset = -1
    private var uEdgeRefractionFalloff = 0
    private var uGlassColor = 0

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
    private var cornerRadius = 30f
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
    private var shadowColor = floatArrayOf(0f, 0f, 0f, 0.3f)
    private var shadowSoftness = 0.2f
    private var edgeRefractionFalloff = 4f
    private var glassColor = floatArrayOf(1f, 1f, 1f, 1f)

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

        bgPosAttrib = GLES20.glGetAttribLocation(bgProgram, "a_position")
        bgBgTextureUniform = GLES20.glGetUniformLocation(bgProgram, "u_backgroundTexture")

        positionAttrib = GLES20.glGetAttribLocation(glassProgram, "a_position")
        uResolution = GLES20.glGetUniformLocation(glassProgram, "u_resolution")
        uMousePos = GLES20.glGetUniformLocation(glassProgram, "u_mousePos")
        uGlassSize = GLES20.glGetUniformLocation(glassProgram, "u_glassSize")
        uCornerRadius = GLES20.glGetUniformLocation(glassProgram, "u_cornerRadius")
        uIOR = GLES20.glGetUniformLocation(glassProgram, "u_ior")
        uGlassThickness = GLES20.glGetUniformLocation(glassProgram, "u_glassThickness")
        uNormalStrength = GLES20.glGetUniformLocation(glassProgram, "u_normalStrength")
        uDisplacementScale = GLES20.glGetUniformLocation(glassProgram, "u_displacementScale")
        uHeightTransitionWidth = GLES20.glGetUniformLocation(glassProgram, "u_heightTransitionWidth")
        uSminSmoothing = GLES20.glGetUniformLocation(glassProgram, "u_sminSmoothing")
        uShowNormals = GLES20.glGetUniformLocation(glassProgram, "u_showNormals")
        uBlurRadius = GLES20.glGetUniformLocation(glassProgram, "u_blurRadius")
        uOverlayColor = GLES20.glGetUniformLocation(glassProgram, "u_overlayColor")
        uHighlightWidth = GLES20.glGetUniformLocation(glassProgram, "u_highlightWidth")
        uChromaticAberration = GLES20.glGetUniformLocation(glassProgram, "u_chromaticAberration")
        uBrightness = GLES20.glGetUniformLocation(glassProgram, "u_brightness")
        backgroundTextureUniform = GLES20.glGetUniformLocation(glassProgram, "u_backgroundTexture")
        uRefractionInset = GLES20.glGetUniformLocation(glassProgram, "u_refractionInset")
        uShadowColor = GLES20.glGetUniformLocation(glassProgram, "u_shadowColor")
        uShadowOffset = GLES20.glGetUniformLocation(glassProgram, "u_shadowOffset")
        uShadowSoftness = GLES20.glGetUniformLocation(glassProgram, "u_shadowSoftness")
        uEdgeRefractionFalloff = GLES20.glGetUniformLocation(glassProgram, "u_edgeRefractionFalloff")
        uGlassColor = GLES20.glGetUniformLocation(glassProgram, "u_glassColor")

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        backgroundTexture = createPlaceholderTexture()
    }

    fun setRefractionInset(v: Float) {
        refractionInset = v
    }

    override fun onSurfaceChanged(glUnused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = max(1, width).toFloat()
        screenHeight = max(1, height).toFloat()
        mouseX = screenWidth * 0.5f
        mouseY = screenHeight * 0.5f
    }

    override fun onDrawFrame(glUnused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(bgProgram)
        bgQuadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(bgPosAttrib)
        GLES20.glVertexAttribPointer(bgPosAttrib, 2, GLES20.GL_FLOAT, false, 0, bgQuadBuffer)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(bgBgTextureUniform, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(bgPosAttrib)

        GLES20.glUseProgram(glassProgram)
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)

        GLES20.glUniform2f(uResolution, screenWidth, screenHeight)
        val shaderMouseY = screenHeight - mouseY
        GLES20.glUniform2f(uMousePos, mouseX, shaderMouseY)
        GLES20.glUniform2f(uGlassSize, glassWidth, glassHeight)
        GLES20.glUniform1f(uCornerRadius, cornerRadius)
        GLES20.glUniform1f(uIOR, ior)
        GLES20.glUniform1f(uGlassThickness, glassThickness)
        GLES20.glUniform1f(uNormalStrength, normalStrength)
        GLES20.glUniform1f(uDisplacementScale, displacementScale)
        GLES20.glUniform1f(uHeightTransitionWidth, heightBlurFactor)
        GLES20.glUniform1f(uSminSmoothing, sminSmoothing)
        GLES20.glUniform1i(uShowNormals, if (showNormals) 1 else 0)
        GLES20.glUniform1f(uBlurRadius, blurRadius)
        GLES20.glUniform4f(uOverlayColor, 1f, 1f, 1f, 1f)
        GLES20.glUniform1f(uHighlightWidth, highlightWidth)
        GLES20.glUniform1f(uChromaticAberration, chromaticAberration)
        GLES20.glUniform1f(uBrightness, brightness)
        GLES20.glUniform1f(uRefractionInset, refractionInset)
        GLES20.glUniform4f(
            uShadowColor,
            shadowColor[0],
            shadowColor[1],
            shadowColor[2],
            shadowColor[3]
        )
        GLES20.glUniform1f(uShadowSoftness, shadowSoftness)
        GLES20.glUniform1f(uEdgeRefractionFalloff, edgeRefractionFalloff)
        GLES20.glUniform4f(uGlassColor, glassColor[0], glassColor[1], glassColor[2], glassColor[3])

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(backgroundTextureUniform, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(positionAttrib)
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

    fun setEdgeRefractionFalloff(v: Float) {
        edgeRefractionFalloff = v
    }

    fun setGlassColor(color: Int) {
        glassColor = floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            Color.alpha(color) / 255f
        )
    }

    fun setFilter(filter: PrismalFilter) {
        with(filter) {
            setGlassSize(glassWidth, glassHeight)
            setCornerRadius(cornerRadius)
            setIOR(ior)
            setGlassThickness(glassThickness)
            setNormalStrength(normalStrength)
            setDisplacementScale(displacementScale)
            setHeightBlurFactor(heightBlurFactor)
            setSminSmoothing(sminSmoothing)
            setBlurRadius(blurRadius)
            setHighlightWidth(highlightWidth)
            setChromaticAberration(chromaticAberration)
            setBrightness(brightness)
            setShowNormals(showNormals)
            setEdgeRefractionFalloff(edgeRefractionFalloff)
        }
    }

    fun setShadowProperties(color: Int, softness: Float) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val a = Color.alpha(color) / 255f

        shadowColor = floatArrayOf(r, g, b, a)
        shadowSoftness = softness
    }

    fun setBackgroundTexture(bitmap: Bitmap) {
        if (backgroundTexture != 0) {
            val tmp = IntArray(1)
            tmp[0] = backgroundTexture
            GLES20.glDeleteTextures(1, tmp, 0)
        }
        backgroundTexture = loadTextureFromBitmap(bitmap)
        bitmap.recycle()
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Could not compile shader $type: $info")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun linkProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(prog)
            Log.e(TAG, "Could not link program: $info")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun createPlaceholderTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        val pixel = intArrayOf(0xFF3366FF.toInt())
        val bmp = Bitmap.createBitmap(pixel, 1, 1, Bitmap.Config.ARGB_8888)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        bmp.recycle()
        return tex[0]
    }

    private fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return tex[0]
    }
}