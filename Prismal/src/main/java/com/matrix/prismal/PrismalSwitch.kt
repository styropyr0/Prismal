package com.matrix.prismal

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt

/**
 * A custom, animated, glass-like switch component built with the Prismal rendering engine.
 *
 * **PrismalSwitch** mimics the behavior of a traditional toggle switch, enhanced with
 * realistic refraction, blur, and chromatic aberration effects rendered via [PrismalFrameLayout].
 *
 * ### Key Features
 * - Fully animated ON/OFF transition with bounce and smooth motion.
 * - Thumb component rendered using Prismal glass shaders for a translucent, refractive look.
 * - Dynamic color transitions for the track background.
 * - Supports runtime customization of thumb's optical parameters such as IOR, blur, normal strength, etc.
 * - Provides a listener callback for toggle state changes.
 *
 * ### Usage Example
 * ```kotlin
 * val switch = PrismalSwitch(context)
 * switch.setOnToggleChangedListener { isOn ->
 *     Log.d("Switch", "State: $isOn")
 * }
 * switch.setThumbIOR(1.8f)
 * switch.setThumbBlurRadius(2f)
 * ```
 *
 * ### Custom XML Attributes
 * Supports attributes defined in `res/values/attrs.xml`:
 * - `isOn`: Initial toggle state
 * - `animDuration`: Animation duration in ms
 * - `thumbWidth`, `trackHeight`: Dimensions
 * - `onColor`, `offColor`: Track colors
 * - Prismal parameters (`thumbIOR`, `thumbBlurRadius`, etc.)
 *
 * @constructor Creates a new [PrismalSwitch] instance.
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var isOn = false
    private var animDuration = 250L
    private var onToggleChanged: ((Boolean) -> Unit)? = null

    private val bounceScale = 1.1f
    private var thumbWidth = 0f
    private var trackHeight = 0f
    private var layoutHeight = 0f

    private var baseThumbWidth = 0f
    private var onColor = "#00B624".toColorInt()
    private var offColor = "#555555".toColorInt()

    private val track = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor("#555555".toColorInt())
        }
    }

    private val thumb = PrismalFrameLayout(context)

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private val switchTouchListener = OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animateThumbShape(pressed = true)
                thumb.setDebug(true)
            }

            MotionEvent.ACTION_UP -> {
                toggle()
                animateThumbShape(pressed = false)
                thumb.setDebug(false)
            }
        }
        true
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalSwitch, 0, 0).apply {
            try {
                isOn = getBoolean(R.styleable.PrismalSwitch_psw_isOn, false)
                animDuration = getInt(R.styleable.PrismalSwitch_psw_animDuration, 250).toLong()
                thumbWidth = getDimension(R.styleable.PrismalSwitch_psw_thumbWidth, -3f)
                trackHeight = getDimension(R.styleable.PrismalSwitch_psw_trackHeight, dp(22f))

                onColor = getColor(R.styleable.PrismalSwitch_psw_onColor, "#00B624".toColorInt())
                offColor = getColor(R.styleable.PrismalSwitch_psw_offColor, "#555555".toColorInt())
                (track.background as GradientDrawable).setColor(if (isOn) onColor else offColor)

                with(thumb) {
                    setIOR(getFloat(R.styleable.PrismalSwitch_psw_thumbIOR, 1.85f))
                    setNormalStrength(getFloat(R.styleable.PrismalSwitch_psw_thumbNormalStrength, 8f))
                    setDisplacementScale(getFloat(R.styleable.PrismalSwitch_psw_thumbDisplacementScale, 10f))
                    setBlurRadius(getFloat(R.styleable.PrismalSwitch_psw_thumbBlurRadius, 1f))
                    setChromaticAberration(getFloat(R.styleable.PrismalSwitch_psw_thumbChromaticAberration, 2f))
                    setCornerRadius(getDimension(R.styleable.PrismalSwitch_psw_thumbCornerRadius, 70f))
                    setBrightness(getFloat(R.styleable.PrismalSwitch_psw_thumbBrightness, 1.195f))
                    setThickness(getDimension(R.styleable.PrismalSwitch_psw_thumbThickness, 15f))
                    setHighlightWidth(getFloat(R.styleable.PrismalSwitch_psw_thumbHighlightWidth, 4f))
                    setHeightBlurFactor(getFloat(R.styleable.PrismalSwitch_psw_thumbHeightBlurFactor, 8f))
                    setMinSmoothing(getFloat(R.styleable.PrismalSwitch_psw_thumbMinSmoothing, 1f))
                    setRefractionInset(getFloat(R.styleable.PrismalSwitch_psw_thumbRefractionInset, 0.1f))
                    setEdgeRefractionFalloff(getFloat(R.styleable.PrismalSwitch_psw_thumbEdgeRefractionFalloff, 0.3f))

                    val shadowSoftness = getFloat(R.styleable.PrismalSwitch_psw_thumbShadowSoftness, 0.2f).coerceIn(0f..1f)
                    val shadowAlpha = getInt(R.styleable.PrismalSwitch_psw_thumbShadowAlpha, 70).coerceIn(0, 255)
                    setShadowProperties("#20222244".toColorInt(), shadowSoftness * (shadowAlpha / 255f))
                }
            } finally {
                recycle()
            }
        }

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, layoutHeight.toInt())

        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, trackHeight.toInt()).apply {
            gravity = Gravity.CENTER
            leftMargin = dp(20f).toInt()
            rightMargin = dp(20f).toInt()
        })

        addView(thumb, LayoutParams(1, 1).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        thumb.setOnTouchListener(switchTouchListener)
        post {
            if (thumbWidth == 0f) thumbWidth = dp(60f)
            if (trackHeight == 0f) trackHeight = dp(22f)
            if (layoutHeight == 0f) layoutHeight = height.toFloat()
            baseThumbWidth = thumbWidth / bounceScale

            thumb.layoutParams = (thumb.layoutParams as LayoutParams).apply {
                width = baseThumbWidth.toInt()
                height = layoutHeight.toInt()
            }

            updateThumbPosition(animated = false)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (thumbWidth == -3f) thumbWidth = w * 0.65f

        baseThumbWidth = thumbWidth
        updateThumbPosition(animated = false)
    }


    private fun updateThumbPosition(animated: Boolean) {
        val maxTravel = width - (baseThumbWidth * bounceScale)
        val targetX = if (isOn) maxTravel else (baseThumbWidth * bounceScale - baseThumbWidth)
        val trackColor = if (isOn) onColor else offColor

        if (animated) {
            val startX = thumb.translationX

            val moveAnim = ValueAnimator.ofFloat(startX, targetX).apply {
                duration = animDuration
                interpolator = OvershootInterpolator(2.0f)
                addUpdateListener {
                    thumb.translationX = it.animatedValue as Float
                    thumb.updateBackground()
                }
            }

            val colorAnim = ValueAnimator.ofArgb(
                (track.background as GradientDrawable).color?.defaultColor ?: 0,
                trackColor
            ).apply {
                duration = animDuration
                addUpdateListener {
                    (track.background as GradientDrawable).setColor(it.animatedValue as Int)
                }
            }

            val bounceUp = ValueAnimator.ofFloat(1f, bounceScale).apply {
                duration = animDuration / 2
                interpolator = OvershootInterpolator(2f)
                addUpdateListener {
                    val s = it.animatedValue as Float
                    thumb.scaleX = s
                    thumb.scaleY = 1f / s
                }
            }

            val bounceDown = ValueAnimator.ofFloat(bounceScale, 1f).apply {
                duration = animDuration / 2
                interpolator = OvershootInterpolator(3.8f)
                addUpdateListener {
                    val s = it.animatedValue as Float
                    thumb.scaleX = s
                    thumb.scaleY = 1f / s
                }
            }

            AnimatorSet().apply {
                playTogether(moveAnim, colorAnim)
                playSequentially(bounceUp, bounceDown)
                start()
            }
        } else {
            thumb.translationX = targetX
            (track.background as GradientDrawable).setColor(trackColor)
            thumb.updateBackground()
        }
    }

    private fun animateThumbShape(pressed: Boolean) {
        thumb.pivotX = thumb.width / 2f
        thumb.pivotY = thumb.height / 2f

        val startScale = if (pressed) 1f else bounceScale
        val endScale = if (pressed) bounceScale else 1f
        val scaleDuration = if (pressed) 120L else 160L

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
            duration = if (pressed) 150L else 250L
            interpolator = OvershootInterpolator(3.8f)
            addUpdateListener {
                val strength = it.animatedValue as Float
                thumb.setNormalStrength(10f * strength)
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

    /** Sets the refractive index (IOR) for the thumb's glass effect. */
    fun setThumbIOR(value: Float) {
        thumb.setIOR(value)
        thumb.updateBackground()
    }

    /** Sets the normal map strength controlling refraction intensity. */
    fun setThumbNormalStrength(value: Float) {
        thumb.setNormalStrength(value)
        thumb.updateBackground()
    }

    /** Adjusts how much the thumb's displacement map affects the glass distortion. */
    fun setThumbDisplacementScale(value: Float) {
        thumb.setDisplacementScale(value)
        thumb.updateBackground()
    }

    /** Sets the blur radius applied to the thumb's glass surface. */
    fun setThumbBlurRadius(value: Float) {
        thumb.setBlurRadius(value)
        thumb.updateBackground()
    }

    /** Defines the chromatic aberration intensity for the glass shader. */
    fun setThumbChromaticAberration(value: Float) {
        thumb.setChromaticAberration(value)
        thumb.updateBackground()
    }

    /** Sets the corner radius of the thumb's rounded glass shape. */
    fun setThumbCornerRadius(value: Float) {
        thumb.setCornerRadius(value)
        thumb.updateBackground()
    }

    /** Controls the width of the highlight ring effect on the thumb. */
    fun setThumbHighlightWidth(value: Float) {
        thumb.setHighlightWidth(value)
        thumb.updateBackground()
    }

    /** Adjusts overall brightness of the thumb's glass reflection. */
    fun setThumbBrightness(value: Float) {
        thumb.setBrightness(value)
        thumb.updateBackground()
    }

    /** Enables or disables normal map visualization mode for debugging. */
    fun setThumbShowNormals(enabled: Boolean) {
        thumb.setShowNormals(enabled)
        thumb.updateBackground()
    }

    /**
     * Sets a soft shadow behind the thumb for depth appearance.
     *
     * @param color Shadow color (including alpha)
     * @param radius Blur radius for the shadow
     */
    fun setThumbShadow(color: Int, radius: Float) {
        thumb.setShadowProperties(color, radius)
        thumb.updateBackground()
    }

    /** Modifies the height-dependent blur intensity of the thumb. */
    fun setThumbHeightBlurFactor(value: Float) {
        thumb.setHeightBlurFactor(value)
        thumb.updateBackground()
    }

    /**
     * Conveniently sets both IOR and brightness at once.
     *
     * @param ior Index of refraction
     * @param brightness Light intensity applied to the thumb's reflection
     */
    fun setThumbIORAndBrightness(ior: Float, brightness: Float) {
        thumb.setIOR(ior)
        thumb.setBrightness(brightness)
        thumb.updateBackground()
    }

    /**
     * Programmatically toggles the switch state.
     *
     * @param on Desired state (`true` = ON, `false` = OFF)
     * @param animated Whether to animate the transition
     */
    fun setOn(on: Boolean, animated: Boolean = false) {
        if (isOn == on) return
        isOn = on
        updateThumbPosition(animated)
    }

    private fun toggle() {
        isOn = !isOn
        updateThumbPosition(animated = true)
        onToggleChanged?.invoke(isOn)
    }

    /** Returns the current toggle state of the switch. */
    fun isOn(): Boolean = isOn

    /**
     * Registers a listener to be notified when the switch state changes.
     *
     * @param listener Callback invoked with the new state whenever toggled
     */
    fun setOnToggleChangedListener(listener: (Boolean) -> Unit) {
        onToggleChanged = listener
    }

    /**
     * Sets the width of the thumb in dp units.
     *
     * @param dpValue Desired width in density-independent pixels
     */
    fun setThumbWidthDp(dpValue: Float) {
        thumbWidth = dp(dpValue)
        thumb.layoutParams = thumb.layoutParams.apply {
            width = thumbWidth.toInt()
        }
        requestLayout()
    }
}