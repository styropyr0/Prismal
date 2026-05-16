package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.matrix.prismal.utils.SpringAnimator
import kotlin.math.abs
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
    private var restBlur = 3f
    private var accentColor = "#0088FF".toColorInt()
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
            val w = width.toFloat();
            val h = height.toFloat();
            val r = h / 2f
            c.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
            val fw = w * progress
            if (fw > 0f) c.drawRoundRect(0f, 0f, fw.coerceAtLeast(h), h, r, r, fillPaint)
        }
    }

    private val thumb = PrismalFrameLayout(context)
    private val overlay = View(context).also { it.setBackgroundColor(Color.WHITE) }
    private val pressSpring = SpringAnimator(1.0f, 1000f)
    private val scaleXSpring = SpringAnimator(0.6f, 250f)
    private val scaleYSpring = SpringAnimator(0.7f, 250f)
    private var dragging = false
    private var lastRawX = 0f
    private var velTracker: VelocityTracker? = null
    private var normVel = 0f
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun maxTravel() = (width - thumbW).coerceAtLeast(0f)

    private fun applyPressState(t: Float) {
        val density = resources.displayMetrics.density
        thumb.setBlurRadius(lerp(restBlur, 0f, t))
        thumb.setChromaticAberration(lerp(0f, 6f, t))
        thumb.setLensRefractionScale(lerp(0.5f, 1.3f, t))
        thumb.setHeightBlurFactor(lerp(6f, 20f, t) * density)
        overlay.alpha = lerp(1f, 0f, t)
        thumb.updateBackground()
    }

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
    }

    private fun updateProgress(rawX: Float) {
        val travel = maxTravel()
        val newX = rawX.coerceIn(0f, travel)
        thumb.translationX = newX
        val p = if (travel > 0f) newX / travel else 0f
        track.progress = p
        currentValue = p * maxValue
        onValueChanged?.invoke(currentValue)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val touchListener = OnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velTracker?.recycle()
                velTracker = VelocityTracker.obtain().also { it.addMovement(e) }
                dragging = true
                lastRawX = e.rawX
                scaleXSpring.animateTo(1.5f)
                scaleYSpring.animateTo(1.5f)
                pressSpring.animateTo(1f)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return@OnTouchListener true
                velTracker!!.addMovement(e)
                velTracker!!.computeCurrentVelocity(1000)
                val travel = maxTravel().coerceAtLeast(1f)
                normVel = normVel * 0.6f + (velTracker!!.xVelocity / travel) * 0.4f
                val dx = e.rawX - lastRawX
                lastRawX = e.rawX
                updateProgress(thumb.translationX + dx)
                thumb.updateBackground()
                applySquish()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
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
        })

        addView(thumb, LayoutParams(thumbW.toInt(), thumbH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        })
        thumb.addView(
            overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
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
        }

        post { setupThumb(); applyPressState(0f) }
    }

    private fun setupThumb() {
        val density = resources.displayMetrics.density
        PrismalLiquidGlass.applyBase(thumb)
        thumb.setCornerRadius(thumbR)
        thumb.setIOR(1.55f)
        thumb.setBlurRadius(restBlur)
        thumb.setBrightness(1.08f)
        thumb.setLiquidDomeStrength(0.82f)
        thumb.setFresnelReflectStrength(1.5f)
        thumb.setSpecular(1.72f, 96f)
        thumb.setRimStrength(1.8f)
        thumb.setCausticIntensity(0.24f)
        thumb.setNormalStrength(1.15f)
        thumb.setDisplacementScale(1.15f)
        thumb.setThickness(dp(4f))
        thumb.setMinSmoothing(1.8f)
        thumb.setHeightBlurFactor(3f * density)
        thumb.setLightDirection(-0.45f, -0.75f)
        thumb.setShadowProperties(Color.argb(72, 0, 0, 18), 0.2f)
        thumb.setGlassColor(Color.argb(34, 255, 255, 255))
        thumb.setLensRefractionScale(0.5f)
        thumb.updateBackground()
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
        val p = value.coerceIn(0f, maxValue) / maxValue
        currentValue = p * maxValue
        post {
            thumb.translationX = p * maxTravel()
            track.progress = p
            thumb.updateBackground()
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
    fun updateBackground() = thumb.updateBackground()

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
    fun setThumbIOR(v: Float) = thumb.run { setIOR(v); updateBackground() }

    /**
     * Sets the normal-mapping strength on the thumb glass surface.
     *
     * @param v Strength multiplier.
     */
    fun setThumbNormalStrength(v: Float) = thumb.run { setNormalStrength(v); updateBackground() }

    /**
     * Sets the displacement scale for the thumb glass surface.
     *
     * @param v Distortion strength multiplier.
     */
    fun setThumbDisplacementScale(v: Float) =
        thumb.run { setDisplacementScale(v); updateBackground() }

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
    fun setThumbChromaticAberration(v: Float) =
        thumb.run { setChromaticAberration(v); updateBackground() }

    /**
     * Sets the corner radius of the thumb capsule.
     *
     * @param v Radius in pixels.
     */
    fun setThumbCornerRadius(v: Float) = thumb.run { setCornerRadius(v); updateBackground() }

    /**
     * Sets the brightness multiplier for the thumb glass effect.
     *
     * @param v Brightness factor (default around `1.08f`).
     */
    fun setThumbBrightness(v: Float) = thumb.run { setBrightness(v); updateBackground() }

    /**
     * Toggles display of surface normals on the thumb (debug visualization).
     *
     * @param on `true` to show normals.
     */
    fun setThumbShowNormals(on: Boolean) = thumb.run { setShowNormals(on); updateBackground() }

    /**
     * Sets shadow color and softness for the thumb.
     *
     * @param c Shadow color as an Android ColorInt (ARGB).
     * @param r Shadow softness / radius factor.
     */
    fun setThumbShadow(c: Int, r: Float) =
        thumb.run { setShadowProperties(c, r); updateBackground() }

    /**
     * Sets the height-to-blur scaling factor on the thumb.
     *
     * @param v Blur factor applied based on simulated glass height.
     */
    fun setThumbHeightBlurFactor(v: Float) =
        thumb.run { setHeightBlurFactor(v); updateBackground() }

    /**
     * Sets the simulated glass thickness on the thumb.
     *
     * @param v Thickness in pixels.
     */
    fun setThumbThickness(v: Float) = thumb.run { setThickness(v); updateBackground() }

    /**
     * Sets the refraction inset on the thumb - distance from the edge where refraction applies.
     *
     * @param v Inset value in pixels.
     */
    fun setThumbRefractionInset(v: Float) = thumb.run { setRefractionInset(v); updateBackground() }

    /**
     * No-op - thumb dimensions are fixed at 40 × 24 dp. Retained for API compatibility with XML.
     *
     * @param dpValue Ignored.
     */
    fun setThumbWidthDp(@Suppress("UNUSED_PARAMETER") dpValue: Float) {}
}