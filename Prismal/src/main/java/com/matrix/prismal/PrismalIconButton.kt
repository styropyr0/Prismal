package com.matrix.prismal

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.toColorInt

/**
 * **PrismalIconButton** - A circular, glass-like button designed for icons, powered by
 * Prismal's real-time rendering system.
 *
 * This component creates a refractive, glossy surface that animates smoothly when pressed,
 * featuring depth and distortion effects that mimic real glass materials.
 * It is ideal for toolbar actions, floating buttons, or compact controls.
 *
 * ### Key Features
 * - Circular glass surface with adaptive sizing.
 * - Smooth press/release scaling animation with refractive pulse.
 * - Configurable optical parameters (IOR, blur, chromatic aberration, etc.).
 * - Supports any drawable or vector icon via `setIcon()`.
 * - Fully functional as a normal Android button with `setOnClickListener()`.
 *
 * ### Usage Example
 * ```kotlin
 * val iconButton = PrismalIconButton(context).apply {
 *     setIcon(R.drawable.ic_heart)
 *     setIOR(1.8f)
 *     setBlurRadius(2.5f)
 *     setOnClickListener { Log.d("PrismalIconButton", "Clicked!") }
 * }
 * ```
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalIconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val prismalSurface = PrismalFrameLayout(context)
    private val iconView = AppCompatImageView(context)

    private var pressScale = 0.88f
    private var animDuration = 180L
    private var clickListener: (() -> Unit)? = null
    private var iconTint: Int
    private var defaultSizePx = 0
    private var lastGlassSizePx = 0

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

        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalIconButton, 0, 0).apply {
            try {
                PrismalLiquidGlass.applyBase(prismalSurface)
                with(prismalSurface) {
                    setLiquidDomeStrength(0.72f)
                    setFresnelReflectStrength(1.3f)
                    setLensRefractionScale(0.55f)
                    setShadowProperties("#22000000".toColorInt(), 0.18f)
                    setIOR(getFloat(R.styleable.PrismalIconButton_pib_ior, 1.55f))
                    setBlurRadius(getFloat(R.styleable.PrismalIconButton_pib_blurRadius, 2f))
                    setNormalStrength(
                        getFloat(
                            R.styleable.PrismalIconButton_pib_normalStrength,
                            1.0f
                        )
                    )
                    setDisplacementScale(
                        getFloat(
                            R.styleable.PrismalIconButton_pib_displacementScale,
                            0.9f
                        )
                    )
                    setChromaticAberration(
                        getFloat(
                            R.styleable.PrismalIconButton_pib_chromaticAberration,
                            0f
                        )
                    )
                    setBrightness(getFloat(R.styleable.PrismalIconButton_pib_brightness, 1.12f))
                    setHighlightWidth(
                        getFloat(
                            R.styleable.PrismalIconButton_pib_highlightWidth,
                            1.2f
                        )
                    )
                    setShowNormals(getBoolean(R.styleable.PrismalIconButton_pib_showNormals, false))
                }

                defaultSizePx = getDimension(R.styleable.PrismalIconButton_pib_buttonSize, dp(56f)).toInt()
                val iconPadding =
                    getDimension(R.styleable.PrismalIconButton_pib_iconPadding, dp(8f)).toInt()
                val iconRes = getResourceId(R.styleable.PrismalIconButton_pib_iconSrc, 0)
                iconTint = getColor(R.styleable.PrismalIconButton_pib_iconTint, Color.BLACK)
                pressScale = getFloat(R.styleable.PrismalIconButton_pib_pressScale, 0.88f)
                animDuration = getInt(R.styleable.PrismalIconButton_pib_animDuration, 180).toLong()

                addView(
                    prismalSurface,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
                )

                prismalSurface.addView(
                    iconView,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                        setMargins(iconPadding, iconPadding, iconPadding, iconPadding)
                    }
                )

                if (iconRes != 0) {
                    with(iconView) {
                        setImageResource(iconRes)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        imageTintList = ColorStateList.valueOf(iconTint)
                    }
                }
            } finally {
                recycle()
            }
        }

        prismalSurface.setOnTouchListener(touchListener)
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val resolvedW = when (wMode) {
            MeasureSpec.EXACTLY -> wSize
            MeasureSpec.AT_MOST -> if (layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                defaultSizePx.coerceAtMost(wSize)
            } else {
                wSize
            }
            else -> defaultSizePx
        }
        val resolvedH = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> if (layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                defaultSizePx.coerceAtMost(hSize)
            } else {
                hSize
            }
            else -> defaultSizePx
        }
        val side = minOf(resolvedW, resolvedH)
        val sideSpec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        super.onMeasure(sideSpec, sideSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? ViewGroup)?.let {
            it.clipChildren = false
            it.clipToPadding = false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = minOf(w, h)
        if (side <= 0) return
        applySizeScaledGlass(side)
        val radius = side / 2f
        prismalSurface.setCornerRadius(radius)
        prismalSurface.updateBackground()
    }

    /**
     * Scales glass thickness, blur band, and refraction inset to the button diameter so small
     * controls do not inherit card-sized defaults from [PrismalLiquidGlass.applyBase].
     */
    private fun applySizeScaledGlass(sidePx: Int) {
        if (sidePx == lastGlassSizePx) return
        lastGlassSizePx = sidePx
        val density = resources.displayMetrics.density
        val sideDp = sidePx / density
        prismalSurface.setThickness((sideDp * 0.095f).coerceIn(3f, 6f) * density)
        prismalSurface.setHeightBlurFactor((sideDp * 0.07f).coerceIn(2.5f, 5f) * density)
        prismalSurface.setRefractionInset((sidePx * 0.35f).coerceIn(4f * density, 20f * density))
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
                iconView.scaleX = s
                iconView.scaleY = s
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
     * Triggers an update to the background texture by scheduling a capture of the underlying view hierarchy.
     * This is useful for refreshing the glass effect when the content beneath changes (e.g., after scrolling or layout updates).
     */
    fun updateBackground() {
        prismalSurface.updateBackground()
    }

    /**
     * Sets the icon drawable resource displayed in the button.
     *
     * @param resId Drawable resource ID.
     */
    fun setIcon(resId: Int) {
        iconView.setImageResource(resId)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /**
     * Sets the **Index of Refraction (IOR)** for the glass surface.
     *
     * @param value IOR value typically between `1.0f` and `2.0f`.
     */
    fun setIOR(value: Float) {
        prismalSurface.setIOR(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the blur radius for the refracted background.
     *
     * @param value Blur radius in dp.
     */
    fun setBlurRadius(value: Float) {
        prismalSurface.setBlurRadius(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the chromatic aberration intensity.
     *
     * @param value Aberration intensity, typically `0f`–`10f`.
     */
    fun setChromaticAberration(value: Float) {
        prismalSurface.setChromaticAberration(value)
        prismalSurface.updateBackground()
    }

    /**
     * Sets the displacement scale for the glass surface.
     *
     * @param value Distortion strength in pixels.
     */
    fun setDisplacementScale(value: Float) {
        prismalSurface.setDisplacementScale(value)
        prismalSurface.updateBackground()
    }

    /**
     * Assigns a click listener for this button.
     *
     * @param l Listener invoked when the user clicks the button.
     */
    override fun setOnClickListener(l: OnClickListener?) {
        clickListener = { l?.onClick(this) }
    }
}