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
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.matrix.prismal.filters.PrismalFilter
import com.matrix.prismal.renderer.PrismalGlassRenderer
import com.matrix.prismal.utils.LiquidGlassInteraction
import com.matrix.prismal.utils.SpringAnimator
import androidx.core.graphics.withClip
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

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
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
    }
    private val renderer: PrismalGlassRenderer = PrismalGlassRenderer(context)
    private var lastWidth = 0
    private var lastHeight = 0
    private var debug = false
    private var captureHost: ViewGroup? = null

    private var capturePending = false
    private val bakeRender = Runnable { if (renderer.markRenderRequested()) glSurface.requestRender() }
    private val captureRunnable = Runnable {
        capturePending = false
        captureAndSetBackground()
    }
    internal var isCapturing = false

    internal val surfaceView: android.view.SurfaceView get() = glSurface

    internal fun setSurfaceOnTop() = glSurface.setZOrderOnTop(true)

    internal fun setBackdropPassEnabled(enabled: Boolean) =
        glSurface.queueAndRender { renderer.drawBackdropPass = enabled }

    /** Draws extra content into the captured backdrop, in [captureRegion] source coordinates. */
    internal var onCaptureDrawn: ((Canvas, ViewGroup, Bitmap) -> Unit)? = null
    private var backdropStale = false
    private val visibleRect = Rect()
    private val captureCanvas = Canvas()
    private val bitmapPool = ArrayList<Bitmap>(MAX_POOLED_BITMAPS)
    private val sourceLoc = IntArray(2)
    private val viewLoc = IntArray(2)

    private var lastCapTranslationX = Float.NaN
    private var lastCapTranslationY = Float.NaN
    private var lastCapScaleX = Float.NaN
    private var lastCapScaleY = Float.NaN
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
    private var expandOnPress = true
    private var clickAnimExpandDp = LiquidGlassInteraction.DEFAULT_PRESS_EXPAND_DP
    private val pressSpring = SpringAnimator(0.5f, 300f)
    private val dragSpringX = SpringAnimator(0.5f, 300f)
    private val dragSpringY = SpringAnimator(0.5f, 300f)
    private var interactiveEffects = false
    private var dragging = false
    private var touchOriginX = 0f
    private var touchOriginY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var glowX = 0f
    private var glowY = 0f
    private var glowAlpha = 0f
    private var glowIn = false
    private var glowLastNanos = 0L
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowShader = RadialGradient(
        0f, 0f, 1f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
    )
    private val glowMatrix = Matrix()

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
        scheduleCaptureBackground(requireOnScreen = true)
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
        } else if (backdropStale) {
            scheduleCaptureBackground(requireOnScreen = true)
        }
    }

    init {
        glowPaint.shader = glowShader
        renderer.onSurfaceReady = {
            post { if (!backdropHandledByChild) updateBackground() }
        }
        renderer.onCacheStale = {
            removeCallbacks(bakeRender)
            postDelayed(bakeRender, CACHE_BAKE_DELAY_MS)
        }
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

        bindInteractionSprings()
    }

    private fun bindInteractionSprings() {
        val onFrame = {
            applyInteractionVisuals()
        }
        pressSpring.onUpdate = { onFrame() }
        pressSpring.onSettled = { updateBackgroundIfMoved() }
        dragSpringX.onUpdate = { if (!dragging) onFrame() }
        dragSpringY.onUpdate = { if (!dragging) onFrame() }
        pressSpring.snapTo(0f)
        dragSpringX.snapTo(0f)
        dragSpringY.snapTo(0f)
    }

    private fun applyInteractionVisuals() {
        if (width <= 0 || height <= 0) return
        val progress = pressSpring.value.coerceIn(0f, 1f)
        val dragX = if (dragging) dragOffsetX else dragSpringX.value
        val dragY = if (dragging) dragOffsetY else dragSpringY.value
        val transform = LiquidGlassInteraction.computeViewTransform(
            width = width,
            height = height,
            density = resources.displayMetrics.density,
            pressProgress = progress,
            dragOffsetX = dragX,
            dragOffsetY = dragY,
            expandDp = clickAnimExpandDp,
            expandOnPress = expandOnPress,
            legacyPressScale = clickAnimPressScale,
        )
        pivotX = width / 2f
        pivotY = height / 2f
        scaleX = transform.scaleX
        scaleY = transform.scaleY
        translationX = transform.translationX
        translationY = transform.translationY

        val highlightX = if (interactiveEffects) touchOriginX + dragX else glowX
        val highlightY = if (interactiveEffects) touchOriginY + dragY else glowY
        pushPressInteraction(progress, highlightX, highlightY)

        if (progress > 0.02f || transform.scaleX > 1.02f || transform.scaleY > 1.02f) {
            updateBackgroundIfMoved()
        }
        invalidate()
    }

    private fun pushPressInteraction(
        progress: Float,
        highlightX: Float,
        highlightY: Float,
        backdropPinch: Float = LiquidGlassInteraction.DEFAULT_BACKDROP_PINCH,
        glowStrength: Float = 1f,
    ) {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val pinch = if (progress > 0.001f) backdropPinch else 1f
        val strength = if (progress > 0.001f) glowStrength else 0f
        glSurface.queueAndRender {
            renderer.setPressInteraction(
                progress,
                pinch,
                (highlightX / w).coerceIn(0f, 1f),
                (1f - highlightY / h).coerceIn(0f, 1f),
                strength,
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, clipRadius, clipRadius, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        if (isCapturing) return
        super.draw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isCapturing) return
        canvas.withClip(clipPath) {
            super.dispatchDraw(canvas)
            val progress = when {
                interactiveEffects -> pressSpring.value.coerceIn(0f, 1f)
                else -> glowAlpha
            }
            if (hasClickCallback && progress > 0.001f) {
                glowFillPaint.color = Color.argb((progress * 20f).toInt().coerceIn(0, 255), 255, 255, 255)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowFillPaint)

                val cx = if (interactiveEffects) touchOriginX + if (dragging) dragOffsetX else dragSpringX.value else glowX
                val cy = if (interactiveEffects) touchOriginY + if (dragging) dragOffsetY else dragSpringY.value else glowY
                val glowR = minOf(width, height) * 1.5f
                glowMatrix.setScale(glowR, glowR)
                glowMatrix.postTranslate(cx, cy)
                glowShader.setLocalMatrix(glowMatrix)
                glowPaint.alpha = (progress * 38f).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, glowR, glowPaint)
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
        removeCallbacks(captureRunnable)
        removeCallbacks(bakeRender)
        capturePending = false
        clearBitmapPool()
        Choreographer.getInstance().removeFrameCallback(glowCallback)
        pressSpring.cancel()
        dragSpringX.cancel()
        dragSpringY.cancel()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        hasClickListenerCallback = l != null
        updateClickableState()
        super.setOnClickListener(l)
    }

    /**
     * Sets a click listener with a spring press-scale animation and radial glow.
     * Independent of [setOnClickListener] - use this on glass cards; child components
     * (e.g. [PrismalSwitch]) can keep their own touch handling without conflict.
     */
    fun setOnClickWithAnimationListener(l: (() -> Unit)?) {
        clickWithAnimListener = l
        interactiveEffects = l != null
        updateClickableState()
    }

    /** Java-friendly overload for [setOnClickWithAnimationListener]. */
    fun setOnClickWithAnimationListener(l: OnClickListener?) {
        setOnClickWithAnimationListener(if (l != null) { { l.onClick(this) } } else null)
    }

    /**
     * Target scale while pressed when [setExpandOnPress] is `false` (legacy shrink mode).
     * Default `0.96`. Ignored when expand-on-press is enabled.
     */
    fun setClickAnimationPressScale(scale: Float) {
        clickAnimPressScale = scale.coerceIn(0.5f, 1f)
    }

    /** When `true` (default), the glass expands on press */
    fun setExpandOnPress(expand: Boolean) {
        expandOnPress = expand
    }

    /** Extra dp added to height on full press */
    fun setClickAnimationExpandDp(dp: Float) {
        clickAnimExpandDp = dp.coerceAtLeast(0f)
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
            handleInteractiveTouch(event)
            return true
        }

        if (hasClickListenerCallback || glowEnabled) {
            pulseGlow(event)
        }
        return super.onTouchEvent(event)
    }

    private fun handleInteractiveTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                touchOriginX = event.x
                touchOriginY = event.y
                dragOffsetX = 0f
                dragOffsetY = 0f
                dragSpringX.snapTo(0f)
                dragSpringY.snapTo(0f)
                pressSpring.animateTo(1f)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                dragOffsetX = event.x - touchOriginX
                dragOffsetY = event.y - touchOriginY
                applyInteractionVisuals()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                pressSpring.animateTo(0f)
                dragSpringX.animateTo(0f)
                dragSpringY.animateTo(0f)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    clickWithAnimListener?.invoke()
                    performClick()
                }
            }
        }
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (backdropStale && visibility == VISIBLE) scheduleCaptureBackground(requireOnScreen = true)
    }

    private fun isOnScreen(): Boolean = isShown && getGlobalVisibleRect(visibleRect)

    private fun scheduleCaptureBackground(requireOnScreen: Boolean = false) {
        if (backdropHandledByChild) return
        if (requireOnScreen && !isOnScreen()) {
            backdropStale = true
            return
        }
        updateBackground()
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
            if (host != null) {
                captureRegion(host, inWindow = true)
            } else {
                val root = rootView as? ViewGroup ?: return
                BackdropCaptureBatch.enqueue(root, this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal class CaptureCrop(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val scale: Float,
        val sampleScaleX: Float,
        val sampleScaleY: Float,
    ) {
        val gridX = floor(x * scale).toInt()
        val gridY = floor(y * scale).toInt()
        val bitmapWidth = (ceil((x + width) * scale).toInt() - gridX).coerceAtLeast(1)
        val bitmapHeight = (ceil((y + height) * scale).toInt() - gridY).coerceAtLeast(1)
        val uvOffsetX get() = (x * scale - gridX) / bitmapWidth
        val uvOffsetY get() = (y * scale - gridY) / bitmapHeight
        val uvScaleX get() = width * scale / bitmapWidth
        val uvScaleY get() = height * scale / bitmapHeight
    }

    internal fun computeCrop(source: ViewGroup, inWindow: Boolean): CaptureCrop? {
        val savedScaleX = scaleX
        val savedScaleY = scaleY
        scaleX = 1f
        scaleY = 1f

        if (inWindow) {
            source.getLocationInWindow(sourceLoc)
            getLocationInWindow(viewLoc)
        } else {
            source.getLocationOnScreen(sourceLoc)
            getLocationOnScreen(viewLoc)
        }

        scaleX = savedScaleX
        scaleY = savedScaleY

        val relX = viewLoc[0] - sourceLoc[0]
        val relY = viewLoc[1] - sourceLoc[1]

        val sx = savedScaleX.coerceAtLeast(1f)
        val sy = savedScaleY.coerceAtLeast(1f)
        val extraX = ((sx - 1f) * pivotX).toInt()
        val extraY = ((sy - 1f) * pivotY).toInt()

        val cropX = (relX - extraX).coerceAtLeast(0)
        val cropY = (relY - extraY).coerceAtLeast(0)
        val cropW = (width + 2 * extraX).coerceAtMost(source.width - cropX)
        val cropH = (height + 2 * extraY).coerceAtMost(source.height - cropY)

        if (cropW <= 0 || cropH <= 0) return null
        return CaptureCrop(cropX, cropY, cropW, cropH, captureScale(), sx, sy)
    }

    internal fun captureRegion(source: ViewGroup, inWindow: Boolean) {
        val crop = computeCrop(source, inWindow) ?: return
        val ds = crop.scale
        val bitmap = obtainCaptureBitmap(crop.bitmapWidth, crop.bitmapHeight)
        val canvas = captureCanvas
        canvas.setBitmap(bitmap)
        val saveCount = canvas.save()
        isCapturing = true
        try {
            canvas.translate(-crop.gridX.toFloat(), -crop.gridY.toFloat())
            canvas.scale(ds, ds)
            canvas.drawColor(Color.WHITE)
            source.draw(canvas)
            onCaptureDrawn?.invoke(canvas, source, bitmap)
        } catch (e: Exception) {
            releaseCaptureBitmap(bitmap)
            throw e
        } finally {
            isCapturing = false
            canvas.restoreToCount(saveCount)
            canvas.setBitmap(null)
        }
        submitCapture(bitmap, crop)
    }

    internal fun submitCapture(bitmap: Bitmap, crop: CaptureCrop) {
        val sx = crop.sampleScaleX
        val sy = crop.sampleScaleY
        backdropStale = false
        lastCapTranslationX = translationX
        lastCapTranslationY = translationY
        lastCapScaleX = sx
        lastCapScaleY = sy

        val uvOffsetX = crop.uvOffsetX
        val uvOffsetY = crop.uvOffsetY
        val uvScaleX = crop.uvScaleX
        val uvScaleY = crop.uvScaleY
        val ssx = sx / scaleX.coerceAtLeast(1f)
        val ssy = sy / scaleY.coerceAtLeast(1f)
        glSurface.queueAndRender {
            renderer.setBackdropSampleScale(ssx, ssy)
            renderer.setBackdropUvTransform(uvOffsetX, uvOffsetY, uvScaleX, uvScaleY)
            renderer.setBackgroundTexture(bitmap)
            releaseCaptureBitmap(bitmap)
            notifyBackdropCaptured()
        }
    }

    internal fun obtainCaptureBitmap(w: Int, h: Int): Bitmap {
        val needed = w * h * 4
        synchronized(bitmapPool) {
            bitmapPool.removeAll { it.isRecycled }
            bitmapPool.indexOfFirst { it.width == w && it.height == h }
                .takeIf { it >= 0 }
                ?.let { return bitmapPool.removeAt(it) }
            val reusable = bitmapPool.indexOfFirst { it.allocationByteCount in needed..needed * 2 }
            if (reusable >= 0) {
                val b = bitmapPool.removeAt(reusable)
                try {
                    b.reconfigure(w, h, Bitmap.Config.ARGB_8888)
                    return b
                } catch (_: IllegalArgumentException) {
                    b.recycle()
                }
            }
        }
        return createBitmap(w, h)
    }

    internal fun releaseCaptureBitmap(bitmap: Bitmap) {
        synchronized(bitmapPool) {
            if (!bitmap.isRecycled && bitmapPool.size < MAX_POOLED_BITMAPS) {
                bitmapPool.add(bitmap)
                return
            }
        }
        bitmap.recycle()
    }

    private fun clearBitmapPool() {
        synchronized(bitmapPool) {
            bitmapPool.forEach { it.recycle() }
            bitmapPool.clear()
        }
    }

    /**
     * Captures the backdrop and uploads it to the GPU. Always runs when called explicitly,
     * including when [setBackdropHandledByChild] is `true`.
     */
    fun updateBackground() {
        if (width <= 0 || height <= 0) return
        if (capturePending) return
        capturePending = true
        post(captureRunnable)
    }

    internal fun updateBackgroundIfMoved() {
        val sx = scaleX.coerceAtLeast(1f)
        val sy = scaleY.coerceAtLeast(1f)
        val moved = lastCapScaleX.isNaN() ||
            abs(translationX - lastCapTranslationX) >= CAPTURE_MOVE_EPSILON_PX ||
            abs(translationY - lastCapTranslationY) >= CAPTURE_MOVE_EPSILON_PX ||
            abs(sx - lastCapScaleX) * width >= CAPTURE_MOVE_EPSILON_PX ||
            abs(sy - lastCapScaleY) * height >= CAPTURE_MOVE_EPSILON_PX
        if (moved) updateBackground()
    }

    /**
     * Re-fits the last captured backdrop to the current [scaleX]/[scaleY] without a full
     * bitmap re-capture. Call this on every scale-animation frame between captures.
     */
    fun updateBackdropScale() {
        if (lastCapScaleX.isNaN()) return
        val sx = lastCapScaleX / scaleX.coerceAtLeast(1f)
        val sy = lastCapScaleY / scaleY.coerceAtLeast(1f)
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

    /**
     * Drives press interaction for custom components (switch thumb, icon button, etc.)
     * @param progress Press amount 0–1
     * @param dragOffsetX Finger drag from touch origin in px
     * @param dragOffsetY Finger drag from touch origin in px
     * @param highlightX Glow center X in view coords
     * @param highlightY Glow center Y in view coords
     */
    fun setPressInteraction(
        progress: Float,
        dragOffsetX: Float = 0f,
        dragOffsetY: Float = 0f,
        highlightX: Float = width / 2f,
        highlightY: Float = height / 2f,
        applyTransform: Boolean = true,
        backdropPinch: Float = LiquidGlassInteraction.DEFAULT_BACKDROP_PINCH,
        glowStrength: Float = 1f,
    ) {
        if (applyTransform && width > 0 && height > 0) {
            val transform = LiquidGlassInteraction.computeViewTransform(
                width = width,
                height = height,
                density = resources.displayMetrics.density,
                pressProgress = progress,
                dragOffsetX = dragOffsetX,
                dragOffsetY = dragOffsetY,
                expandDp = clickAnimExpandDp,
                expandOnPress = expandOnPress,
                legacyPressScale = clickAnimPressScale,
            )
            pivotX = width / 2f
            pivotY = height / 2f
            scaleX = transform.scaleX
            scaleY = transform.scaleY
            translationX = transform.translationX
            translationY = transform.translationY
            if (progress > 0.02f || transform.scaleX > 1.02f) {
                updateBackgroundIfMoved()
            }
        }
        pushPressInteraction(progress, highlightX, highlightY, backdropPinch, glowStrength)
        if (progress > 0.001f) invalidate()
    }

    fun clearPressInteraction() {
        pressSpring.snapTo(0f)
        dragSpringX.snapTo(0f)
        dragSpringY.snapTo(0f)
        dragging = false
        dragOffsetX = 0f
        dragOffsetY = 0f
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        glSurface.queueAndRender { renderer.clearPressInteraction() }
        updateBackground()
        invalidate()
    }

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
        renderer.post(block)
        if (renderer.markRenderRequested()) requestRender()
    }

    private companion object {
        const val MAX_POOLED_BITMAPS = 2
        const val CAPTURE_MOVE_EPSILON_PX = 0.5f
        const val CACHE_BAKE_DELAY_MS = 120L
    }
}