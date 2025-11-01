package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import kotlin.math.max
import kotlin.math.min

/**
 * A self-contained glass-like slider using Prismal rendering.
 * Includes a background track and a draggable refractive thumb.
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

    private var thumbWidth = dp(60f)
    private var trackHeight = dp(12f)

    private val track = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor("#00B624".toColorInt())
        }
    }

    // Thumb (the glassy refractive element)
    private val thumb = PrismalFrameLayout(context).apply {
        setCornerRadius(50f)
        setIOR(1.35f)
        setNormalStrength(8f)
        setMinSmoothing(10f)
        setDisplacementScale(10f)
        setRefractionInset(100f)
        setHeightBlurFactor(30f)
        setBlurRadius(3f)
        setChromaticAberration(60f)
        setBrightness(1f)
        setShowNormals(false)
        setShadowProperties(Color.argb(80, 0, 0, 0), 0.25f)
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)


    private val sliderTouchListener = OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastX = event.rawX
                thumb.setDebug(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    thumb.updateBackground()
                    val dx = event.rawX - lastX
                    lastX = event.rawX

                    val parentW = width
                    val maxTravel = parentW - thumb.width
                    val newX = (thumb.translationX + dx).coerceIn(0f, maxTravel.toFloat())
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
            }
        }
        true
    }

    init {
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, trackHeight.toInt()).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL

            leftMargin = dp(20f).toInt()
            rightMargin = dp(20f).toInt()
        })

        addView(thumb, LayoutParams(thumbWidth.toInt(), LayoutParams.MATCH_PARENT))

        thumb.setOnTouchListener(sliderTouchListener)
    }

    fun setValue(value: Float) {
        val clamped = value.coerceIn(0f, maxValue)
        currentValue = clamped
        post {
            val maxTravel = width - thumb.width
            val pos = (clamped / maxValue) * maxTravel
            thumb.translationX = pos
        }
    }

    fun getValue(): Float = currentValue

    fun setMaxValue(value: Float) {
        maxValue = max(1f, value)
    }

    fun setOnValueChangedListener(listener: (Float) -> Unit) {
        onValueChanged = listener
    }

    fun setThumbWidthDp(dpValue: Float) {
        thumbWidth = dp(dpValue)
        thumb.layoutParams = thumb.layoutParams.apply {
            width = thumbWidth.toInt()
        }
        requestLayout()
    }
}
