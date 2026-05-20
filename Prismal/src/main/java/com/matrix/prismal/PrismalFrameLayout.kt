package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import kotlin.math.hypot
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.matrix.prismal.filters.PrismalFilter
import com.matrix.prismal.renderer.PrismalGlassRenderer
import com.matrix.prismal.utils.SpringAnimator
import androidx.core.graphics.withClip

/**
 * A `FrameLayout` subclass that renders an iOS-style liquid glass material over its children
 * using an embedded OpenGL ES 2.0 surface.
 *
 * ## How it works
 * On each [updateBackground] call, the view hierarchy beneath this layout is drawn into a
 * `Bitmap` and uploaded to the GPU as a background texture. The OpenGL renderer then runs a
 * GLSL fragment shader that applies refraction, Gaussian frosting, Snell's law lens distortion,
 * Fresnel rim highlights, Blinn-Phong dual specular, chromatic aberration, and caustics on
 * top of the captured content - composited back into the normal view hierarchy.
 *
 * ## Quick start
 * ```xml
 * <com.matrix.prismal.PrismalFrameLayout
 *     android:layout_width=”match_parent”
 *     android:layout_height=”120dp”
 *     app:pfl_cornerRadius=”24dp”
 *     app:pfl_ior=”1.55”
 *     app:pfl_blurRadius=”4” />
 * ```
 * ```kotlin
 * PrismalLiquidGlass.applyBase(myGlassView)   // apply the canonical iOS recipe
 * myGlassView.updateBackground()              // capture the current backdrop
 * myGlassView.setOnClickWithAnimationListener { /* spring press + glow */ }
 * ```
 *
 * Use [setOnClickWithAnimationListener] for interactive glass cards. It is separate from
 * [setOnClickListener] so subclasses such as [PrismalSwitch] are not affected.
 *
 * ## Threading
 * All setter methods are safe to call from any thread- they queue work onto the GL thread
 * internally. [updateBackground] must be called from the main thread.
 *
 * ## Sizing constraint
 * The `thickness` parameter (`pfl_glassThickness`) controls the SDF edge-ramp width.
 * Keep it below ~40 % of `min(width, height) / 2` or the entire shape collapses into a border
 * ring. See [setThickness] for details.
 *
 * @see PrismalLiquidGlass.applyBase
 * @see updateBackground
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
open class PrismalFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), View.OnTouchListener {

    private val glSurface: GLSurfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
    }
    private val renderer: PrismalGlassRenderer = PrismalGlassRenderer(context)
    private var lastWidth = 0
    private var lastHeight = 0
    private var captureScheduled = false
    private var debug = false
    private var lastCaptureTime = 0L
    private val minCaptureInterval = 0L
    private var captureHost: ViewGroup? = null
    private var captureBlurRadius = 3.85f
    private var captureDownsampleMode: DownsampleMode? = null

    private var backdropHandledByChild = false
    private var onBackdropCaptured: (() -> Unit)? = null
    private var hasClickListenerCallback = false
    private var glowEnabled = false
    private val hasClickCallback
        get() = hasClickListenerCallback || glowEnabled || clickWithAnimListener != null

    private var clickWithAnimListener: (() -> Unit)? = null
    private var clickAnimPressScale = 0.96f
    private val clickAnimSpring = SpringAnimator(0.55f, 380f)

    private var glowX = 0f
    private var glowY = 0f
    private var glowAlpha = 0f
    private var glowIn = false
    private var glowLastNanos = 0L
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        val dt = if (glowLastNanos == 0L) 0.016f
        else ((nanos - glowLastNanos) / 1_000_000_000f).coerceAtMost(0.048f)
        glowLastNanos = nanos
        glowAlpha = if (glowIn) (glowAlpha + dt / 0.12f).coerceAtMost(1f)
        else (glowAlpha - dt / 0.28f).coerceAtLeast(0f)
        invalidate()
        val settled = if (glowIn) glowAlpha >= 1f else glowAlpha <= 0f
        if (!settled) Choreographer.getInstance().postFrameCallback(glowCallback)
    }

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        scheduleCaptureBackground()
    }

    private val clipPath = Path()
    private val clipRect = RectF()
    private var clipRadius = 0f
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            glSurface.queueAndRender {
                renderer.setGlassSize(width.toFloat(), height.toFloat())
            }
            scheduleCaptureBackground()
        }
    }

    init {
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        glSurface.setOnTouchListener(this)

        val density = context.resources.displayMetrics.density
        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalFrameLayout, 0, 0).apply {
            try {
                getFloat(R.styleable.PrismalFrameLayout_pfl_glassWidth, -1f).takeIf { it > 0 }
                    ?.let { w ->
                        getFloat(
                            R.styleable.PrismalFrameLayout_pfl_glassHeight,
                            -1f
                        ).takeIf { it > 0 }?.let { h ->
                            setGlassSize(w, h)
                        }
                    }

                setIOR(getFloat(R.styleable.PrismalFrameLayout_pfl_ior, 1.42222f))
                setThickness(
                    getDimension(
                        R.styleable.PrismalFrameLayout_pfl_glassThickness,
                        18f * density
                    )
                )
                setNormalStrength(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_normalStrength,
                        3.6515f
                    )
                )
                setDisplacementScale(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_displacementScale,
                        1f
                    )
                )
                setHeightBlurFactor(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_heightTransitionWidth,
                        15.3f
                    )
                )
                setMinSmoothing(getFloat(R.styleable.PrismalFrameLayout_pfl_minSmoothing, 4.0f))
                setBlurRadius(getFloat(R.styleable.PrismalFrameLayout_pfl_blurRadius, 2f))
                setHighlightWidth(getFloat(R.styleable.PrismalFrameLayout_pfl_highlightWidth, 0.35f))
                setChromaticAberration(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_chromaticAberration,
                        26f
                    )
                )
                setBrightness(getFloat(R.styleable.PrismalFrameLayout_pfl_brightness, 1.21f))
                setShowNormals(getBoolean(R.styleable.PrismalFrameLayout_pfl_showNormals, false))
                setCornerRadius(
                    getDimension(
                        R.styleable.PrismalFrameLayout_pfl_cornerRadius,
                        28f * density
                    )
                )
                setShadowProperties(
                    "#23FFFFFF".toColorInt(),
                    getFloat(R.styleable.PrismalFrameLayout_pfl_shadowSoftness, 10f)
                )
                setGlassColor(getColor(R.styleable.PrismalFrameLayout_pfl_glassColor, Color.TRANSPARENT))
                setLightDirection(
                    getFloat(R.styleable.PrismalFrameLayout_pfl_lightDirX, 1.0f),
                    getFloat(R.styleable.PrismalFrameLayout_pfl_lightDirY, 1.0f)
                )
                setSpecular(
                    getFloat(R.styleable.PrismalFrameLayout_pfl_specular, 2.4184f),
                    getFloat(R.styleable.PrismalFrameLayout_pfl_shininess, 152.6f)
                )
                setRimStrength(getFloat(R.styleable.PrismalFrameLayout_pfl_rimStrength, 0.18f))
                setDispersion(
                    getFloat(R.styleable.PrismalFrameLayout_pfl_dispersionR, 0.39000002f),
                    getFloat(R.styleable.PrismalFrameLayout_pfl_dispersionB, 0.375f)
                )
                setCausticIntensity(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_causticIntensity,
                        0.1445f
                    )
                )
                setTransmittance(getFloat(R.styleable.PrismalFrameLayout_pfl_transmittance, 1.0f))
                setLiquidDomeStrength(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_liquidDome,
                        1.3f
                    )
                )
                setFresnelReflectStrength(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_fresnelReflect,
                        1.98f
                    )
                )
                setLensRefractionScale(
                    getFloat(
                        R.styleable.PrismalFrameLayout_pfl_lensRefractionScale,
                        1.2973684f
                    )
                )
                getInt(R.styleable.PrismalFrameLayout_pfl_captureDownsample, -1)
                    .takeIf { it >= 0 }
                    ?.let { captureDownsampleMode = DownsampleMode.entries[it.coerceIn(0, DownsampleMode.entries.lastIndex)] }
            } finally {
                recycle()
            }
        }
        addView(glSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        isChildrenDrawingOrderEnabled = true

        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnScrollChangedListener(scrollListener)

        clickAnimSpring.onUpdate = { t ->
            val s = 1f + (clickAnimPressScale - 1f) * t.coerceIn(0f, 1f)
            pivotX = width / 2f
            pivotY = height / 2f
            scaleX = s
            scaleY = s
        }
        clickAnimSpring.snapTo(0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, clipRadius, clipRadius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withClip(clipPath) {
            super.dispatchDraw(canvas)
            if (hasClickCallback && glowAlpha > 0f) {
                val maxR = hypot(width.toFloat(), height.toFloat())
                glowPaint.shader = RadialGradient(
                    glowX, glowY, maxR,
                    intArrayOf(
                        Color.argb((glowAlpha * 100).toInt(), 255, 255, 255),
                        Color.argb((glowAlpha * 45).toInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.38f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(glowX, glowY, maxR, glowPaint)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        Choreographer.getInstance().removeFrameCallback(glowCallback)
        clickAnimSpring.cancel()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        hasClickListenerCallback = l != null
        updateClickableState()
        super.setOnClickListener(l)
    }

    /**
     * Sets a click listener with a spring press-scale animation and radial glow.
     * Independent of [setOnClickListener] — use this on glass cards; child components
     * (e.g. [PrismalSwitch]) can keep their own touch handling without conflict.
     */
    fun setOnClickWithAnimationListener(l: (() -> Unit)?) {
        clickWithAnimListener = l
        updateClickableState()
    }

    /** Java-friendly overload for [setOnClickWithAnimationListener]. */
    fun setOnClickWithAnimationListener(l: OnClickListener?) {
        setOnClickWithAnimationListener(if (l != null) { { l.onClick(this) } } else null)
    }

    /**
     * Target scale while pressed (default `0.96`). `1.0` disables the shrink effect.
     */
    fun setClickAnimationPressScale(scale: Float) {
        clickAnimPressScale = scale.coerceIn(0.5f, 1f)
    }

    private fun updateClickableState() {
        val wantsClick = hasClickListenerCallback || clickWithAnimListener != null
        isClickable = wantsClick
        isFocusable = wantsClick
    }

    private fun pulseGlow(at: MotionEvent) {
        if (!hasClickCallback) return
        val ch = Choreographer.getInstance()
        when (at.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                glowX = at.x
                glowY = at.y
                glowIn = true
                glowLastNanos = 0L
                ch.removeFrameCallback(glowCallback)
                ch.postFrameCallback(glowCallback)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                glowIn = false
                glowLastNanos = 0L
                ch.removeFrameCallback(glowCallback)
                ch.postFrameCallback(glowCallback)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (clickWithAnimListener != null) {
            pulseGlow(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    clickAnimSpring.animateTo(1f)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    clickAnimSpring.animateTo(0f)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        clickWithAnimListener?.invoke()
                        performClick()
                    }
                }
            }
            return true
        }

        if (hasClickListenerCallback || glowEnabled) {
            pulseGlow(event)
        }
        return super.onTouchEvent(event)
    }

    internal fun setGlowEnabled(enabled: Boolean) {
        glowEnabled = enabled
    }

    internal fun showGlow(x: Float, y: Float) {
        if (!hasClickCallback) return
        glowX = x; glowY = y
        glowIn = true; glowLastNanos = 0L
        val ch = Choreographer.getInstance()
        ch.removeFrameCallback(glowCallback)
        ch.postFrameCallback(glowCallback)
    }

    internal fun hideGlow() {
        if (!hasClickCallback) return
        glowIn = false; glowLastNanos = 0L
        val ch = Choreographer.getInstance()
        ch.removeFrameCallback(glowCallback)
        ch.postFrameCallback(glowCallback)
    }

    /**
     * Marks that a parent view (e.g. [PrismalSlider]) owns backdrop updates.
     * While `true`, scroll and layout listeners will not schedule captures; call
     * [updateBackground] from the parent when the glass content should refresh.
     */
    fun setBackdropHandledByChild(handled: Boolean) {
        backdropHandledByChild = handled
    }

    /** Invoked on the main thread after a backdrop bitmap is uploaded to the GPU. */
    fun setOnBackdropCapturedListener(listener: (() -> Unit)?) {
        onBackdropCaptured = listener
    }

    private fun notifyBackdropCaptured() {
        post { onBackdropCaptured?.invoke() }
    }

    private fun scheduleCaptureBackground() {
        if (backdropHandledByChild) return
        if (!captureScheduled) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCaptureTime

            if (elapsed < minCaptureInterval) {
                postDelayed({
                    captureScheduled = false
                    scheduleCaptureBackground()
                }, minCaptureInterval - elapsed)
                captureScheduled = true
            } else {
                captureScheduled = true
                post {
                    captureScheduled = false
                    captureAndSetBackground()
                    lastCaptureTime = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * When set (e.g. to a [com.matrix.prismal.PrismalSwitch]), background capture draws this host
     * in local coordinates so the track stays aligned under the thumb instead of sampling the
     * full window with parallax skew.
     */
    fun setCaptureHost(host: ViewGroup?) {
        captureHost = host
    }

    private fun captureScale(): Float =
        captureDownsampleMode?.scale ?: (3f / captureBlurRadius.coerceAtLeast(3f)).coerceIn(0.25f, 1f)

    internal fun captureAndSetBackground() {
        if (width <= 0 || height <= 0) return

        try {
            val host = captureHost
            if (host != null) captureFromHost(host) else captureFromRoot()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun captureFromHost(host: ViewGroup) {
        val savedScaleX = scaleX
        val savedScaleY = scaleY
        scaleX = 1f
        scaleY = 1f

        val hostLoc = IntArray(2)
        host.getLocationInWindow(hostLoc)
        val viewLoc = IntArray(2)
        getLocationInWindow(viewLoc)

        val relX = viewLoc[0] - hostLoc[0]
        val relY = viewLoc[1] - hostLoc[1]

        scaleX = savedScaleX
        scaleY = savedScaleY

        val sx = savedScaleX.coerceAtLeast(1f)
        val sy = savedScaleY.coerceAtLeast(1f)
        val extraX = ((sx - 1f) * pivotX).toInt()
        val extraY = ((sy - 1f) * pivotY).toInt()

        val cropX = (relX - extraX).coerceAtLeast(0)
        val cropY = (relY - extraY).coerceAtLeast(0)
        val cropW = (width + 2 * extraX).coerceAtMost(host.width - cropX)
        val cropH = (height + 2 * extraY).coerceAtMost(host.height - cropY)

        if (cropW <= 0 || cropH <= 0) return

        val ds = captureScale()
        val croppedBitmap = createBitmap((cropW * ds).toInt().coerceAtLeast(1), (cropH * ds).toInt().coerceAtLeast(1))
        val canvas = Canvas(croppedBitmap)
        canvas.scale(ds, ds)
        canvas.translate(-cropX.toFloat(), -cropY.toFloat())

        alpha = 0f
        canvas.drawColor(Color.WHITE)
        host.draw(canvas)
        alpha = 1f

        glSurface.queueAndRender {
            renderer.setBackdropSampleScale(scaleX.coerceAtLeast(1f), scaleY.coerceAtLeast(1f))
            renderer.setBackgroundTexture(croppedBitmap)
            notifyBackdropCaptured()
        }
    }

    private fun captureFromRoot() {
        val root = rootView as? ViewGroup ?: return

        val savedScaleX = scaleX
        val savedScaleY = scaleY
        scaleX = 1f
        scaleY = 1f

        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val viewLoc = IntArray(2)
        getLocationOnScreen(viewLoc)

        scaleX = savedScaleX
        scaleY = savedScaleY

        val relX = viewLoc[0] - rootLoc[0]
        val relY = viewLoc[1] - rootLoc[1]

        val sx = savedScaleX.coerceAtLeast(1f)
        val sy = savedScaleY.coerceAtLeast(1f)
        val extraX = ((sx - 1f) * pivotX).toInt()
        val extraY = ((sy - 1f) * pivotY).toInt()

        val cropX = (relX - extraX).coerceAtLeast(0)
        val cropY = (relY - extraY).coerceAtLeast(0)
        val cropW = (width + 2 * extraX).coerceAtMost(root.width - cropX)
        val cropH = (height + 2 * extraY).coerceAtMost(root.height - cropY)

        if (cropW <= 0 || cropH <= 0) return

        val ds = captureScale()
        val croppedBitmap = createBitmap((cropW * ds).toInt().coerceAtLeast(1), (cropH * ds).toInt().coerceAtLeast(1))
        val canvas = Canvas(croppedBitmap)
        canvas.scale(ds, ds)
        canvas.translate(-cropX.toFloat(), -cropY.toFloat())

        alpha = 0f
        canvas.drawColor(Color.WHITE)
        root.draw(canvas)
        alpha = 1f

        glSurface.queueAndRender {
            renderer.setBackdropSampleScale(scaleX.coerceAtLeast(1f), scaleY.coerceAtLeast(1f))
            renderer.setBackgroundTexture(croppedBitmap)
            notifyBackdropCaptured()
        }
    }

    /**
     * Captures the backdrop and uploads it to the GPU. Always runs when called explicitly,
     * including when [setBackdropHandledByChild] is `true`.
     */
    fun updateBackground() {
        if (width <= 0 || height <= 0) return
        post {
            captureAndSetBackground()
            lastCaptureTime = System.currentTimeMillis()
        }
    }

    /**
     * Pushes the current [scaleX]/[scaleY] to the backdrop sample scale uniform without
     * triggering a full bitmap re-capture. Call this on every scale-animation frame so the
     * glass correctly "zooms out" to cover the expanded visual area even between captures.
     */
    fun updateBackdropScale() {
        val sx = scaleX.coerceAtLeast(1f)
        val sy = scaleY.coerceAtLeast(1f)
        glSurface.queueAndRender { renderer.setBackdropSampleScale(sx, sy) }
    }

    /**
     * Sets the refraction inset value, which controls the distance from the edge where refraction effects are applied.
     *
     * @param value The inset value in pixels (default: 20f).
     */
    fun setRefractionInset(value: Float) =
        glSurface.queueAndRender { renderer.setRefractionInset(value) }

    /**
     * Sets the size of the glass effect area.
     *
     * @param width The width of the glass in pixels.
     * @param height The height of the glass in pixels.
     */
    fun setGlassSize(width: Float, height: Float) =
        glSurface.queueAndRender { renderer.setGlassSize(width, height) }

    /**
     * Sets the corner radius for rounded corners on the glass effect.
     *
     * @param radius The radius in pixels (default: 30f).
     */
    fun setCornerRadius(radius: Float) {
        clipRadius = radius
        glSurface.queueAndRender { renderer.setCornerRadius(radius) }
        postInvalidateOnAnimation()
    }

    /**
     * Sets the Index of Refraction (IOR) for the glass material, affecting light bending.
     *
     * @param value The IOR value (typical range: 1.3-1.6 for glass; default: 1.5f).
     */
    fun setIOR(value: Float) = glSurface.queueAndRender { renderer.setIOR(value) }

    /**
     * Sets the thickness of the glass, influencing distortion and depth effects.
     *
     * @param value The thickness in pixels (default: 15f).
     */
    fun setThickness(value: Float) = glSurface.queueAndRender { renderer.setGlassThickness(value) }

    /**
     * Sets the strength of the normal mapping, controlling surface bumpiness and light interaction.
     *
     * @param value The strength multiplier (default: 1.2f).
     */
    fun setNormalStrength(value: Float) =
        glSurface.queueAndRender { renderer.setNormalStrength(value) }

    /**
     * Sets the scale for displacement mapping, which warps the texture based on height data.
     *
     * @param value The displacement scale multiplier (default: 1.0f).
     */
    fun setDisplacementScale(value: Float) =
        glSurface.queueAndRender { renderer.setDisplacementScale(value) }

    /**
     * Sets the blur factor applied based on the simulated glass height/depth.
     *
     * @param value The height-to-blur scaling factor (default: 8f).
     */
    fun setHeightBlurFactor(value: Float) =
        glSurface.queueAndRender { renderer.setHeightBlurFactor(value) }

    /**
     * Sets the minimum smoothing value for SDF (Signed Distance Field) operations in the shader.
     *
     * @param value The smoothing threshold (default: 1.0f).
     */
    fun setMinSmoothing(value: Float) =
        glSurface.queueAndRender { renderer.setSminSmoothing(value) }

    /**
     * Sets the radius for the overall blur effect in the glass rendering.
     *
     * @param value The blur radius in pixels (default: 2.5f).
     */
    fun setBlurRadius(value: Float) {
        captureBlurRadius = value
        glSurface.queueAndRender { renderer.setBlurRadius(value) }
    }

    /**
     * Sets the downsampling level for the background capture bitmap.
     *
     * Lower resolution reduces GPU upload cost and memory; the blur pass masks the loss of
     * sharpness. Use [DownsampleMode.OFF] for sharp, low-blur glass. Use
     * [DownsampleMode.AGGRESSIVE] for large, heavily-blurred surfaces where quality is
     * not critical.
     *
     * When not set (default), the scale is derived automatically from [setBlurRadius].
     *
     * @param mode The desired [DownsampleMode], or `null` to restore auto behaviour.
     */
    fun setCaptureDownsample(mode: DownsampleMode?) {
        captureDownsampleMode = mode
    }

    /**
     * Sets the width of highlight edges in the glass effect.
     *
     * @param value The highlight width in pixels (default: 4.0f).
     */
    fun setHighlightWidth(value: Float) =
        glSurface.queueAndRender { renderer.setHighlightWidth(value) }

    /**
     * Sets the intensity of chromatic aberration, simulating color fringing at edges.
     *
     * @param value The aberration strength (default: 2.0f).
     */
    fun setChromaticAberration(value: Float) =
        glSurface.queueAndRender { renderer.setChromaticAberration(value) }

    /**
     * Sets the overall brightness multiplier for the glass effect.
     *
     * @param value The brightness factor (default: 1.15f).
     */
    fun setBrightness(value: Float) = glSurface.queueAndRender { renderer.setBrightness(value) }

    /**
     * Toggles the display of normal vectors for debugging the surface normals.
     *
     * @param show True to show normals, false to hide (default: false).
     */
    fun setShowNormals(show: Boolean) = glSurface.queueAndRender { renderer.setShowNormals(show) }

    /**
     * Sets the shadow properties for the glass effect, including color and softness.
     *
     * @param color The shadow color as an Android ColorInt (ARGB).
     * @param softness The softness/feathering of the shadow (0.0-1.0; default: 0.2f).
     */
    fun setShadowProperties(color: Int, softness: Float) {
        glSurface.queueAndRender { renderer.setShadowProperties(color, softness) }
    }

    /**
     * Sets the falloff rate for refraction effects at the edges of the glass.
     *
     * @param value The falloff sharpness (higher values = sharper transition; default: 4f).
     */
    fun setEdgeRefractionFalloff(value: Float) =
        glSurface.queueAndRender { renderer.setEdgeRefractionFalloff(value) }

    /**
     * Enables or disables debug mode for the glass renderer (e.g., visualizing internals).
     *
     * @param value True to enable debug visuals, false to disable.
     */
    fun setDebug(value: Boolean) {
        debug = value
    }

    /**
     * Set the color of the glass
     * @param color The color of the glass
     */
    fun setGlassColor(color: Int) = glSurface.queueAndRender { renderer.setGlassColor(color) }

    fun setFilter(value: PrismalFilter) = glSurface.queueAndRender { renderer.setFilter(value) }

    /**
     * Sets the dominant light direction used for specular highlights and the directional edge arc.
     * Coordinates are 2-D screen-space direction; (−0.5, −0.8) places the light at upper-left.
     *
     * @param x Horizontal component of the light direction vector.
     * @param y Vertical component of the light direction vector.
     */
    fun setLightDirection(x: Float, y: Float) =
        glSurface.queueAndRender { renderer.setLightDirection(x, y) }

    /**
     * Controls the Blinn-Phong specular highlight: a crisp, directional glint characteristic of
     * real glass surfaces.
     *
     * @param intensity Brightness of the specular spot (0 = none, 1 = neutral, 3+ = very bright).
     * @param shininess Glossiness power - higher values produce a smaller, sharper highlight (e.g. 16–256).
     */
    fun setSpecular(intensity: Float, shininess: Float) =
        glSurface.queueAndRender { renderer.setSpecular(intensity, shininess) }

    /**
     * Sets the strength of the Fresnel-based rim/edge glow - the characteristic bright white ring
     * visible on the boundary of real glass. This is a defining visual trait of iOS-style glass.
     *
     * @param value Glow strength (0 = no rim, 0.6 = default, 2+ = very bright halo).
     */
    fun setRimStrength(value: Float) = glSurface.queueAndRender { renderer.setRimStrength(value) }

    /**
     * Sets per-channel chromatic dispersion multipliers, controlling how much red and blue light
     * separate from the reference green channel at glass edges (rainbow fringe effect).
     *
     * @param r Red-channel dispersion scale (> 0 = shifts outward from glass centre).
     * @param b Blue-channel dispersion scale (> 0 = shifts inward toward glass centre).
     */
    fun setDispersion(r: Float, b: Float) =
        glSurface.queueAndRender { renderer.setDispersion(r, b) }

    /**
     * Sets the intensity of the caustic inner-brightening effect, simulating light focusing
     * inside the glass volume. Creates warm bright patches that shift with the glass normals.
     *
     * @param value Caustic intensity (0 = disabled, 0.15 = subtle, 0.6+ = dramatic).
     */
    fun setCausticIntensity(value: Float) =
        glSurface.queueAndRender { renderer.setCausticIntensity(value) }

    /**
     * Controls the overall transmittance (opacity) of the glass layer.
     * At 1.0 the glass fully occupies its shape; at 0.0 it is invisible.
     *
     * @param value Transmittance in [0, 1] (default: 1.0).
     */
    fun setTransmittance(value: Float) =
        glSurface.queueAndRender { renderer.setTransmittance(value) }

    fun setLiquidDomeStrength(value: Float) =
        glSurface.queueAndRender { renderer.setLiquidDomeStrength(value) }

    fun setFresnelReflectStrength(value: Float) =
        glSurface.queueAndRender { renderer.setFresnelReflectStrength(value) }

    fun setLensRefractionScale(value: Float) =
        glSurface.queueAndRender { renderer.setLensRefractionScale(value) }

    fun setBackdropSampleScale(sx: Float, sy: Float) =
        glSurface.queueAndRender { renderer.setBackdropSampleScale(sx, sy) }

    fun setParallaxScale(value: Float) =
        glSurface.queueAndRender { renderer.setParallaxScale(value) }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (!debug) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                glSurface.queueAndRender {
                    renderer.setTouching(true)
                    renderer.setMousePosition(event.x, event.y)
                }
            }

            MotionEvent.ACTION_MOVE -> glSurface.queueAndRender {
                renderer.setMousePosition(event.x, event.y)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> glSurface.queueAndRender {
                renderer.setTouching(false)
            }
        }
        return true
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        return if (i == 0) 0 else i
    }


    fun GLSurfaceView.queueAndRender(block: () -> Unit) {
        queueEvent { block() }
        requestRender()
    }
}