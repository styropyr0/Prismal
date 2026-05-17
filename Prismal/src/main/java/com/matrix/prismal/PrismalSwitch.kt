package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import com.matrix.prismal.utils.SpringAnimator
import kotlin.math.abs
import androidx.core.graphics.toColorInt

/**
 * **PrismalSwitch** - An iOS-style liquid-glass toggle with tap and drag support.
 *
 * The track is a 64 × 28 dp capsule (scaled proportionally via `psw_trackHeight`) that
 * crossfades from grey (off) to green (on). The thumb is a 40 × 24 dp frosted capsule
 * that reveals live liquid-glass on press. Spring physics animate position, press scale,
 * and velocity-based squish deformation.
 *
 * ### Key Features
 * - Tap-to-toggle and drag-to-scrub interaction.
 * - Track colour crossfade between off and on states.
 * - Frosted thumb at rest; live glass refraction on press.
 * - Configurable track height and thumb optical parameters.
 *
 * ### Usage Example
 * ```kotlin
 * val toggle = PrismalSwitch(context).apply {
 *     setOn(true, animate = false)
 *     setOnToggleChangedListener { on -> Log.d("Switch", "on=$on") }
 * }
 * ```
 *
 * XML attributes: `psw_isOn`, `psw_trackHeight` (see [R.styleable.PrismalSwitch]).
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var trackH = dp(28f)
    private val trackW get() = (trackH * 64f / 28f).toInt()
    private val thumbW get() = (trackH * 40f / 28f)
    private val thumbH get() = (trackH * 24f / 28f)
    private val thumbR get() = thumbH / 2f
    private val padPx get() = (trackH * 2f / 28f)
    private val travelPx get() = (trackW - thumbW - 2f * padPx).coerceAtLeast(0f)
    private var isOn = false
    private var fraction = 0f
    private var onToggleChanged: ((Boolean) -> Unit)? = null
    private var onColor = "#34C759".toColorInt()
    private var offColor = Color.argb(140, 0x78, 0x78, 0x78)

    private var restBlur = 3f
    private var pressChromatic = 6f
    private var thumbIOR = 1.3f
    private var thumbBrightness = 1.12f
    private var thumbNormalStrength = 1f
    private var thumbDisplacementScale = 2.8f
    private var thumbShadowColor = Color.argb(65, 0, 0, 20)
    private var thumbShadowSoftness = 0.25f
    private var thumbThicknessPx = -1f
    private var thumbHighlightWidth = -1f
    private var thumbHeightBlurFactor = -1f
    private var thumbMinSmoothing = 10f
    private var thumbCornerRadiusPx = -1f
    private var thumbRefractionInset = -1f
    private var thumbEdgeFalloff = -1f
    private var thumbShowNormals = false
    private var thumbGlassColor = Color.argb(28, 255, 255, 255)
    private val trackView = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var frac = 0f
            set(v) {
                field = v; invalidate()
            }

        override fun onDraw(c: Canvas) {
            val r = height / 2f
            paint.color = lerpColor(offColor, onColor, frac)
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        }
    }

    private val thumb = PrismalFrameLayout(context)
    private val overlay = View(context).also { it.setBackgroundColor(Color.WHITE) }
    private val posSpring = SpringAnimator(1.0f, 1000f)
    private val pressSpring = SpringAnimator(1.0f, 1000f)
    private val scaleXSpring = SpringAnimator(0.6f, 250f)
    private val scaleYSpring = SpringAnimator(0.7f, 250f)
    private var dragging = false
    private var didDrag = false
    private var lastRawX = 0f
    private var dragStartX = 0f
    private var normVel = 0f
    private var velTracker: VelocityTracker? = null
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val s = t.coerceIn(0f, 1f)
        return Color.argb(
            lerp(Color.alpha(c1).toFloat(), Color.alpha(c2).toFloat(), s).toInt(),
            lerp(Color.red(c1).toFloat(), Color.red(c2).toFloat(), s).toInt(),
            lerp(Color.green(c1).toFloat(), Color.green(c2).toFloat(), s).toInt(),
            lerp(Color.blue(c1).toFloat(), Color.blue(c2).toFloat(), s).toInt()
        )
    }

    private fun applyPressState(t: Float) {
        val density = resources.displayMetrics.density
        val restHBF = if (thumbHeightBlurFactor > 0f) thumbHeightBlurFactor * density else 6f * density
        thumb.setBlurRadius(lerp(restBlur, 0f, t))
        thumb.setChromaticAberration(lerp(0f, pressChromatic, t))
        thumb.setLensRefractionScale(lerp(0.5f, 1.2f, t))
        thumb.setHeightBlurFactor(lerp(restHBF, restHBF * 3f, t))
        overlay.alpha = lerp(1f, 0f, t)
    }

    private fun applySquish() {
        val pressScale = scaleXSpring.value
        thumb.pivotX = thumbW / 2f
        thumb.pivotY = thumbH / 2f
        thumb.scaleX = pressScale
        thumb.scaleY = pressScale
        if (pressScale > 1.05f || dragging) {
            thumb.updateBackground()
        }
    }

    private fun applyFraction(f: Float) {
        fraction = f
        trackView.frac = f
        thumb.translationX = padPx + f * travelPx
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velTracker?.recycle()
                velTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                dragging = true
                didDrag = false
                lastRawX = event.rawX
                dragStartX = event.rawX
                scaleXSpring.animateTo(1.5f)
                scaleYSpring.animateTo(1.5f)
                pressSpring.animateTo(1f)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velTracker!!.addMovement(event)
                if (!didDrag && abs(event.rawX - dragStartX) > touchSlop) didDrag = true
                if (didDrag) {
                    velTracker!!.computeCurrentVelocity(1000)
                    val travel = travelPx.coerceAtLeast(1f)
                    normVel = normVel * 0.6f + (velTracker!!.xVelocity / travel) * 0.4f

                    val dx = event.rawX - lastRawX
                    lastRawX = event.rawX
                    val newFrac = (fraction + dx / travel).coerceIn(0f, 1f)
                    applyFraction(newFrac)
                    applySquish()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                normVel = 0f
                velTracker?.recycle(); velTracker = null
                isOn = if (didDrag) fraction >= 0.5f else !isOn
                posSpring.animateTo(if (isOn) 1f else 0f)
                onToggleChanged?.invoke(isOn)

                scaleXSpring.animateTo(1f)
                scaleYSpring.animateTo(1f)
                pressSpring.animateTo(0f)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalSwitch, 0, 0).apply {
            try {
                isOn = getBoolean(R.styleable.PrismalSwitch_psw_isOn, false)
                trackH = getDimension(R.styleable.PrismalSwitch_psw_trackHeight, dp(28f))
                onColor = getColor(R.styleable.PrismalSwitch_psw_onColor, onColor)
                offColor = getColor(R.styleable.PrismalSwitch_psw_offColor, offColor)
                restBlur = getFloat(R.styleable.PrismalSwitch_psw_thumbBlurRadius, restBlur)
                pressChromatic = getFloat(R.styleable.PrismalSwitch_psw_thumbChromaticAberration, pressChromatic)
                thumbIOR = getFloat(R.styleable.PrismalSwitch_psw_thumbIOR, thumbIOR)
                thumbBrightness = getFloat(R.styleable.PrismalSwitch_psw_thumbBrightness, thumbBrightness)
                thumbNormalStrength = getFloat(R.styleable.PrismalSwitch_psw_thumbNormalStrength, thumbNormalStrength)
                thumbDisplacementScale = getFloat(R.styleable.PrismalSwitch_psw_thumbDisplacementScale, thumbDisplacementScale)
                thumbShadowColor = getColor(R.styleable.PrismalSwitch_psw_thumbShadowColor, thumbShadowColor)
                thumbShadowSoftness = getFloat(R.styleable.PrismalSwitch_psw_thumbShadowSoftness, thumbShadowSoftness)
                thumbThicknessPx = getDimension(R.styleable.PrismalSwitch_psw_thumbThickness, -1f)
                thumbHighlightWidth = getFloat(R.styleable.PrismalSwitch_psw_thumbHighlightWidth, -1f)
                thumbHeightBlurFactor = getFloat(R.styleable.PrismalSwitch_psw_thumbHeightBlurFactor, -1f)
                thumbMinSmoothing = getFloat(R.styleable.PrismalSwitch_psw_thumbMinSmoothing, thumbMinSmoothing)
                thumbCornerRadiusPx = getDimension(R.styleable.PrismalSwitch_psw_thumbCornerRadius, -1f)
                thumbRefractionInset = getFloat(R.styleable.PrismalSwitch_psw_thumbRefractionInset, -1f)
                thumbEdgeFalloff = getFloat(R.styleable.PrismalSwitch_psw_thumbEdgeRefractionFalloff, -1f)
                thumbShowNormals = getBoolean(R.styleable.PrismalSwitch_psw_thumbShowNormals, false)
                thumbGlassColor = getColor(R.styleable.PrismalSwitch_psw_thumbColor, thumbGlassColor)
            } finally {
                recycle()
            }
        }

        addView(trackView, LayoutParams(trackW, trackH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        addView(thumb, LayoutParams(thumbW.toInt(), thumbH.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        thumb.addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        posSpring.onUpdate = { f ->
            applyFraction(f)
            applySquish()
        }
        pressSpring.onUpdate = { applyPressState(it); applySquish() }
        scaleXSpring.onUpdate = { applySquish() }
        scaleYSpring.onUpdate = { applySquish() }

        fraction = if (isOn) 1f else 0f
        posSpring.snapTo(fraction)
        pressSpring.snapTo(0f)
        scaleXSpring.snapTo(1f)
        scaleYSpring.snapTo(1f)

        post { setupThumb() }
    }

    private fun setupThumb() {
        if (thumbW <= 0f || thumbH <= 0f) return
        val density = resources.displayMetrics.density
        (thumb.layoutParams as? LayoutParams)?.apply {
            width = thumbW.toInt()
            height = thumbH.toInt()
            gravity = Gravity.CENTER_VERTICAL
        }
        thumb.requestLayout()
        PrismalLiquidGlass.applyBase(thumb)

        thumb.setCornerRadius(if (thumbCornerRadiusPx > 0f) thumbCornerRadiusPx else thumbR)
        thumb.setIOR(thumbIOR)
        thumb.setThickness(if (thumbThicknessPx > 0f) thumbThicknessPx else dp(1f))
        thumb.setBrightness(thumbBrightness)
        thumb.setGlassColor(thumbGlassColor)
        thumb.setLensRefractionScale(50f)
        thumb.setDisplacementScale(thumbDisplacementScale)
        thumb.setNormalStrength(thumbNormalStrength)
        thumb.setLiquidDomeStrength(5.15f)
        thumb.setFresnelReflectStrength(10f)
        thumb.setRimStrength(0f)
        thumb.setSpecular(.5f, 18f)
        thumb.setCausticIntensity(0f)
        thumb.setMinSmoothing(thumbMinSmoothing)
        thumb.setLightDirection(42f, 78f)
        thumb.setShadowProperties(thumbShadowColor, thumbShadowSoftness)
        if (thumbHighlightWidth > 0f) thumb.setHighlightWidth(thumbHighlightWidth)
        if (thumbRefractionInset > 0f) thumb.setRefractionInset(thumbRefractionInset)
        if (thumbEdgeFalloff > 0f) thumb.setEdgeRefractionFalloff(thumbEdgeFalloff)
        thumb.setShowNormals(thumbShowNormals)

        applyFraction(fraction)
        applyPressState(0f)
        thumb.updateBackground()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w > 0 && thumb.width > 0) setupThumb()
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
        posSpring.cancel(); pressSpring.cancel()
        scaleXSpring.cancel(); scaleYSpring.cancel()
        velTracker?.recycle(); velTracker = null
    }

    /**
     * Sets the switch state programmatically.
     *
     * @param on `true` for on, `false` for off.
     * @param animate When `true`, animates the thumb to the new position with spring physics.
     */
    fun setOn(on: Boolean, animate: Boolean = false) {
        if (isOn == on) return
        isOn = on
        val target = if (on) 1f else 0f
        if (animate) posSpring.animateTo(target) else {
            posSpring.snapTo(target); applyFraction(target)
        }
    }

    /** @return `true` if the switch is in the on position. */
    fun isOn(): Boolean = isOn

    /**
     * Flips the switch state.
     *
     * @param animate When `true`, animates the thumb transition.
     */
    fun toggle(animate: Boolean = true) = setOn(!isOn, animate)

    /**
     * Registers a listener invoked when the user toggles the switch (tap or drag release).
     *
     * @param l Callback receiving the new on/off state.
     */
    fun setOnToggleChangedListener(l: (Boolean) -> Unit) {
        onToggleChanged = l
    }

    /**
     * Triggers an update to the thumb's background texture.
     * Call when content beneath the switch changes (e.g. after scrolling).
     */
    fun updateBackground() = thumb.updateBackground()

    /**
     * Sets the **Index of Refraction (IOR)** for the thumb glass surface.
     *
     * @param v IOR value, typically between `1.0f` and `2.0f`.
     */
    fun setThumbIOR(v: Float) = thumb.run { setIOR(v); updateBackground() }

    /**
     * Sets the blur radius for the thumb glass surface.
     *
     * @param v Blur radius in dp.
     */
    fun setThumbBlurRadius(v: Float) = thumb.run { setBlurRadius(v); updateBackground() }

    /**
     * Sets the brightness multiplier for the thumb glass effect.
     *
     * @param v Brightness factor (default around `1.08f`).
     */
    fun setThumbBrightness(v: Float) = thumb.run { setBrightness(v); updateBackground() }

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
     * Sets the falloff rate for edge refraction on the thumb.
     *
     * @param v Falloff sharpness (higher = sharper transition).
     */
    fun setThumbEdgeRefractionFalloff(v: Float) =
        thumb.run { setEdgeRefractionFalloff(v); updateBackground() }

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
     * Sets the highlight edge width on the thumb glass surface.
     *
     * @param v Highlight width in pixels.
     */
    fun setThumbHighlightWidth(v: Float) = thumb.run { setHighlightWidth(v); updateBackground() }

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
     * No-op - thumb dimensions scale proportionally with track height. Retained for API compatibility.
     *
     * @param dpV Ignored.
     */
    fun setThumbWidthDp(@Suppress("UNUSED_PARAMETER") dpV: Float) {}

    /**
     * Sets IOR and brightness on the thumb in a single call.
     *
     * @param ior Index of refraction.
     * @param b Brightness multiplier.
     */
    fun setThumbIORAndBrightness(ior: Float, b: Float) =
        thumb.run { setIOR(ior); setBrightness(b); updateBackground() }

    /**
     * Sets the glass tint color of the thumb.
     *
     * @param color ARGB color — alpha controls tint strength (0 = clear, 255 = fully tinted).
     */
    fun setThumbColor(color: Int) {
        thumbGlassColor = color
        thumb.run { setGlassColor(color); updateBackground() }
    }
}