package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.matrix.prismal.utils.LiquidGlassInteraction
import com.matrix.prismal.utils.SpringAnimator
import com.matrix.prismal.utils.applyThumbLens
import androidx.core.graphics.ColorUtils
import com.matrix.prismal.utils.ThumbShadowView
import kotlin.math.abs
import kotlin.math.min
import androidx.core.graphics.toColorInt

/**
 * **PrismalSlider** - An iOS-style liquid-glass slider with a draggable frosted thumb.
 *
 * The track is a thin 6 dp capsule with an accent-coloured fill; the thumb is a
 * 40 × 24 dp capsule that appears frosted-white at rest and reveals live liquid-glass
 * on press. Spring physics drive press/release animation and velocity-based squish
 * deformation while dragging.
 *
 * ### Key Features
 * - Continuous value range with configurable maximum ([setMaxValue]).
 * - Frosted thumb at rest; live glass refraction on press.
 * - Spring-physics press scale and velocity-based squish deformation.
 * - Configurable thumb optical parameters via `setThumb*` methods.
 *
 * ### Usage Example
 * ```kotlin
 * val slider = PrismalSlider(context).apply {
 *     setMaxValue(100f)
 *     setValue(42f)
 *     setOnValueChangedListener { value -> Log.d("Slider", "value=$value") }
 * }
 * ```
 *
 * XML attributes: `psl_maxValue`, `psl_trackColor` (see [R.styleable.PrismalSlider]).
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val thumbW = dp(40f)
    private val thumbH = dp(24f)
    private val trackH = dp(6f)
    private val thumbR = thumbH / 2f
    private var maxValue = 100f
    private var currentValue = 0f
    private var onValueChanged: ((Float) -> Unit)? = null
    private var restBlur = 0f
    private var pressChromatic = 50f
    private var accentColor = "#0088FF".toColorInt()
    private var thumbIOR = 1.4f
    private var thumbBrightness = 1f
    private var thumbNormalStrength = 0.2f
    private var thumbDisplacementScale = 0.6f
    private var thumbRefractionAmount = 14f
    private var thumbRestLensScale = 0.38f
    private var thumbShadowColor = Color.argb(38, 0, 0, 0)
    private var thumbShadowSoftness = 0.25f
    private var thumbThicknessPx = -1f
    private var thumbHighlightWidth = -1f
    private var thumbHeightBlurFactor = -1f
    private var thumbMinSmoothing = 2.5f
    private var thumbCornerRadiusPx = -1f
    private var thumbRefractionInset = -1f
    private var thumbEdgeFalloff = -1f
    private var thumbParallaxScale = 0.4f
    private var thumbShowNormals = false
    private var thumbGlassColor = Color.argb(28, 255, 255, 255)
    private val trackBgColor = Color.argb(51, 0x78, 0x78, 0x78)
    private val track = object : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = trackBgColor }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = accentColor }
        var progress = 0f
            set(v) {
                field = v; invalidate()
            }

        fun setFillColor(c: Int) {
            fillPaint.color = c; invalidate()
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = h / 2f
            c.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
            val fw = w * progress
            if (fw > 0f) c.drawRoundRect(0f, 0f, fw.coerceAtLeast(h), h, r, r, fillPaint)
        }
    }

    private val thumb = PrismalFrameLayout(context)
    private val shadow = ThumbShadowView(context, dp(4f))
    private var progress = 0f
    private val overlay = View(context).also { it.setBackgroundColor(Color.WHITE) }
    private val pressSpring = SpringAnimator(1.0f, 1000f)
    private val scaleXSpring = SpringAnimator(0.6f, 250f)
    private val scaleYSpring = SpringAnimator(0.7f, 250f)
    private var dragging = false
    private var lastRawX = 0f
    private var velTracker: VelocityTracker? = null
    private var normVel = 0f
    private var thumbBackdropReady = false
    private var thumbCapturePending = false
    private var thumbCaptureInFlight = false
    private var lastThumbCaptureAt = 0L
    private var lastCapturedThumbX = Float.NaN

    private companion object {
        const val THUMB_CAPTURE_MIN_MS = 48L
        const val THUMB_CAPTURE_MOVE_PX = 6f
    }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v,
        resources.displayMetrics
    )

    private fun maxTravel() = (width - thumbW).coerceAtLeast(0f)
    private fun trackLength() = (width - thumbW / 2f).coerceAtLeast(1f)

    private fun layoutThumb() {
        thumb.translationX = (trackLength() * progress - thumbW / 4f).coerceIn(0f, maxTravel())
        track.progress = progress
        shadow.follow(thumb)
    }

    private fun isThumbInGlassState(): Boolean =
        dragging || pressSpring.target > 0f || pressSpring.value > 0.02f

    private fun scheduleThumbBackdropCapture(force: Boolean = false) {
        if (!isThumbInGlassState()) return
        if (thumbCaptureInFlight && !force) return
        val now = SystemClock.uptimeMillis()
        val moved = lastCapturedThumbX.isNaN() ||
            abs(thumb.translationX - lastCapturedThumbX) >= THUMB_CAPTURE_MOVE_PX
        if (!force && !moved && now - lastThumbCaptureAt < THUMB_CAPTURE_MIN_MS) {
            if (!thumbCapturePending) {
                thumbCapturePending = true
                val delay = THUMB_CAPTURE_MIN_MS - (now - lastThumbCaptureAt)
                postDelayed(thumbCaptureRunnable, delay.coerceAtLeast(1L))
            }
            return
        }
        thumbCapturePending = false
        removeCallbacks(thumbCaptureRunnable)
        thumbCaptureInFlight = true
        lastThumbCaptureAt = now
        lastCapturedThumbX = thumb.translationX
        thumb.updateBackground()
    }

    private fun updateThumbBackdrop() = scheduleThumbBackdropCapture(force = true)

    private fun applyPressState(t: Float) {
        val pressT = t.coerceIn(0f, 1f)

        thumb.setBlurRadius(lerp(restBlur, 0f, pressT))
        thumb.setChromaticAberration(pressChromatic * pressT)
        thumb.applyThumbLens(
            bandPx = thumbRefractionHeightPx() * pressT,
            amountPx = dp(thumbRefractionAmount) * lensScale() * pressT,
            press = pressT,
            dome = 0.2f,
            displacement = thumbDisplacementScale,
        )
        thumb.setIOR(thumbIOR)
        thumb.setBrightness(thumbBrightness)
        thumb.setThickness(if (thumbThicknessPx > 0f) thumbThicknessPx else 0.2f)
        thumb.setShadowProperties(
            ColorUtils.setAlphaComponent(thumbShadowColor, (Color.alpha(thumbShadowColor) * pressT).toInt()),
            thumbShadowSoftness
        )
        overlay.alpha = if (isThumbInGlassState() && !thumbBackdropReady) 1f else lerp(1f, 0f, pressT)
        thumb.setPressInteraction(
            progress = pressT,
            highlightX = thumbW / 2f,
            highlightY = thumbH / 2f,
            applyTransform = false,
            backdropPinch = lerp(1f, LiquidGlassInteraction.THUMB_BACKDROP_PINCH, pressT),
            glowStrength = pressT * LiquidGlassInteraction.THUMB_GLOW_STRENGTH,
        )
        if (isThumbInGlassState() && !thumbBackdropReady && !thumbCaptureInFlight && !thumbCapturePending) {
            scheduleThumbBackdropCapture(force = true)
        }
    }

    private fun lensScale() = 1f

    /** Refraction band in px at full press. */
    private fun thumbRefractionHeightPx(): Float =
        if (thumbHeightBlurFactor > 0f) dp(thumbHeightBlurFactor) else dp(10f) * lensScale()

    private fun applySquish() {
        val v = normVel.coerceIn(-0.2f, 0.2f)
        val sx = scaleXSpring.value / (1f - v * 0.75f)
        val sy = scaleYSpring.value * (1f - abs(v) * 0.25f)
        thumb.apply {
            pivotX = thumbW / 2f
            pivotY = thumbH / 2f
            scaleX = sx
            scaleY = sy
        }
        shadow.follow(thumb)
        if (isThumbInGlassState()) thumb.updateBackdropScale()
    }

    private fun updateProgress(dx: Float) {
        progress = (progress + dx / trackLength()).coerceIn(0f, 1f)
        layoutThumb()
        currentValue = progress * maxValue
        onValueChanged?.invoke(currentValue)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val touchListener = OnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velTracker?.recycle()
                velTracker = VelocityTracker.obtain().also { it.addMovement(e) }
                dragging = true
                thumbBackdropReady = false
                thumbCaptureInFlight = false
                lastCapturedThumbX = Float.NaN
                removeCallbacks(thumbCaptureRunnable)
                thumbCapturePending = false
                lastRawX = e.rawX
                scaleXSpring.animateTo(1.5f)
                scaleYSpring.animateTo(1.5f)
                pressSpring.animateTo(1f)
                scheduleThumbBackdropCapture(force = true)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return@OnTouchListener true
                velTracker!!.addMovement(e)
                velTracker!!.computeCurrentVelocity(1000)
                normVel = normVel * 0.6f + (velTracker!!.xVelocity / trackLength()) * 0.4f
                val dx = e.rawX - lastRawX
                lastRawX = e.rawX
                updateProgress(dx)
                scheduleThumbBackdropCapture()
                applySquish()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                thumbBackdropReady = false
                thumbCaptureInFlight = false
                lastCapturedThumbX = Float.NaN
                removeCallbacks(thumbCaptureRunnable)
                thumbCapturePending = false
                normVel = 0f
                velTracker?.recycle(); velTracker = null
                scaleXSpring.animateTo(1f)
                scaleYSpring.animateTo(1f)
                pressSpring.animateTo(0f)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        true
    }

    init {
        clipChildren = false
        clipToPadding = false
        minimumHeight = (thumbH * 1.7f).toInt()

        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, trackH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL
            leftMargin = (thumbW / 4f).toInt()
            rightMargin = (thumbW / 4f).toInt()
        })

        addView(shadow, LayoutParams(thumbW.toInt(), thumbH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        })

        addView(thumb, LayoutParams(thumbW.toInt(), thumbH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        })
        thumb.addView(
            overlay, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )
        thumb.setOnTouchListener(touchListener)

        pressSpring.onUpdate = { applyPressState(it); applySquish() }
        scaleXSpring.onUpdate = { applySquish() }
        scaleYSpring.onUpdate = { applySquish() }
        pressSpring.snapTo(0f)
        scaleXSpring.snapTo(1f)
        scaleYSpring.snapTo(1f)

        context.obtainStyledAttributes(attrs, R.styleable.PrismalSlider).use { a ->
            maxValue = a.getFloat(R.styleable.PrismalSlider_psl_maxValue, 100f)
            val c = a.getColor(R.styleable.PrismalSlider_psl_trackColor, accentColor)
            accentColor = c
            track.setFillColor(c)
            restBlur = a.getFloat(R.styleable.PrismalSlider_psl_thumbBlurRadius, restBlur)
            pressChromatic = a.getFloat(R.styleable.PrismalSlider_psl_thumbChromaticAberration, pressChromatic)
            thumbIOR = a.getFloat(R.styleable.PrismalSlider_psl_thumbIOR, thumbIOR)
            thumbBrightness = a.getFloat(R.styleable.PrismalSlider_psl_thumbBrightness, thumbBrightness)
            thumbNormalStrength = a.getFloat(R.styleable.PrismalSlider_psl_thumbNormalStrength, thumbNormalStrength)
            thumbDisplacementScale = a.getFloat(R.styleable.PrismalSlider_psl_thumbDisplacementScale, thumbDisplacementScale)
            thumbShadowColor = a.getColor(R.styleable.PrismalSlider_psl_thumbShadowColor, thumbShadowColor)
            thumbShadowSoftness = a.getFloat(R.styleable.PrismalSlider_psl_thumbShadowSoftness, thumbShadowSoftness)
            thumbThicknessPx = a.getDimension(R.styleable.PrismalSlider_psl_thumbThickness, -1f)
            thumbHighlightWidth = a.getFloat(R.styleable.PrismalSlider_psl_thumbHighlightWidth, -1f)
            thumbHeightBlurFactor = a.getFloat(R.styleable.PrismalSlider_psl_thumbHeightBlurFactor, -1f)
            thumbMinSmoothing = a.getFloat(R.styleable.PrismalSlider_psl_thumbMinSmoothing, thumbMinSmoothing)
            thumbCornerRadiusPx = a.getDimension(R.styleable.PrismalSlider_psl_thumbCornerRadius, -1f)
            thumbRefractionInset = a.getFloat(R.styleable.PrismalSlider_psl_thumbRefractionInset, -1f)
            thumbEdgeFalloff = a.getFloat(R.styleable.PrismalSlider_psl_thumbEdgeRefractionFalloff, -1f)
            thumbParallaxScale = a.getFloat(R.styleable.PrismalSlider_psl_thumbParallaxScale, thumbParallaxScale)
            thumbShowNormals = a.getBoolean(R.styleable.PrismalSlider_psl_thumbShowNormals, false)
            thumbGlassColor = a.getColor(R.styleable.PrismalSlider_psl_thumbColor, thumbGlassColor)
        }

        post { setupThumb(); applyPressState(0f) }
    }

    private val thumbCaptureRunnable = Runnable { scheduleThumbBackdropCapture(force = true) }

    /** Re-applies thumb-only optical calibration. */
    fun recalibrateThumb() {
        setupThumb()
        applyPressState(pressSpring.value.coerceIn(0f, 1f))
    }

    private fun setupThumb() {
        thumb.setBackdropHandledByChild(true)
        thumb.setOnBackdropCapturedListener {
            thumbCaptureInFlight = false
            thumbBackdropReady = true
            applyPressState(pressSpring.value)
            if (isThumbInGlassState() && thumb.translationX != lastCapturedThumbX) scheduleThumbBackdropCapture()
        }
        configureThumbGlass()
    }

    /** Small-capsule recipe — independent of [PrismalLiquidGlass] card defaults. */
    private fun configureThumbGlass() {
        val density = resources.displayMetrics.density
        val minDim = min(thumbW, thumbH)
        val minDimDp = minDim / density
        val refractionHeightDp = if (thumbHeightBlurFactor > 0f) {
            thumbHeightBlurFactor
        } else {
            (minDimDp * 0.42f).coerceIn(4f, 12f)
        }
        val thicknessPx = if (thumbThicknessPx > 0f) {
            thumbThicknessPx
        } else {
            dp((minDimDp * 0.30f).coerceIn(4f, 9f))
        }
        val insetPx = if (thumbRefractionInset > 0f) {
            thumbRefractionInset
        } else {
            (minDim * 0.06f).coerceIn(1.5f * density, 4f * density)
        }

        thumb.setCornerRadius(if (thumbCornerRadiusPx > 0f) thumbCornerRadiusPx else thumbR)
        thumb.setIOR(thumbIOR)
        thumb.setThickness(thicknessPx)
        thumb.setBlurRadius(restBlur)
        thumb.setBrightness(thumbBrightness)
        thumb.setGlassColor(thumbGlassColor)
        thumb.setLensRefractionScale(thumbRestLensScale)
        thumb.setDisplacementScale(thumbDisplacementScale)
        thumb.setNormalStrength(thumbNormalStrength)
        thumb.setLiquidDomeStrength(1.15f)
        thumb.setFresnelReflectStrength(2.0f)
        thumb.setRimStrength(0.75f)
        thumb.setSpecular(1.2f, 120f)
        thumb.setCausticIntensity(0.12f)
        thumb.setMinSmoothing(thumbMinSmoothing)
        thumb.setHighlightWidth(if (thumbHighlightWidth > 0f) thumbHighlightWidth else 0.45f)
        thumb.setLightDirection(-0.35f, -0.75f)
        thumb.setHeightBlurFactor(dp(refractionHeightDp * 0.28f))
        thumb.setRefractionInset(insetPx)
        thumb.setEdgeRefractionFalloff(if (thumbEdgeFalloff > 0f) thumbEdgeFalloff else 2f)
        thumb.setChromaticAberration(0f)
        thumb.setDispersion(0.4f, 0.4f)
        thumb.setTransmittance(1f)
        thumb.setShadowProperties(thumbShadowColor, thumbShadowSoftness)
        thumb.setShowNormals(thumbShowNormals)
        thumb.setParallaxScale(thumbParallaxScale)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        layoutThumb()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? android.view.ViewGroup)?.let {
            it.clipChildren = false
            it.clipToPadding = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(thumbCaptureRunnable)
        thumbCapturePending = false
        thumbCaptureInFlight = false
        pressSpring.cancel(); scaleXSpring.cancel(); scaleYSpring.cancel()
        velTracker?.recycle(); velTracker = null
    }

    /**
     * Sets the current slider value, clamped to `[0, maxValue]`.
     * Updates thumb position and track fill on the next layout pass.
     *
     * @param value The value to set.
     */
    fun setValue(value: Float) {
        progress = value.coerceIn(0f, maxValue) / maxValue
        currentValue = progress * maxValue
        post {
            layoutThumb()
            updateThumbBackdrop()
        }
    }

    /** @return The current slider value in `[0, maxValue]`. */
    fun getValue(): Float = currentValue

    /**
     * Sets the maximum value the slider can represent.
     *
     * @param v Maximum value; clamped to at least `1f`.
     */
    fun setMaxValue(v: Float) {
        maxValue = v.coerceAtLeast(1f)
    }

    /**
     * Registers a listener invoked whenever the value changes (drag or [setValue]).
     *
     * @param l Callback receiving the new value.
     */
    fun setOnValueChangedListener(l: (Float) -> Unit) {
        onValueChanged = l
    }

    /**
     * Triggers an update to the thumb's background texture.
     * Call when content beneath the slider changes (e.g. after scrolling).
     */
    fun updateBackground() = updateThumbBackdrop()

    /**
     * Returns the underlying [PrismalFrameLayout] used as the draggable thumb.
     * Useful for advanced customization beyond the `setThumb*` helpers.
     */
    fun getThumb(): PrismalFrameLayout = thumb

    /**
     * Sets the **Index of Refraction (IOR)** for the thumb glass surface.
     *
     * @param v IOR value, typically between `1.0f` and `2.0f`.
     */
    fun setThumbIOR(v: Float) {
        thumbIOR = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the normal-mapping strength on the thumb glass surface.
     *
     * @param v Strength multiplier.
     */
    fun setThumbNormalStrength(v: Float) = thumb.run { setNormalStrength(v); updateThumbBackdrop() }

    /**
     * Sets the displacement scale for the thumb glass surface.
     *
     * @param v Distortion strength multiplier.
     */
    fun setThumbDisplacementScale(v: Float) {
        thumbDisplacementScale = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the resting blur radius for the thumb. On press, blur animates toward zero.
     *
     * @param v Blur radius in dp.
     */
    fun setThumbBlurRadius(v: Float) {
        restBlur = v; applyPressState(pressSpring.value)
    }

    /**
     * Sets the chromatic aberration intensity on the thumb.
     *
     * @param v Aberration intensity, typically `0f`–`10f`.
     */
    fun setThumbChromaticAberration(v: Float) {
        pressChromatic = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the corner radius of the thumb capsule.
     *
     * @param v Radius in pixels.
     */
    fun setThumbCornerRadius(v: Float) = thumb.run { setCornerRadius(v); updateThumbBackdrop() }

    /**
     * Sets the brightness multiplier for the thumb glass effect.
     *
     * @param v Brightness factor (default around `1.08f`).
     */
    fun setThumbBrightness(v: Float) {
        thumbBrightness = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Toggles display of surface normals on the thumb (debug visualization).
     *
     * @param on `true` to show normals.
     */
    fun setThumbShowNormals(on: Boolean) = thumb.run { setShowNormals(on); updateThumbBackdrop() }

    /**
     * Sets shadow color and softness for the thumb.
     *
     * @param c Shadow color as an Android ColorInt (ARGB).
     * @param r Shadow softness / radius factor.
     */
    fun setThumbShadow(c: Int, r: Float) {
        thumbShadowColor = c; thumbShadowSoftness = r; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the width of the pressed thumb's refraction band, measured in from the rim.
     *
     * @param v Band width in dp; `<= 0` restores the iOS default.
     */
    fun setThumbHeightBlurFactor(v: Float) {
        thumbHeightBlurFactor = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets how far the pressed thumb pulls the backdrop inward at its rim.
     *
     * @param v Displacement in dp.
     */
    fun setThumbRefractionAmount(v: Float) {
        thumbRefractionAmount = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the simulated glass thickness on the thumb.
     *
     * @param v Thickness in pixels.
     */
    fun setThumbThickness(v: Float) {
        thumbThicknessPx = v; applyPressState(pressSpring.value); updateThumbBackdrop()
    }

    /**
     * Sets the refraction inset on the thumb - distance from the edge where refraction applies.
     *
     * @param v Inset value in pixels.
     */
    fun setThumbRefractionInset(v: Float) = thumb.run { setRefractionInset(v); updateThumbBackdrop() }

    /**
     * Sets the parallax scale for the thumb glass surface.
     *
     * Controls how much the background UV shifts based on the glass surface normal and height.
     * Lower values (0.2–0.5) keep the background locked close to what is directly beneath the
     * thumb; higher values (1+) add a more dramatic depth/lens wander effect.
     *
     * @param v Scale multiplier in `[0, 2]`. Default is `1f`.
     */
    fun setThumbParallaxScale(v: Float) = thumb.run { setParallaxScale(v); updateThumbBackdrop() }

    /**
     * No-op - thumb dimensions are fixed at 40 × 24 dp. Retained for API compatibility with XML.
     *
     * @param dpValue Ignored.
     */
    fun setThumbWidthDp(@Suppress("UNUSED_PARAMETER") dpValue: Float) {}

    /**
     * Sets the glass tint color of the thumb.
     *
     * @param color ARGB color - alpha controls tint strength (0 = clear, 255 = fully tinted).
     */
    fun setThumbColor(color: Int) {
        thumbGlassColor = color
        thumb.run { setGlassColor(color); updateThumbBackdrop() }
    }

    /**
     * Sets the accent color for the track fill.
     *
     * @param color ARGB color int.
     */
    fun setAccentColor(color: Int) {
        accentColor = color
        track.setFillColor(color)
    }
}