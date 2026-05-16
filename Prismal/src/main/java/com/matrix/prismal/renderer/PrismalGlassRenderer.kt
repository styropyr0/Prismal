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
 * OpenGL ES 2.0 renderer that produces the liquid glass effect for [com.matrix.prismal.PrismalFrameLayout].
 *
 * ## Shader programs
 * - **background** (`background_vert` / `background_frag`): draws the raw captured bitmap as a
 *   fullscreen quad so the glass appears to sit on top of real content.
 * - **blurH / blurV** (`prismal_blur_h` / `prismal_blur_v`): separable Gaussian blur run into two
 *   FBOs; the final blurred texture feeds the frosted-glass refraction path.
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
    }

    private var bgProgram = 0
    private var glassProgram = 0
    private var blurHProgram = 0
    private var blurVProgram = 0

    // Bg program attributes
    private var bgPosAttrib = -1
    private var bgTextureUniform = -1

    // Blur program attributes / uniforms
    private var blurHPosAttrib = -1
    private var blurHTexUniform = -1
    private var blurHSigmaUniform = -1
    private var blurHTexelUniform = -1
    private var blurVPosAttrib = -1
    private var blurVTexUniform = -1
    private var blurVSigmaUniform = -1
    private var blurVTexelUniform = -1

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

    // Geometry buffers
    private lateinit var quadBuffer: FloatBuffer
    private lateinit var bgQuadBuffer: FloatBuffer

    // Textures & FBOs
    private var backgroundTexture = 0
    private var blurTex1 = 0
    private var blurTex2 = 0
    private var blurFbo1 = 0
    private var blurFbo2 = 0
    private var blurFboReady = false
    private var blurFboWidth = 0
    private var blurFboHeight = 0

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

    // Renderer
    override fun onSurfaceCreated(glUnused: GL10?, config: EGLConfig?) {
        val vertexCode = ShaderUtils.loadShaderSource(context, R.raw.vertex_shader)
        val fragmentCode = ShaderUtils.loadShaderSource(context, R.raw.fragment_shader)
        val bgVertCode = ShaderUtils.loadShaderSource(context, R.raw.background_vert)
        val bgFragCode = ShaderUtils.loadShaderSource(context, R.raw.background_frag)
        val blurHFragCode = ShaderUtils.loadShaderSource(context, R.raw.prismal_blur_h)
        val blurVFragCode = ShaderUtils.loadShaderSource(context, R.raw.prismal_blur_v)

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

        if (bgProgram == 0 || glassProgram == 0 || blurHProgram == 0 || blurVProgram == 0) {
            Log.e(TAG, "One or more shader programs failed to link")
        }

        bgPosAttrib = GLES20.glGetAttribLocation(bgProgram, "a_position")
        bgTextureUniform = GLES20.glGetUniformLocation(bgProgram, "u_backgroundTexture")

        blurHPosAttrib = GLES20.glGetAttribLocation(blurHProgram, "a_position")
        blurHTexUniform = GLES20.glGetUniformLocation(blurHProgram, "u_texture")
        blurHSigmaUniform = GLES20.glGetUniformLocation(blurHProgram, "u_sigma")
        blurHTexelUniform = GLES20.glGetUniformLocation(blurHProgram, "u_texelSize")

        blurVPosAttrib = GLES20.glGetAttribLocation(blurVProgram, "a_position")
        blurVTexUniform = GLES20.glGetUniformLocation(blurVProgram, "u_texture")
        blurVSigmaUniform = GLES20.glGetUniformLocation(blurVProgram, "u_sigma")
        blurVTexelUniform = GLES20.glGetUniformLocation(blurVProgram, "u_texelSize")

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

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        backgroundTexture = createPlaceholderTexture()
    }

    override fun onSurfaceChanged(glUnused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = max(1, width).toFloat()
        screenHeight = max(1, height).toFloat()
        mouseX = screenWidth * 0.5f
        mouseY = screenHeight * 0.5f
        setupBlurFBOs(width, height)
    }

    override fun onDrawFrame(glUnused: GL10?) {
        if (blurFboReady && backgroundTexture != 0) {
            val sigma = max(blurRadius, 0.5f)
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
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glDisableVertexAttribArray(blurVPosAttrib)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, screenWidth.toInt(), screenHeight.toInt())
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(bgProgram)
        bgQuadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(bgPosAttrib)
        GLES20.glVertexAttribPointer(bgPosAttrib, 2, GLES20.GL_FLOAT, false, 0, bgQuadBuffer)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(bgTextureUniform, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(bgPosAttrib)

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
        val dimNorm = kotlin.math.sqrt((minGlassDim / 118f).coerceIn(0.3f, 1f))
        val lensPx = (minGlassDim * 0.058f * displacementScale + glassThickness * displacementScale * 1.75f + (ior - 1f) * 20f) * lensRefractionUserScale * dimNorm

        val bigGlassT = ((minGlassDim - 88f) / (228f - 88f)).coerceIn(0f, 1f)
        val bigGlassK = bigGlassT * bigGlassT * (3f - 2f * bigGlassT)
        val lensCapFrac = 0.34f + 0.17f * bigGlassK
        GLES20.glUniform1f(uLensRefractionPx, lensPx.coerceIn(3.2f, minGlassDim * lensCapFrac))
        GLES20.glUniform1f(
            uLensDepthEffect,
            kotlin.math.min(1f, max(0f, normalStrength * 0.9f))
        )
        GLES20.glUniform1f(uVibrancy, 1.28f)
        GLES20.glUniform1f(uPlainHighlight, 0.22f)
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

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glUniform1i(backgroundTextureUniform, 0)

        if (blurFboReady && blurTex2 != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTex2)
            GLES20.glUniform1i(uBlurredTexture, 1)
            GLES20.glUniform1i(uUseBlurredTexture, 1)
        } else {
            GLES20.glUniform1i(uUseBlurredTexture, 0)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(positionAttrib)
    }

    private fun setupBlurFBOs(width: Int, height: Int) {
        deleteBlurFBOs()

        blurFboWidth = max(width, 1)
        blurFboHeight = max(height, 1)

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
     * The bitmap is recycled immediately after upload to free native memory.
     *
     * @param bitmap ARGB_8888 screenshot of the view hierarchy beneath the glass.
     */
    fun setBackgroundTexture(bitmap: Bitmap) {
        if (backgroundTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(backgroundTexture), 0)
        backgroundTexture = loadTextureFromBitmap(bitmap)
        bitmap.recycle()
    }

    private fun createPlaceholderTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        val bmp = Bitmap.createBitmap(intArrayOf(0xFF3366FF.toInt()), 1, 1, Bitmap.Config.ARGB_8888)
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
     * Controls the depth-of-field blur gradient in pixels (`u_heightTransitionWidth` in the shader).
     * Proportional to view size - set to roughly 25 % of the view's minimum dimension.
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
        blurRadius = v
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

    /** Kyant-style overall lens distortion scale (1 = default). */
    fun setLensRefractionScale(v: Float) {
        lensRefractionUserScale = max(0.25f, v)
    }

    /** 0 = flat sigmoid slab, 1 = strong spherical-cap “droplet” volume */
    fun setLiquidDomeStrength(v: Float) {
        liquidDome = v.coerceIn(0f, 1f)
    }

    /** Multiplier for grazing-angle sky reflection / liquid Fresnel (0–2). */
    fun setFresnelReflectStrength(v: Float) {
        fresnelReflect = v.coerceIn(0f, 2f)
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