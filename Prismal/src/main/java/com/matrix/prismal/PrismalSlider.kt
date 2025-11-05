package com.matrix.prismal

import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.content.res.use
import androidx.core.graphics.toColorInt
import kotlin.math.max

/**
 * ## PrismalSlider
 *
 * A **glass-like slider** component built on top of the `PrismalFrameLayout`.
 * It provides a smooth, refractive thumb that can be dragged horizontally
 * along a background track. Ideal for volume, brightness, or custom value sliders.
 *
 * ### Features
 * - Real-time refractive thumb using Prismal rendering.
 * - Configurable thumb width, corner radius, blur, and optical parameters.
 * - XML attribute support for customization.
 * - Smooth touch tracking with live value updates.
 * - Easy integration with `setOnValueChangedListener()`.
 *
 * ### Example Usage
 * ```xml
 * <com.matrix.prismal.PrismalSlider
 *     android:id="@+id/slider"
 *     android:layout_width="match_parent"
 *     android:layout_height="80dp"
 *     app:thumbWidth="60dp"
 *     app:maxValue="200"
 *     app:thumbCornerRadius="50"
 *     app:thumbIOR="1.35"
 *     app:thumbBlurRadius="5"
 *     app:thumbBrightness="1.3" />
 * ```
 *
 * ```kotlin
 * slider.setOnValueChangedListener { value ->
 *     // React to slider movement
 * }
 * ```
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var maxValue = 100f
    private var currentValue = 0f
    private var dragging = false
    private var lastX = 0f
    private var onValueChanged: ((Float) -> Unit)? = null

    private var thumbWidth = dp(55f)
    private var trackHeight = dp(12f)
    private val bounceScale = 1.1f

    private val track = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor("#00B624".toColorInt())
        }
    }

    private val thumb = PrismalFrameLayout(context).apply {
        setHeightBlurFactor(30f)
    }

    private val sliderTouchListener = OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastX = event.rawX
                thumb.setDebug(true)
                animateThumbShape(pressed = true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    thumb.updateBackground()
                    val dx = event.rawX - lastX
                    lastX = event.rawX

                    val parentW = width
                    val offset = thumbWidth * bounceScale - thumbWidth
                    val maxTravel = parentW - thumb.width - offset
                    val newX = (thumb.translationX + dx).coerceIn(offset, maxTravel)
                    thumb.translationX = newX

                    val progress = (newX / maxTravel) * maxValue
                    if (progress != currentValue) {
                        currentValue = progress
                        onValueChanged?.invoke(progress)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                thumb.setDebug(false)
                animateThumbShape(pressed = false)
            }
        }
        true
    }

    init {
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, trackHeight.toInt()).apply {
            gravity = Gravity.CENTER_VERTICAL
            leftMargin = dp(20f + (thumbWidth * bounceScale - thumbWidth)).toInt()
            rightMargin = dp(20f + (thumbWidth * bounceScale - thumbWidth)).toInt()
        })

        addView(thumb, LayoutParams(thumbWidth.toInt(), LayoutParams.MATCH_PARENT))
        thumb.setOnTouchListener(sliderTouchListener)

        context.obtainStyledAttributes(attrs, R.styleable.PrismalSlider).use { a ->
            maxValue = a.getFloat(R.styleable.PrismalSlider_psl_maxValue, 100f)

            val widthDp = a.getDimension(R.styleable.PrismalSlider_psl_thumbWidth, thumbWidth)
            (track.background as GradientDrawable).setColor(
                a.getColor(R.styleable.PrismalSlider_psl_trackColor, "#00B624".toColorInt())
            )
            thumbWidth = widthDp

            with(thumb) {
                layoutParams.width = thumbWidth.toInt()
                setCornerRadius(a.getDimension(R.styleable.PrismalSlider_psl_thumbCornerRadius, 50f))
                setIOR(a.getFloat(R.styleable.PrismalSlider_psl_thumbIOR, 1.55f))
                setNormalStrength(a.getFloat(R.styleable.PrismalSlider_psl_thumbNormalStrength, 8f))
                setDisplacementScale(a.getFloat(R.styleable.PrismalSlider_psl_thumbDisplacementScale, 10f))
                setBlurRadius(a.getFloat(R.styleable.PrismalSlider_psl_thumbBlurRadius, 1f))
                setChromaticAberration(a.getFloat(R.styleable.PrismalSlider_psl_thumbChromaticAberration, 8f))
                setBrightness(a.getFloat(R.styleable.PrismalSlider_psl_thumbBrightness, 1.19f))
                setThickness(a.getDimension(R.styleable.PrismalSlider_psl_thumbThickness, 15f))
                setHighlightWidth(a.getFloat(R.styleable.PrismalSlider_psl_thumbHighlightWidth, 4f))
                setHeightBlurFactor(a.getFloat(R.styleable.PrismalSlider_psl_thumbHeightBlurFactor, 8f))
                setMinSmoothing(a.getFloat(R.styleable.PrismalSlider_psl_thumbMinSmoothing, 1f))
                setRefractionInset(a.getFloat(R.styleable.PrismalSlider_psl_thumbRefractionInset, 0.1f))
                setEdgeRefractionFalloff(a.getFloat(R.styleable.PrismalSlider_psl_thumbEdgeRefractionFalloff, 0.3f))

                val shadowSoftness = a.getFloat(R.styleable.PrismalSlider_psl_thumbShadowSoftness, 0.7f).coerceIn(0f..1f)
                val shadowAlpha = a.getInt(R.styleable.PrismalSlider_psl_thumbShadowAlpha, 100).coerceIn(0, 255)
                val shadowColor = a.getColor(R.styleable.PrismalSlider_psl_thumbShadowColor, "#45222244".toColorInt())

                setShadowProperties(shadowColor, shadowSoftness * (shadowAlpha / 255f))
            }
        }
        invalidate()
    }

    private fun animateThumbShape(pressed: Boolean) {
        thumb.pivotY = thumb.height / 2f
        thumb.pivotX = thumb.width / 2f

        val startScale = if (pressed) 1f else bounceScale
        val endScale = if (pressed) bounceScale else 1f
        val scaleDuration = if (pressed) 150L else 280L

        val scaleAnim = ValueAnimator.ofFloat(startScale, endScale).apply {
            duration = scaleDuration
            interpolator = OvershootInterpolator(3.2f)
            addUpdateListener {
                val s = it.animatedValue as Float
                thumb.scaleX = s
                thumb.scaleY = 1f / s
                thumb.updateBackground()
            }
        }

        val pulseAnim = ValueAnimator.ofFloat(
            if (pressed) 1f else 1.2f,
            if (pressed) 1.25f else 1f
        ).apply {
            duration = if (pressed) 140L else 120L
            interpolator = OvershootInterpolator(2.8f)
            addUpdateListener {
                val strength = it.animatedValue as Float
                thumb.setNormalStrength(13f * strength)
                thumb.updateBackground()
            }
        }

        AnimatorSet().apply {
            playTogether(scaleAnim, pulseAnim)
            start()
        }
    }

    /**
     * Triggers an update to the background texture by scheduling a capture of the underlying view hierarchy.
     * This is useful for refreshing the glass effect when the content beneath changes (e.g., after scrolling or layout updates).
     */
    fun updateBackground() {
        thumb.updateBackground()
    }

    /**
     * Sets the current value of the slider (0–[maxValue]).
     * Automatically updates the thumb position.
     */
    fun setValue(value: Float) {
        val clamped = value.coerceIn(0f, maxValue)
        currentValue = clamped
        post {
            val maxTravel = width - thumb.width
            val pos = (clamped / maxValue) * maxTravel
            thumb.translationX = pos
        }
    }

    /** Returns the current slider value. */
    fun getValue(): Float = currentValue

    /**
     * Sets the maximum possible slider value.
     * The default is 100.
     */
    fun setMaxValue(value: Float) {
        maxValue = max(1f, value)
    }

    /**
     * Registers a listener that receives the current slider value
     * whenever the thumb position changes.
     */
    fun setOnValueChangedListener(listener: (Float) -> Unit) {
        onValueChanged = listener
    }

    /**
     * Sets the width of the thumb in dp.
     */
    fun setThumbWidthDp(dpValue: Float) {
        thumbWidth = dp(dpValue)
        thumb.layoutParams = thumb.layoutParams.apply {
            width = thumbWidth.toInt()
        }
        requestLayout()
    }

    fun setThumbIOR(value: Float) = thumb.apply { setIOR(value); updateBackground() }
    fun setThumbNormalStrength(value: Float) = thumb.apply { setNormalStrength(value); updateBackground() }
    fun setThumbDisplacementScale(value: Float) = thumb.apply { setDisplacementScale(value); updateBackground() }
    fun setThumbBlurRadius(value: Float) = thumb.apply { setBlurRadius(value); updateBackground() }
    fun setThumbChromaticAberration(value: Float) = thumb.apply { setChromaticAberration(value); updateBackground() }
    fun setThumbCornerRadius(value: Float) = thumb.apply { setCornerRadius(value); updateBackground() }
    fun setThumbBrightness(value: Float) = thumb.apply { setBrightness(value); updateBackground() }
    fun setThumbShowNormals(enabled: Boolean) = thumb.apply { setShowNormals(enabled); updateBackground() }
    fun setThumbShadow(color: Int, radius: Float) = thumb.apply { setShadowProperties(color, radius); updateBackground() }
    fun setThumbHeightBlurFactor(value: Float) = thumb.apply { setHeightBlurFactor(value); updateBackground() }
    fun getThumb(): PrismalFrameLayout = thumb

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
