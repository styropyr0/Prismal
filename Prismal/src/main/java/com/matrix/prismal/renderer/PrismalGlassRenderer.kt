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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.max

/**
 * OpenGL ES 2.0 renderer that produces the liquid glass effect for [com.matrix.prismal.PrismalFrameLayout].
 *
 * ## Shader programs
 * - **background** (`background_vert` / `background_frag`): draws the raw captured bitmap as a
 *   fullscreen quad so the glass appears to sit on top of real content.
 * - **blurH / blurV** (`prismal_blur_h` / `prismal_blur_v`): separable Gaussian blur run into two
 *   FBOs; the final blurred texture feeds the frosted-glass refraction path.
 * - **compose** (`vertex_shader` / `glass_compose`): cheap pass used while the glass is static. It
 *   reads the geometry layers the glass shader bakes once (`u_bakeMode` 1..4) and only resamples
 *   the backdrop, so scrolling content under the glass does not re-run the full shader.
 * - **glass** (`vertex_shader` / `fragment_shader`): the main SDF-based liquid glass shader.
 *   Computes a rounded-rect signed distance field, derives a height field and surface normals,
 *   then applies IOR-driven Snell refraction, chromatic dispersion, Blinn-Phong specular,
 *   Fresnel rim highlights, caustic inner-light, and optional per-quadrant corner radii.
 *
 * ## Key design notes
 * - The glass quad is a half-unit NDC quad scaled to [glassWidth] × [glassHeight]; the background
 *   quad is a full −1..1 clip-space quad. Both share the same vertex layout (2-float XY).
 * - `u_heightTransitionWidth` controls the SDF ramp width - keep it proportional to the view's
 *   minimum dimension (≈ 25–40 % of half-height) or the entire shape collapses into a rim.
 * - Blur FBOs are re-created on every [onSurfaceChanged] to match the new viewport size.
 * - All setter methods only mutate Kotlin-side state; the values are pushed as uniforms inside
 *   [onDrawFrame] on the GL thread, so they are safe to call from any thread.
 *
 * @author Saurav Sajeev
 */
