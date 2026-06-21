package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.toColorInt
import com.matrix.prismal.utils.SpringAnimator

/**
 * A circular liquid-glass button for icons.
 *
 * ## Appearance
 * Applies [PrismalLiquidGlass.applyBase] then scales thickness, blur band, and refraction inset
 * proportionally to the actual pixel diameter on every [onSizeChanged], so the glass proportions
 * are correct at any button size without manual tuning.
 *
 * ## Press animation
 * Two [SpringAnimator] instances replace the previous `ValueAnimator`:
 * - **scaleSpring** (`ζ = 0.7`, `k = 500`) — slightly underdamped so the surface briefly overshoots
 *   1.0 on release, giving the characteristic iOS spring-back click feel.
 * - **pressSpring** (`ζ = 1.0`, `k = 1200`) — critically damped, tracks the finger instantly with
 *   no oscillation. Drives blur (rest → 0), chromatic aberration (0 → 3.5 px), and lens
 *   distortion (0.55 → 1.3) so the glass "activates" on press.
 *
 * ## XML attributes (`pib_` prefix)
 * `pib_iconSrc`, `pib_iconPadding`, `pib_iconTint`, `pib_buttonSize`, `pib_pressScale`,
 * `pib_ior`, `pib_blurRadius`, `pib_normalStrength`, `pib_displacementScale`,
 * `pib_chromaticAberration`, `pib_brightness`, `pib_highlightWidth`, `pib_showNormals`
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
    private var pressScale = 0.82f
    private var restBlur = 2f
    private var restLensScale = 0.85f
    private var clickListener: (() -> Unit)? = null
    private var defaultSizePx = 0
    private var lastGlassSizePx = 0
    private val scaleSpring = SpringAnimator(0.4f, 500f)
    private val pressSpring = SpringAnimator(1.0f, 1200f)

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    private fun applyPressState(t: Float) {
        prismalSurface.setBlurRadius(lerp(restBlur, 0f, t))
        prismalSurface.setChromaticAberration(lerp(0f, 3.5f, t))
        prismalSurface.setLensRefractionScale(lerp(restLensScale, restLensScale + 0.65f, t))
    }

    private fun applyScale(t: Float) {
        val s = (1f + (pressScale - 1f) * t).coerceIn(0.1f, 2f)
        prismalSurface.scaleX = s
        prismalSurface.scaleY = s
    }

    private val touchListener = OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressSpring.animateTo(1f)
                scaleSpring.animateTo(1f)
                prismalSurface.showGlow(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressSpring.animateTo(0f)
                scaleSpring.animateTo(0f)
                prismalSurface.hideGlow()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    clickListener?.invoke()
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        true
    }

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalIconButton, 0, 0).apply {
            try {
                PrismalLiquidGlass.applyBase(prismalSurface)
                with(prismalSurface) {
                    setLiquidDomeStrength(getFloat(R.styleable.PrismalIconButton_pib_liquidDomeStrength, 0.92f))
                    setFresnelReflectStrength(getFloat(R.styleable.PrismalIconButton_pib_fresnelReflectStrength, 1.8f))
                    setShadowProperties(
                        getColor(R.styleable.PrismalIconButton_pib_shadowColor, "#22000000".toColorInt()),
                        getFloat(R.styleable.PrismalIconButton_pib_shadowSoftness, 0.18f)
                    )
                    setIOR(getFloat(R.styleable.PrismalIconButton_pib_ior, 1.55f))
                    setNormalStrength(getFloat(R.styleable.PrismalIconButton_pib_normalStrength, 1.6f))
                    setDisplacementScale(getFloat(R.styleable.PrismalIconButton_pib_displacementScale, 1.15f))
                    setChromaticAberration(getFloat(R.styleable.PrismalIconButton_pib_chromaticAberration, 0f))
                    setBrightness(getFloat(R.styleable.PrismalIconButton_pib_brightness, 1.12f))
                    setHighlightWidth(getFloat(R.styleable.PrismalIconButton_pib_highlightWidth, 1.2f))
                    setRimStrength(1.4f)
                    setShowNormals(getBoolean(R.styleable.PrismalIconButton_pib_showNormals, false))
                }

                restBlur = getFloat(R.styleable.PrismalIconButton_pib_blurRadius, 2f)
                restLensScale = getFloat(R.styleable.PrismalIconButton_pib_lensRefractionScale, 0.85f)
                prismalSurface.setBlurRadius(restBlur)
                prismalSurface.setLensRefractionScale(restLensScale)

                defaultSizePx = getDimension(R.styleable.PrismalIconButton_pib_buttonSize, dp(56f)).toInt()
                val iconPadding = getDimension(R.styleable.PrismalIconButton_pib_iconPadding, dp(8f)).toInt()
                val iconRes = getResourceId(R.styleable.PrismalIconButton_pib_iconSrc, 0)
                val iconTint = getColor(R.styleable.PrismalIconButton_pib_iconTint, Color.BLACK)
                pressScale = getFloat(R.styleable.PrismalIconButton_pib_pressScale, 0.82f)

                addView(
                    prismalSurface,
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                )
                prismalSurface.addView(
                    iconView,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                        setMargins(iconPadding, iconPadding, iconPadding, iconPadding)
                    }
                )

                if (iconRes != 0) {
                    iconView.setImageResource(iconRes)
                    iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                    iconView.imageTintList = ColorStateList.valueOf(iconTint)
                }
            } finally {
                recycle()
            }
        }

        pressSpring.onUpdate = { applyPressState(it) }
        scaleSpring.onUpdate = { applyScale(it) }
        pressSpring.snapTo(0f)
        scaleSpring.snapTo(0f)
        applyPressState(0f)
        applyScale(0f)

        prismalSurface.setOnTouchListener(touchListener)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val resolvedW = when (wMode) {
            MeasureSpec.EXACTLY -> wSize
            MeasureSpec.AT_MOST -> if (layoutParams.width == LayoutParams.WRAP_CONTENT) defaultSizePx.coerceAtMost(
                wSize
            ) else wSize

            else -> defaultSizePx
        }
        val resolvedH = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> if (layoutParams.height == LayoutParams.WRAP_CONTENT) defaultSizePx.coerceAtMost(
                hSize
            ) else hSize

            else -> defaultSizePx
        }
        val side = minOf(resolvedW, resolvedH)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        )
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
        prismalSurface.setCornerRadius(side / 2f)
        prismalSurface.updateBackground()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pressSpring.cancel()
        scaleSpring.cancel()
    }

    private fun applySizeScaledGlass(sidePx: Int) {
        if (sidePx == lastGlassSizePx) return
        lastGlassSizePx = sidePx
        val density = resources.displayMetrics.density
        val sideDp = sidePx / density
        prismalSurface.setThickness((sideDp * 0.20f).coerceIn(6f, 18f) * density)
        prismalSurface.setHeightBlurFactor((sideDp * 0.32f).coerceIn(12f, 30f) * density)
        prismalSurface.setRefractionInset((sidePx * 0.04f).coerceIn(2f * density, 5f * density))
    }

    /** Refreshes the glass background texture. Call after the content behind this button changes. */
    fun updateBackground() = prismalSurface.updateBackground()

    /** Sets the icon drawable displayed inside the button. */
    fun setIcon(resId: Int) {
        iconView.setImageResource(resId)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /** Index of Refraction. Typical range: 1.3 – 2.0. Default: 1.55. */
    fun setIOR(value: Float) {
        prismalSurface.setIOR(value); prismalSurface.updateBackground()
    }

    /**
     * Resting blur radius in dp. Reduces to 0 on press to sharpen the glass as the button
     * activates, then restores on release.
     */
    fun setBlurRadius(value: Float) {
        restBlur = value
        applyPressState(pressSpring.value)
    }

    /** RGB channel split in pixels. Applied only during the press animation (0 at rest). */
    fun setChromaticAberration(value: Float) {
        prismalSurface.setChromaticAberration(value)
        prismalSurface.updateBackground()
    }

    /** Lens distortion scale factor. */
    fun setDisplacementScale(value: Float) {
        prismalSurface.setDisplacementScale(value)
        prismalSurface.updateBackground()
    }

    /** Exposes the inner glass surface for applying bulk params (e.g. playground presets). */
    val glassSurface: PrismalFrameLayout get() = prismalSurface

    override fun setOnClickListener(l: OnClickListener?) {
        clickListener = { l?.onClick(this) }
        prismalSurface.setGlowEnabled(l != null)
    }
}