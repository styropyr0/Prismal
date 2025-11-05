package com.matrix.prismal

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * **PrismalButton** - A glass-like, interactive UI component that simulates realistic
 * optical refraction and reflection effects using Prismal’s rendering engine.
 *
 * This button provides a dynamic, responsive surface that reacts fluidly to user input
 * through a combination of scaling, normal-map distortion, and refractive animation.
 * The component visually mimics real glass behavior, with subtle depth, blur,
 * and chromatic aberration effects that enhance the realism of the interaction.
 *
 * ### Key Features
 * - Smooth press and release animations with refractive "glass pulse" feedback.
 * - Configurable physical and optical parameters (IOR, blur, chromatic aberration, etc.).
 * - Optional corner rounding for various design styles.
 * - Fully compatible with standard Android click listeners via `setOnClickListener()`.
 * - Integrates seamlessly with Prismal’s real-time shader rendering system.
 *
 * ### Usage Example
 * ```kotlin
 * val prismalButton = PrismalButton(context).apply {
 *     setIOR(1.75f)
 *     setBlurRadius(2.5f)
 *     setCornerRadius(16f)
 *     setOnClickListener { Log.d("PrismalButton", "Button clicked!") }
 * }
 * ```
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val prismalSurface = PrismalFrameLayout(context)
    private var pressScale = 0.92f
    private var animDuration = 200L

    private var clickListener: (() -> Unit)? = null

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private val touchListener = OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatePress(true)
                prismalSurface.setDebug(true)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePress(false)
                prismalSurface.setDebug(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    clickListener?.invoke()
                    performClick()
                }
            }
        }
        true
    }

    init {
        isClickable = true
        isFocusable = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalButton, 0, 0).apply {
            try {
                prismalSurface.setIOR(getFloat(R.styleable.PrismalButton_pbtn_ior, 1.85f))
                prismalSurface.setNormalStrength(getFloat(R.styleable.PrismalButton_pbtn_normalStrength, 12f))
                prismalSurface.setDisplacementScale(getFloat(R.styleable.PrismalButton_pbtn_displacementScale, 10f))
                prismalSurface.setBlurRadius(getFloat(R.styleable.PrismalButton_pbtn_blurRadius, 3f))
                prismalSurface.setChromaticAberration(getFloat(R.styleable.PrismalButton_pbtn_chromaticAberration, 8f))
                prismalSurface.setCornerRadius(getDimension(R.styleable.PrismalButton_pbtn_cornerRadius, 32f))
                prismalSurface.setHighlightWidth(getFloat(R.styleable.PrismalButton_pbtn_highlightWidth, 4f))
                prismalSurface.setBrightness(getFloat(R.styleable.PrismalButton_pbtn_brightness, 1.6f))
                prismalSurface.setShowNormals(getBoolean(R.styleable.PrismalButton_pbtn_showNormals, false))
            } finally {
                recycle()
            }
        }

        addView(
            prismalSurface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        prismalSurface.setOnTouchListener(touchListener)
    }

    private fun animatePress(pressed: Boolean) {
        val scaleStart = if (pressed) 1f else pressScale
        val scaleEnd = if (pressed) pressScale else 1f
        val durationScale = if (pressed) animDuration / 2 else animDuration

        val scaleAnim = ValueAnimator.ofFloat(scaleStart, scaleEnd).apply {
            duration = durationScale
            interpolator = OvershootInterpolator(3f)
            addUpdateListener {
                val s = it.animatedValue as Float
                prismalSurface.scaleX = s
                prismalSurface.scaleY = s
            }
        }

        val pulseAnim = ValueAnimator.ofFloat(
            if (pressed) 1f else 0.5f,
            if (pressed) 1.3f else 1f
        ).apply {
            duration = animDuration
            addUpdateListener {
                val strength = it.animatedValue as Float
                prismalSurface.setNormalStrength(8f * strength)
                prismalSurface.updateBackground()
            }
        }

        AnimatorSet().apply {
            playTogether(scaleAnim, pulseAnim)
            start()
        }
    }

    /**
     * Sets the **Index of Refraction (IOR)** for the glass surface.
     * Higher values create stronger refraction and light-bending effects.
     *
     * @param value The IOR value, typically between `1.0f` and `2.0f`.
     */
    fun setIOR(value: Float) {
        prismalSurface.setIOR(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the strength of the normal-map distortion on the glass surface.
     * This affects the intensity of surface ripples and refraction patterns.
     *
     * @param value Normal strength multiplier.
     */
    fun setNormalStrength(value: Float) {
        prismalSurface.setNormalStrength(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the displacement scale for the glass surface’s distortion.
     * Higher values increase the depth and parallax of the refraction.
     *
     * @param value Displacement intensity in pixels.
     */
    fun setDisplacementScale(value: Float) {
        prismalSurface.setDisplacementScale(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the blur radius applied to the refracted background.
     * Controls how diffused or frosted the glass appearance looks.
     *
     * @param value Blur radius in density-independent pixels (dp).
     */
    fun setBlurRadius(value: Float) {
        prismalSurface.setBlurRadius(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the amount of **chromatic aberration** applied to the refraction.
     * Higher values increase color separation near edges for a prismatic look.
     *
     * @param value Aberration intensity, typically between `0f` and `10f`.
     */
    fun setChromaticAberration(value: Float) {
        prismalSurface.setChromaticAberration(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the corner radius of the glass surface.
     * Controls the curvature of the button’s edges.
     *
     * @param value Corner radius in pixels.
     */
    fun setCornerRadius(value: Float) {
        prismalSurface.setCornerRadius(value)
        prismalSurface.updateBackground()
    }

    /**
     * Adjusts the brightness multiplier of the glass surface.
     * Useful for dark themes or bright background compensation.
     *
     * @param value Brightness factor, where `1.0f` is neutral.
     */
    fun setBrightness(value: Float) {
        prismalSurface.setBrightness(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the highlight width used by the reflective overlay.
     * Wider highlights create a more polished glass appearance.
     *
     * @param value Highlight width in pixels.
     */
    fun setHighlightWidth(value: Float) {
        prismalSurface.setHighlightWidth(value)
        prismalSurface.updateBackground()
    }

    /**
     * Enables or disables the display of surface normals for debugging.
     * When enabled, the shader visualizes surface normals instead of refraction.
     *
     * @param enabled `true` to show normals, `false` to render normally.
     */
    fun setShowNormals(enabled: Boolean) {
        prismalSurface.setShowNormals(enabled)
        prismalSurface.updateBackground()
    }

    /**
     * Assigns a click listener to this button.
     * Behaves identically to a standard Android button click handler.
     *
     * @param l The click listener to be invoked on user press.
     */
    override fun setOnClickListener(l: OnClickListener?) {
        clickListener = { l?.onClick(this) }
    }
}