internal class PrismalGlassRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "PrismalGlassRenderer"
        private const val BLUR_FBO_SCALE = 0.5f
        private const val CACHE_LAYERS = 4
        private const val STATE_SIZE = 64
        private const val VIBRANCY = 1.28f
    }

    private var bgProgram = 0
    private var glassProgram = 0
    private var blurHProgram = 0
    private var blurVProgram = 0

    // Bg program attributes
    private var bgPosAttrib = -1
    private var bgTextureUniform = -1
    private val bgUvOffsetLocs = IntArray(4)
    private val bgUvScaleLocs = IntArray(4)

    // Blur program attributes / uniforms
    private var blurHPosAttrib = -1
    private var blurHTexUniform = -1
    private var blurHSigmaUniform = -1
    private var blurHTexelUniform = -1
    private var blurHRadiusUniform = -1
    private var composeProgram = 0
    private var composePosAttrib = -1
    private var cResolution = -1
    private var cMousePos = -1
    private var cGlassSize = -1
    private var cBackground = -1
    private var cBlurred = -1
    private var cUseBlurred = -1
    private var cCacheOffset = -1
    private var cCacheWeights = -1
    private var cCacheLight = -1
    private var cCacheRefl = -1
    private val cCacheSamplers = IntArray(CACHE_LAYERS)
    private var cSampleScale = -1
    private var cChromatic = -1
    private var cChromaMax = -1
    private var cDispersionR = -1
    private var cDispersionB = -1
    private var cVibrancy = -1
    private var cFresnelReflect = -1
    private var cBrightness = -1
    private var cGlassColor = -1
    private var cShadowColor = -1
    private var cTransmittance = -1

    private var blurVPosAttrib = -1
    private var blurVTexUniform = -1
    private var blurVSigmaUniform = -1
    private var blurVTexelUniform = -1
    private var blurVRadiusUniform = -1

    // Glass program attributes
    private var positionAttrib = -1

    // Glass program uniforms
    private var uResolution = -1
    private var uMousePos = -1
    private var uGlassSize = -1
    private var uCornerRadii = -1
    private var uIOR = -1
    private var uGlassThickness = -1
    private var uNormalStrength = -1
    private var uDisplacementScale = -1
    private var uHeightTransitionWidth = -1
    private var uSminSmoothing = -1
    private var uShowNormals = -1
    private var uHighlightWidth = -1
    private var uChromaticAberration = -1
    private var uBrightness = -1
    private var backgroundTextureUniform = -1
    private var uShadowColor = -1
    private var uShadowSoftness = -1
    private var uRefractionInset = -1
    private var uEdgeRefractionFalloff = -1
    private var uGlassColor = -1
    private var uLensRefractionPx = -1
    private var uLensDepthEffect = -1
    private var uVibrancy = -1
    private var uPlainHighlight = -1
    private var uLiquidDome = -1
    private var uFresnelReflect = -1
    private var uBlurredTexture = -1
    private var uUseBlurredTexture = -1
    private var uLightDir = -1
    private var uSpecular = -1
    private var uShininess = -1
    private var uRimStrength = -1
    private var uDispersionR = -1
    private var uDispersionB = -1
    private var uCausticIntensity = -1
    private var uTransmittance = -1
    private var uBackdropSampleScale = -1
    private var uParallaxScale = -1
    private var uPressProgress = -1
    private var uBackdropPinch = -1
    private var uGlowCenter = -1
    private var uGlowStrength = -1
    private var uBakeMode = -1
    private var uChromaMax = -1

    // Geometry buffers
    private lateinit var quadBuffer: FloatBuffer
    private lateinit var bgQuadBuffer: FloatBuffer

    // Textures & FBOs
    private var backgroundTexture = 0
    private var backgroundTexWidth = 0
    private var backgroundTexHeight = 0
    private var blurTex1 = 0
    private var blurTex2 = 0
    private var blurFbo1 = 0
    private var blurFbo2 = 0
    private var blurFboReady = false
    private var blurFboWidth = 0
    private var blurFboHeight = 0
    private var blurDirty = true

    private val cacheTextures = IntArray(CACHE_LAYERS)
    private val cacheFbos = IntArray(CACHE_LAYERS)
    private var cacheWidth = 0
    private var cacheHeight = 0
    private var cacheValid = false
    private val glassState = FloatArray(STATE_SIZE)
    private val lastGlassState = FloatArray(STATE_SIZE)

    var onCacheStale: (() -> Unit)? = null

    private val pendingOps = ConcurrentLinkedQueue<() -> Unit>()
    private val renderRequested = AtomicBoolean(false)

    var onSurfaceReady: (() -> Unit)? = null

    // Screen /view state
    private var screenWidth = 1f
    private var screenHeight = 1f
    private var mouseX = 0f
    private var mouseY = 0f

    //Glass optics state
    private var glassWidth = 400f
    private var glassHeight = 260f
    private var cornerRadius = 30f
    private var cornerRadiiVec = floatArrayOf(30f, 30f, 30f, 30f)
    private var ior = 1.5f
    private var glassThickness = 15f
    private var normalStrength = 1.2f
    private var displacementScale = 1.0f
    private var heightBlurFactor = 8f
    private var sminSmoothing = 1.0f
    private var blurRadius = 2.5f
    private var highlightWidth = 4.0f
    private var chromaticAberration = 0f
    private var brightness = 1.15f
    private var showNormals = false
    private var refractionInset = 20f
    private var shadowColor = floatArrayOf(0f, 0f, 0f, 0.3f)
    private var shadowSoftness = 0.2f
    private var edgeRefractionFalloff = 4f
    private var glassColor = floatArrayOf(1f, 1f, 1f, 0f)
    private var lightDirX = -0.5f
    private var lightDirY = -0.8f
    private var specular = 0.8f
    private var shininess = 48f
    private var rimStrength = 0.6f
    private var dispersionR = 1.0f
    private var dispersionB = 1.0f
    private var causticIntensity = 0.15f
    private var transmittance = 1.0f
    private var liquidDome = 0.78f
    private var fresnelReflect = 1.0f
    private var lensRefractionUserScale = 1.0f
    private var backdropSampleScaleX = 1f
    private var backdropSampleScaleY = 1f
    private var bgUvOffsetX = 0f
    private var bgUvOffsetY = 0f
    private var bgUvScaleX = 1f
    private var bgUvScaleY = 1f
    private var parallaxScale = 1f
    private var pressProgress = 0f
    private var backdropPinch = 1f
    private var glowCenterX = 0.5f
    private var glowCenterY = 0.5f
    private var glowStrength = 1f

    // Renderer
    override fun onSurfaceCreated(glUnused: GL10?, config: EGLConfig?) {
        val vertexCode = ShaderUtils.loadShaderSource(context, R.raw.vertex_shader)
        val fragmentCode = ShaderUtils.loadShaderSource(context, R.raw.fragment_shader)
        val bgVertCode = ShaderUtils.loadShaderSource(context, R.raw.background_vert)
        val bgFragCode = ShaderUtils.loadShaderSource(context, R.raw.background_frag)
        val blurHFragCode = ShaderUtils.loadShaderSource(context, R.raw.prismal_blur_h)
        val blurVFragCode = ShaderUtils.loadShaderSource(context, R.raw.prismal_blur_v)
        val composeFragCode = ShaderUtils.loadShaderSource(context, R.raw.glass_compose)

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
        bgQuadBuffer = ByteBuffer.allocateDirect(bgQuad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bgQuadBuffer.put(bgQuad).position(0)

        bgProgram = linkProgram(bgVertCode, bgFragCode)
        glassProgram = linkProgram(vertexCode, fragmentCode)
        blurHProgram = linkProgram(bgVertCode, blurHFragCode)
        blurVProgram = linkProgram(bgVertCode, blurVFragCode)
        composeProgram = linkProgram(vertexCode, composeFragCode)
        if (composeProgram == 0) Log.w(TAG, "Compose program failed to link, using direct rendering")

        if (bgProgram == 0 || glassProgram == 0 || blurHProgram == 0 || blurVProgram == 0) {
            Log.e(TAG, "One or more shader programs failed to link")
        }

        bgPosAttrib = GLES20.glGetAttribLocation(bgProgram, "a_position")
        bgTextureUniform = GLES20.glGetUniformLocation(bgProgram, "u_backgroundTexture")

        blurHPosAttrib = GLES20.glGetAttribLocation(blurHProgram, "a_position")
        blurHTexUniform = GLES20.glGetUniformLocation(blurHProgram, "u_texture")
        blurHSigmaUniform = GLES20.glGetUniformLocation(blurHProgram, "u_sigma")
        blurHTexelUniform = GLES20.glGetUniformLocation(blurHProgram, "u_texelSize")
        blurHRadiusUniform = GLES20.glGetUniformLocation(blurHProgram, "u_radius")

        blurVPosAttrib = GLES20.glGetAttribLocation(blurVProgram, "a_position")
        blurVTexUniform = GLES20.glGetUniformLocation(blurVProgram, "u_texture")
        blurVSigmaUniform = GLES20.glGetUniformLocation(blurVProgram, "u_sigma")
        blurVTexelUniform = GLES20.glGetUniformLocation(blurVProgram, "u_texelSize")
        blurVRadiusUniform = GLES20.glGetUniformLocation(blurVProgram, "u_radius")

        positionAttrib = GLES20.glGetAttribLocation(glassProgram, "a_position")
        uResolution = GLES20.glGetUniformLocation(glassProgram, "u_resolution")
        uMousePos = GLES20.glGetUniformLocation(glassProgram, "u_mousePos")
        uGlassSize = GLES20.glGetUniformLocation(glassProgram, "u_glassSize")
        uCornerRadii = GLES20.glGetUniformLocation(glassProgram, "u_cornerRadii")
        uIOR = GLES20.glGetUniformLocation(glassProgram, "u_ior")
        uGlassThickness = GLES20.glGetUniformLocation(glassProgram, "u_glassThickness")
        uNormalStrength = GLES20.glGetUniformLocation(glassProgram, "u_normalStrength")
        uDisplacementScale = GLES20.glGetUniformLocation(glassProgram, "u_displacementScale")
        uHeightTransitionWidth = GLES20.glGetUniformLocation(glassProgram, "u_heightTransitionWidth")
        uSminSmoothing = GLES20.glGetUniformLocation(glassProgram, "u_sminSmoothing")
        uShowNormals = GLES20.glGetUniformLocation(glassProgram, "u_showNormals")
        uHighlightWidth = GLES20.glGetUniformLocation(glassProgram, "u_highlightWidth")
        uChromaticAberration = GLES20.glGetUniformLocation(glassProgram, "u_chromaticAberration")
        uBrightness = GLES20.glGetUniformLocation(glassProgram, "u_brightness")
        backgroundTextureUniform = GLES20.glGetUniformLocation(glassProgram, "u_backgroundTexture")
        uShadowColor = GLES20.glGetUniformLocation(glassProgram, "u_shadowColor")
        uShadowSoftness = GLES20.glGetUniformLocation(glassProgram, "u_shadowSoftness")
        uRefractionInset = GLES20.glGetUniformLocation(glassProgram, "u_refractionInset")
        uEdgeRefractionFalloff = GLES20.glGetUniformLocation(glassProgram, "u_edgeRefractionFalloff")
        uGlassColor = GLES20.glGetUniformLocation(glassProgram, "u_glassColor")
        uLensRefractionPx = GLES20.glGetUniformLocation(glassProgram, "u_lensRefractionPx")
        uLensDepthEffect = GLES20.glGetUniformLocation(glassProgram, "u_lensDepthEffect")
        uVibrancy = GLES20.glGetUniformLocation(glassProgram, "u_vibrancy")
        uPlainHighlight = GLES20.glGetUniformLocation(glassProgram, "u_plainHighlight")
        uLiquidDome = GLES20.glGetUniformLocation(glassProgram, "u_liquidDome")
        uFresnelReflect = GLES20.glGetUniformLocation(glassProgram, "u_fresnelReflect")
        uBlurredTexture = GLES20.glGetUniformLocation(glassProgram, "u_blurredTexture")
        uUseBlurredTexture = GLES20.glGetUniformLocation(glassProgram, "u_useBlurredTexture")
        uLightDir = GLES20.glGetUniformLocation(glassProgram, "u_lightDir")
        uSpecular = GLES20.glGetUniformLocation(glassProgram, "u_specular")
        uShininess = GLES20.glGetUniformLocation(glassProgram, "u_shininess")
        uRimStrength = GLES20.glGetUniformLocation(glassProgram, "u_rimStrength")
        uDispersionR = GLES20.glGetUniformLocation(glassProgram, "u_dispersionR")
        uDispersionB = GLES20.glGetUniformLocation(glassProgram, "u_dispersionB")
        uCausticIntensity = GLES20.glGetUniformLocation(glassProgram, "u_causticIntensity")
        uTransmittance = GLES20.glGetUniformLocation(glassProgram, "u_transmittance")
        uBackdropSampleScale = GLES20.glGetUniformLocation(glassProgram, "u_backdropSampleScale")
        uParallaxScale = GLES20.glGetUniformLocation(glassProgram, "u_parallaxScale")
        uPressProgress = GLES20.glGetUniformLocation(glassProgram, "u_pressProgress")
        uBackdropPinch = GLES20.glGetUniformLocation(glassProgram, "u_backdropPinch")
        uGlowCenter = GLES20.glGetUniformLocation(glassProgram, "u_glowCenter")
        uGlowStrength = GLES20.glGetUniformLocation(glassProgram, "u_glowStrength")
        uBakeMode = GLES20.glGetUniformLocation(glassProgram, "u_bakeMode")
        uChromaMax = GLES20.glGetUniformLocation(glassProgram, "u_chromaMax")

        if (composeProgram != 0) {
            val cp = composeProgram
            composePosAttrib = GLES20.glGetAttribLocation(cp, "a_position")
            cResolution = GLES20.glGetUniformLocation(cp, "u_resolution")
            cMousePos = GLES20.glGetUniformLocation(cp, "u_mousePos")
            cGlassSize = GLES20.glGetUniformLocation(cp, "u_glassSize")
            cBackground = GLES20.glGetUniformLocation(cp, "u_backgroundTexture")
            cBlurred = GLES20.glGetUniformLocation(cp, "u_blurredTexture")
            cUseBlurred = GLES20.glGetUniformLocation(cp, "u_useBlurredTexture")
            cCacheOffset = GLES20.glGetUniformLocation(cp, "u_cacheOffset")
            cCacheWeights = GLES20.glGetUniformLocation(cp, "u_cacheWeights")
            cCacheLight = GLES20.glGetUniformLocation(cp, "u_cacheLight")
            cCacheRefl = GLES20.glGetUniformLocation(cp, "u_cacheRefl")
            cCacheSamplers[0] = cCacheOffset
            cCacheSamplers[1] = cCacheWeights
            cCacheSamplers[2] = cCacheLight
            cCacheSamplers[3] = cCacheRefl
            cSampleScale = GLES20.glGetUniformLocation(cp, "u_backdropSampleScale")
            cChromatic = GLES20.glGetUniformLocation(cp, "u_chromaticAberration")
            cChromaMax = GLES20.glGetUniformLocation(cp, "u_chromaMax")
            cDispersionR = GLES20.glGetUniformLocation(cp, "u_dispersionR")
            cDispersionB = GLES20.glGetUniformLocation(cp, "u_dispersionB")
            cVibrancy = GLES20.glGetUniformLocation(cp, "u_vibrancy")
            cFresnelReflect = GLES20.glGetUniformLocation(cp, "u_fresnelReflect")
            cBrightness = GLES20.glGetUniformLocation(cp, "u_brightness")
            cGlassColor = GLES20.glGetUniformLocation(cp, "u_glassColor")
            cShadowColor = GLES20.glGetUniformLocation(cp, "u_shadowColor")
            cTransmittance = GLES20.glGetUniformLocation(cp, "u_transmittance")
        }

        listOf(bgProgram, blurHProgram, glassProgram, composeProgram).forEachIndexed { i, prog ->
            bgUvOffsetLocs[i] = if (prog != 0) GLES20.glGetUniformLocation(prog, "u_bgUvOffset") else -1
            bgUvScaleLocs[i] = if (prog != 0) GLES20.glGetUniformLocation(prog, "u_bgUvScale") else -1
        }

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        backgroundTexture = createPlaceholderTexture()
        backgroundTexWidth = 1
        backgroundTexHeight = 1
        blurDirty = true
        cacheTextures.fill(0)
        cacheFbos.fill(0)
        cacheWidth = 0
        cacheHeight = 0
        cacheValid = false
        onSurfaceReady?.invoke()
    }

    override fun onSurfaceChanged(glUnused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = max(1, width).toFloat()
        screenHeight = max(1, height).toFloat()
        mouseX = screenWidth * 0.5f
        mouseY = screenHeight * 0.5f
        setupBlurFBOs(width, height)
        deleteCache()
    }

    fun post(op: () -> Unit) {
        pendingOps.add(op)
    }

    fun markRenderRequested(): Boolean = renderRequested.compareAndSet(false, true)

    override fun onDrawFrame(glUnused: GL10?) {
        renderRequested.set(false)
        while (true) {
            val op = pendingOps.poll() ?: break
            op()
        }

        if (blurDirty && blurFboReady && backgroundTexture != 0) {
            blurDirty = false
            val sigma = max(blurRadius * BLUR_FBO_SCALE, 0.5f)
            val tapRadius = ceil(3f * sigma).coerceIn(1f, 15f)
            val texelW = 1f / blurFboWidth
            val texelH = 1f / blurFboHeight

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo1)
            GLES20.glViewport(0, 0, blurFboWidth, blurFboHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(blurHProgram)
            bgQuadBuffer.position(0)
            GLES20.glEnableVertexAttribArray(blurHPosAttrib)
            GLES20.glVertexAttribPointer(blurHPosAttrib, 2, GLES20.GL_FLOAT, false, 0, bgQuadBuffer)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
            GLES20.glUniform1i(blurHTexUniform, 0)
            GLES20.glUniform1f(blurHSigmaUniform, sigma)
            GLES20.glUniform2f(blurHTexelUniform, texelW, texelH)
            GLES20.glUniform1f(blurHRadiusUniform, tapRadius)
            setBgUvUniforms(1)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glDisableVertexAttribArray(blurHPosAttrib)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo2)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(blurVProgram)
            bgQuadBuffer.position(0)
            GLES20.glEnableVertexAttribArray(blurVPosAttrib)
            GLES20.glVertexAttribPointer(blurVPosAttrib, 2, GLES20.GL_FLOAT, false, 0, bgQuadBuffer)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex1)
            GLES20.glUniform1i(blurVTexUniform, 0)
            GLES20.glUniform1f(blurVSigmaUniform, sigma)
            GLES20.glUniform2f(blurVTexelUniform, texelW, texelH)
            GLES20.glUniform1f(blurVRadiusUniform, tapRadius)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glDisableVertexAttribArray(blurVPosAttrib)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, screenWidth.toInt(), screenHeight.toInt())
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(bgProgram)
        bgQuadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(bgPosAttrib)
        GLES20.glVertexAttribPointer(bgPosAttrib, 2, GLES20.GL_FLOAT, false, 0, bgQuadBuffer)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(bgTextureUniform, 0)
        setBgUvUniforms(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(bgPosAttrib)

        val chromaMax = max(chromaticAberration, 0f) * 0.0018f * 1.45f + 1e-6f
        captureGlassState(glassState)
        val changed = !glassState.contentEquals(lastGlassState)
        if (changed) {
            glassState.copyInto(lastGlassState)
            cacheValid = false
        }
        val cacheable = composeProgram != 0 && pressProgress == 0f && !showNormals
        if (cacheable && !changed && !cacheValid) bakeCache(chromaMax)

        if (cacheable && cacheValid) {
            drawCompose(chromaMax)
        } else {
            drawGlass(0, chromaMax)
            if (cacheable) onCacheStale?.invoke()
        }
    }

    private fun drawGlass(bakeMode: Int, chromaMax: Float) {
        GLES20.glUseProgram(glassProgram)
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)

        GLES20.glUniform2f(uResolution, screenWidth, screenHeight)
        GLES20.glUniform2f(uMousePos, mouseX, screenHeight - mouseY)
        GLES20.glUniform2f(uGlassSize, glassWidth, glassHeight)
        GLES20.glUniform4f(
            uCornerRadii,
            cornerRadiiVec[0],
            cornerRadiiVec[1],
            cornerRadiiVec[2],
            cornerRadiiVec[3]
        )
        GLES20.glUniform1f(uIOR, ior)
        GLES20.glUniform1f(uGlassThickness, glassThickness)
        GLES20.glUniform1f(uNormalStrength, normalStrength)
        GLES20.glUniform1f(uDisplacementScale, displacementScale)
        GLES20.glUniform1f(uHeightTransitionWidth, heightBlurFactor)
        GLES20.glUniform1f(uSminSmoothing, sminSmoothing)
        GLES20.glUniform1i(uShowNormals, if (showNormals) 1 else 0)
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

        val minGlassDim = minOf(glassWidth, glassHeight)
        val domeBoost = 1f + 0.55f * liquidDome.coerceIn(0f, 2f)
        val refractionHeight = heightBlurFactor * domeBoost
        val lensPx = refractionHeight * 2f * displacementScale * lensRefractionUserScale
        GLES20.glUniform1f(uLensRefractionPx, lensPx.coerceIn(4f, minGlassDim * 0.85f))
        GLES20.glUniform1f(
            uLensDepthEffect,
            kotlin.math.min(1f, max(0f, normalStrength * 0.9f))
        )
        GLES20.glUniform1f(uVibrancy, VIBRANCY)
        GLES20.glUniform1f(uPlainHighlight, 0.08f)
        GLES20.glUniform1f(uLiquidDome, liquidDome)
        GLES20.glUniform1f(uFresnelReflect, fresnelReflect)

        GLES20.glUniform2f(uLightDir, lightDirX, lightDirY)
        GLES20.glUniform1f(uSpecular, specular)
        GLES20.glUniform1f(uShininess, shininess)
        GLES20.glUniform1f(uRimStrength, rimStrength)
        GLES20.glUniform1f(uDispersionR, dispersionR)
        GLES20.glUniform1f(uDispersionB, dispersionB)
        GLES20.glUniform1f(uCausticIntensity, causticIntensity)
        GLES20.glUniform1f(uTransmittance, transmittance)
        if (uBackdropSampleScale >= 0) {
            GLES20.glUniform2f(uBackdropSampleScale, backdropSampleScaleX, backdropSampleScaleY)
        }
        if (uParallaxScale >= 0) {
            GLES20.glUniform1f(uParallaxScale, parallaxScale)
        }
        if (uPressProgress >= 0) {
            GLES20.glUniform1f(uPressProgress, pressProgress)
        }
        if (uBackdropPinch >= 0) {
            GLES20.glUniform1f(uBackdropPinch, backdropPinch)
        }
        if (uGlowCenter >= 0) {
            GLES20.glUniform2f(uGlowCenter, glowCenterX, glowCenterY)
        }
        if (uGlowStrength >= 0) {
            GLES20.glUniform1f(uGlowStrength, glowStrength)
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(backgroundTextureUniform, 0)

        if (blurFboReady && blurTex2 != 0 && blurRadius > 0.05f) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex2)
            GLES20.glUniform1i(uBlurredTexture, 1)
            GLES20.glUniform1i(uUseBlurredTexture, 1)
        } else {
            GLES20.glUniform1i(uUseBlurredTexture, 0)
        }

        GLES20.glUniform1i(uBakeMode, bakeMode)
        setBgUvUniforms(2)
        GLES20.glUniform1f(uChromaMax, chromaMax)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(positionAttrib)
    }

    private fun drawCompose(chromaMax: Float) {
        val cp = composeProgram
        GLES20.glUseProgram(cp)
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(composePosAttrib)
        GLES20.glVertexAttribPointer(composePosAttrib, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)

        GLES20.glUniform2f(cResolution, screenWidth, screenHeight)
        GLES20.glUniform2f(cMousePos, mouseX, screenHeight - mouseY)
        GLES20.glUniform2f(cGlassSize, glassWidth, glassHeight)
        GLES20.glUniform2f(cSampleScale, backdropSampleScaleX, backdropSampleScaleY)
        GLES20.glUniform1f(cChromatic, chromaticAberration)
        GLES20.glUniform1f(cChromaMax, chromaMax)
        GLES20.glUniform1f(cDispersionR, dispersionR)
        GLES20.glUniform1f(cDispersionB, dispersionB)
        GLES20.glUniform1f(cVibrancy, VIBRANCY)
        GLES20.glUniform1f(cFresnelReflect, fresnelReflect)
        GLES20.glUniform1f(cBrightness, brightness)
        GLES20.glUniform4f(cGlassColor, glassColor[0], glassColor[1], glassColor[2], glassColor[3])
        GLES20.glUniform4f(cShadowColor, shadowColor[0], shadowColor[1], shadowColor[2], shadowColor[3])
        GLES20.glUniform1f(cTransmittance, transmittance)
        setBgUvUniforms(3)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(cBackground, 0)
        if (blurFboReady && blurTex2 != 0 && blurRadius > 0.05f) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex2)
            GLES20.glUniform1i(cBlurred, 1)
            GLES20.glUniform1i(cUseBlurred, 1)
        } else {
            GLES20.glUniform1i(cUseBlurred, 0)
        }
        for (i in 0 until CACHE_LAYERS) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cacheTextures[i])
            GLES20.glUniform1i(cCacheSamplers[i], 2 + i)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(composePosAttrib)
    }

    private fun setBgUvUniforms(program: Int) {
        GLES20.glUniform2f(bgUvOffsetLocs[program], bgUvOffsetX, bgUvOffsetY)
        GLES20.glUniform2f(bgUvScaleLocs[program], bgUvScaleX, bgUvScaleY)
    }

    private fun bakeCache(chromaMax: Float) {
        val w = screenWidth.toInt()
        val h = screenHeight.toInt()
        if (!ensureCacheTargets(w, h)) return
        GLES20.glDisable(GLES20.GL_BLEND)
        for (i in 0 until CACHE_LAYERS) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, cacheFbos[i])
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawGlass(i + 1, chromaMax)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        cacheValid = true
    }

    private fun ensureCacheTargets(w: Int, h: Int): Boolean {
        if (cacheTextures[0] != 0 && cacheWidth == w && cacheHeight == h) return true
        deleteCache()
        GLES20.glGenTextures(CACHE_LAYERS, cacheTextures, 0)
        GLES20.glGenFramebuffers(CACHE_LAYERS, cacheFbos, 0)
        var complete = true
        for (i in 0 until CACHE_LAYERS) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cacheTextures[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, cacheFbos[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, cacheTextures[i], 0
            )
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                complete = false
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (!complete) {
            Log.w(TAG, "Glass cache FBO incomplete, using direct rendering")
            deleteCache()
            composeProgram = 0
            return false
        }
        cacheWidth = w
        cacheHeight = h
        return true
    }

    private fun deleteCache() {
        if (cacheFbos[0] != 0) GLES20.glDeleteFramebuffers(CACHE_LAYERS, cacheFbos, 0)
        if (cacheTextures[0] != 0) GLES20.glDeleteTextures(CACHE_LAYERS, cacheTextures, 0)
        cacheFbos.fill(0)
        cacheTextures.fill(0)
        cacheWidth = 0
        cacheHeight = 0
        cacheValid = false
    }

    private fun captureGlassState(out: FloatArray) {
        var i = 0
        fun put(v: Float) { out[i++] = v }
        put(screenWidth); put(screenHeight); put(mouseX); put(mouseY)
        put(glassWidth); put(glassHeight)
        cornerRadiiVec.forEach { put(it) }
        put(ior); put(glassThickness); put(normalStrength); put(displacementScale)
        put(heightBlurFactor); put(sminSmoothing); put(highlightWidth); put(chromaticAberration)
        put(brightness); put(refractionInset); put(edgeRefractionFalloff)
        shadowColor.forEach { put(it) }
        put(shadowSoftness)
        glassColor.forEach { put(it) }
        put(liquidDome); put(fresnelReflect); put(lensRefractionUserScale)
        put(lightDirX); put(lightDirY); put(specular); put(shininess); put(rimStrength)
        put(dispersionR); put(dispersionB); put(causticIntensity); put(transmittance)
        put(backdropSampleScaleX); put(backdropSampleScaleY); put(parallaxScale)
        put(pressProgress); put(backdropPinch); put(glowCenterX); put(glowCenterY); put(glowStrength)
        put(if (showNormals) 1f else 0f)
        put(if (blurFboReady) 1f else 0f)
    }

    private fun setupBlurFBOs(width: Int, height: Int) {
        deleteBlurFBOs()

        blurFboWidth = max((width * BLUR_FBO_SCALE).toInt(), 1)
        blurFboHeight = max((height * BLUR_FBO_SCALE).toInt(), 1)

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        blurTex1 = textures[0]
        blurTex2 = textures[1]

        for (tex in textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                blurFboWidth, blurFboHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
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
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val fbos = IntArray(2)
        GLES20.glGenFramebuffers(2, fbos, 0)
        blurFbo1 = fbos[0]
        blurFbo2 = fbos[1]

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo1)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, blurTex1, 0
        )
        val status1 = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo2)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, blurTex2, 0
        )
        val status2 = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        blurFboReady = (status1 == GLES20.GL_FRAMEBUFFER_COMPLETE &&
                status2 == GLES20.GL_FRAMEBUFFER_COMPLETE)
        blurDirty = true

        if (!blurFboReady) Log.w(TAG, "Blur FBO incomplete: status1=$status1 status2=$status2")
    }

    private fun deleteBlurFBOs() {
        if (blurFbo1 != 0) {
            GLES20.glDeleteFramebuffers(2, intArrayOf(blurFbo1, blurFbo2), 0)
            blurFbo1 = 0; blurFbo2 = 0
        }
        if (blurTex1 != 0) {
            GLES20.glDeleteTextures(2, intArrayOf(blurTex1, blurTex2), 0)
            blurTex1 = 0; blurTex2 = 0
        }
        blurFboReady = false
    }

    /**
     * Uploads [bitmap] as the background texture that the glass refracts.
     *
     * Must be called on the GL thread (e.g. from a [GLSurfaceView.queueEvent] block).
     *
     * @param bitmap ARGB_8888 screenshot of the view hierarchy beneath the glass.
     */
    /** Maps view UVs into the backdrop bitmap (captures are aligned to the window pixel grid). */
    fun setBackdropUvTransform(offsetX: Float, offsetY: Float, scaleX: Float, scaleY: Float) {
        bgUvOffsetX = offsetX
        bgUvOffsetY = offsetY
        bgUvScaleX = scaleX
        bgUvScaleY = scaleY
        blurDirty = true
    }

    fun setBackgroundTexture(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (backgroundTexture == 0) {
            backgroundTexture = loadTextureFromBitmap(bitmap)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
            if (bitmap.width == backgroundTexWidth && bitmap.height == backgroundTexHeight) {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
        }
        backgroundTexWidth = bitmap.width
        backgroundTexHeight = bitmap.height
        blurDirty = true
    }

    private fun createPlaceholderTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        val bmp = Bitmap.createBitmap(intArrayOf(0xFF3366FF.toInt()), 1, 1, Bitmap.Config.ARGB_8888)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
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

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
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
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    /** Distance in px from the silhouette edge at which refraction begins to fade to zero. */
    fun setRefractionInset(v: Float) {
        refractionInset = v
    }

    /** Sets the glass quad dimensions in pixels - must match the view's measured size. */
    fun setGlassSize(w: Float, h: Float) {
        glassWidth = w; glassHeight = h
    }

    /** Sets a uniform corner radius in pixels applied identically to all four corners. */
    fun setCornerRadius(v: Float) {
        cornerRadius = v
        cornerRadiiVec[0] = v
        cornerRadiiVec[1] = v
        cornerRadiiVec[2] = v
        cornerRadiiVec[3] = v
    }

    /** Index of refraction; drives the Snell-law deflection of background rays (typical 1.3–2.0). */
    fun setIOR(v: Float) {
        ior = v
    }

    /**
     * Edge-ramp width (`u_heightTransitionWidth`) in pixels. Controls how wide the SDF height
     * profile rises at the silhouette - keep below ~40 % of the view's half-height or the shape
     * reads as a thick border rather than filled glass.
     */
    fun setGlassThickness(v: Float) {
        glassThickness = v
    }

    /** Scales the XY surface-normal magnitude, amplifying refraction and specular sharpness. */
    fun setNormalStrength(v: Float) {
        normalStrength = v
    }

    /** Multiplier applied to the final pixel-space lens displacement vector. */
    fun setDisplacementScale(v: Float) {
        displacementScale = v
    }

    /**
     * Lens refraction band depth in pixels (`u_heightTransitionWidth` in the shader).
     * Displacement is set to ~2× this value (see `refractionAmount` in Backdrop).
     */
    fun setHeightBlurFactor(v: Float) {
        heightBlurFactor = v
    }

    /** Smooth-min/max blending radius for the SDF corner join - higher = softer rounded corners. */
    fun setSminSmoothing(v: Float) {
        sminSmoothing = v
    }

    /** Gaussian blur sigma (in pixels) for the frosted background pass. */
    fun setBlurRadius(v: Float) {
        if (v == blurRadius) return
        blurRadius = v
        blurDirty = true
    }

    /** Width (in shader units) of the Fresnel plain highlight band at the glass silhouette. */
    fun setHighlightWidth(v: Float) {
        highlightWidth = v
    }

    /** Lateral RGB channel split in pixels; 0 disables the effect entirely. */
    fun setChromaticAberration(v: Float) {
        chromaticAberration = if (v <= 0.01f) 0f else max(0f, v)
    }

    /** Overall output brightness multiplier applied after all lighting passes. */
    fun setBrightness(v: Float) {
        brightness = v
    }

    /** When true the glass quad renders only the surface normal map (debug visualisation). */
    fun setShowNormals(v: Boolean) {
        showNormals = v
    }

    /** Controls how steeply the lens distortion falls off toward the silhouette edge. */
    fun setEdgeRefractionFalloff(v: Float) {
        edgeRefractionFalloff = v
    }

    /** Passes the current touch position so the shader can add specular tracking (unused path). */
    fun setMousePosition(x: Float, y: Float) {
        mouseX = x; mouseY = y
    }

    fun setTouching(@Suppress("UNUSED_PARAMETER") t: Boolean) { /* reserved */
    }

    /**
     * Tints the glass interior with [color]'s RGBA. Alpha 0 = neutral/clear glass;
     * a subtle blue tint (`#0A0000FF`) mimics real soda-lime glass.
     */
    fun setGlassColor(color: Int) {
        glassColor = floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            Color.alpha(color) / 255f
        )
    }

    /**
     * Sets the drop-shadow beneath the glass.
     * @param color ARGB shadow color - alpha controls opacity; white tints the shadow warm.
     * @param softness ≤ 1 = hard direct shadow, > 1 = softness scaled by ÷ 20 in the shader.
     */
    fun setShadowProperties(color: Int, softness: Float) {
        shadowColor = floatArrayOf(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            Color.alpha(color) / 255f
        )
        shadowSoftness = softness
    }

    /** Sets the dominant light direction used for specular and edge highlights. */
    fun setLightDirection(x: Float, y: Float) {
        lightDirX = x; lightDirY = y
    }

    /** Overall lens distortion scale (1 = default). */
    fun setLensRefractionScale(v: Float) {
        lensRefractionUserScale = max(0.25f, v)
    }

    /** Scales backdrop UVs from center - lower values magnify (flat lens). Used by switch thumb. */
    fun setBackdropSampleScale(sx: Float, sy: Float) {
        backdropSampleScaleX = max(0.01f, sx)
        backdropSampleScaleY = max(0.01f, sy)
    }

    /** Multiplier on parallax UV shift; reduce on small thumbs to keep the track centered. */
    fun setParallaxScale(v: Float) {
        parallaxScale = v.coerceIn(0f, 2f)
    }

    /**
     * Press interaction state for iOS-style liquid glass.
     * @param progress 0 = rest, 1 = fully pressed
     * @param pinch Center backdrop scale at full press
     * @param glowX Touch highlight X in normalized view coords [0, 1] (left to right)
     * @param glowY Touch highlight Y in normalized view coords [0, 1] (top to bottom; flipped to GL shape space internally)
     */
    fun setPressInteraction(
        progress: Float,
        pinch: Float,
        glowX: Float,
        glowY: Float,
        glowStrength: Float = 1f,
    ) {
        pressProgress = progress.coerceIn(0f, 1f)
        backdropPinch = pinch.coerceIn(0.5f, 1f)
        glowCenterX = glowX.coerceIn(0f, 1f)
        glowCenterY = glowY.coerceIn(0f, 1f)
        this.glowStrength = glowStrength.coerceIn(0f, 1f)
    }

    /** Clears press interaction (rest state). */
    fun clearPressInteraction() = setPressInteraction(0f, 1f, 0.5f, 0.5f, 0f)

    /** 0 = flat sigmoid slab, 1 = strong spherical-cap “droplet” volume, up to 2 = exaggerated dome */
    fun setLiquidDomeStrength(v: Float) {
        liquidDome = v.coerceIn(0f, 2f)
    }

    /** Multiplier for grazing-angle sky reflection / liquid Fresnel (0–5). */
    fun setFresnelReflectStrength(v: Float) {
        fresnelReflect = v.coerceIn(0f, 5f)
    }

    /** Sets Blinn-Phong specular highlight strength and glossiness. */
    fun setSpecular(intensity: Float, shine: Float) {
        specular = intensity; shininess = shine
    }

    /** Sets the Fresnel-based rim/edge glow strength. Higher = brighter edge ring. */
    fun setRimStrength(v: Float) {
        rimStrength = v
    }

    /**
     * Sets per-channel chromatic dispersion multipliers.
     * @param r red-channel scale (>0 = outward from centre)
     * @param b blue-channel scale (>0 = inward toward centre)
     */
    fun setDispersion(r: Float, b: Float) {
        dispersionR = r; dispersionB = b
    }

    /** Sets the intensity of the caustic inner-light effect (light focusing through glass). */
    fun setCausticIntensity(v: Float) {
        causticIntensity = v
    }

    /** Sets overall glass transmittance / opacity (0=invisible, 1=opaque glass). */
    fun setTransmittance(v: Float) {
        transmittance = v
    }

    /**
     * Applies all parameters from a [PrismalFilter] preset in one call.
     * Equivalent to calling each individual setter manually.
     */
    fun setFilter(filter: PrismalFilter) {
        setCornerRadius(filter.cornerRadius)
        setIOR(filter.ior)
        setGlassThickness(filter.thickness)
        setNormalStrength(filter.normalStrength)
        setDisplacementScale(filter.displacementScale)
        setHeightBlurFactor(filter.heightBlurFactor)
        setSminSmoothing(filter.minSmoothing)
        setLiquidDomeStrength(filter.liquidDomeStrength)
        setFresnelReflectStrength(filter.fresnelReflectionStrength)
        setBlurRadius(filter.blurRadius)
        setHighlightWidth(filter.highlightWidth)
        setChromaticAberration(filter.chromaticAberration)
        setBrightness(filter.brightness)
        setShowNormals(filter.showNormals)
        setEdgeRefractionFalloff(filter.edgeRefractionFalloff)
        setRefractionInset(filter.refractionInset)
        setShadowProperties(filter.shadowColor, filter.shadowSoftness)
        setLightDirection(filter.lightDirX, filter.lightDirY)
        setSpecular(filter.specular, filter.shininess)
        setRimStrength(filter.rimStrength)
        setDispersion(filter.dispersionR, filter.dispersionB)
        setCausticIntensity(filter.causticIntensity)
        setTransmittance(filter.transmittance)
    }
}