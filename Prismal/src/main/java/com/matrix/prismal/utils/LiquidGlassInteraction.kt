package com.matrix.prismal.utils

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Liquid-glass press physics: expand on press, rubber-band drag translation,
 * directional squish, and center backdrop pinch and edge magnification
 */
internal object LiquidGlassInteraction {
    const val DEFAULT_PRESS_EXPAND_DP = 4f

    /** Center backdrop scale on full press - content under the dome shrinks to 75 %. */
    const val DEFAULT_BACKDROP_PINCH = 0.75f

    /** Softer pinch for small switch/slider thumbs. */
    const val THUMB_BACKDROP_PINCH = 0.88f

    /** Subtle touch glow on small thumbs. */
    const val THUMB_GLOW_STRENGTH = 0.32f

    private const val DRAG_TANH_DERIVATIVE = 0.05f

    data class ViewTransform(
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
    )

    fun computeViewTransform(
        width: Int,
        height: Int,
        density: Float,
        pressProgress: Float,
        dragOffsetX: Float,
        dragOffsetY: Float,
        expandDp: Float = DEFAULT_PRESS_EXPAND_DP,
        expandOnPress: Boolean = true,
        legacyPressScale: Float = 0.96f,
    ): ViewTransform {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val minDim = min(w, h)
        val maxDim = maxOf(w, h)
        val t = pressProgress.coerceIn(0f, 1f)

        val baseScale = if (expandOnPress) {
            1f + expandDp * density / h * t
        } else {
            1f + (legacyPressScale - 1f) * t
        }

        val maxOffset = minDim
        val translationX = maxOffset * tanh(DRAG_TANH_DERIVATIVE * dragOffsetX / maxOffset)
        val translationY = maxOffset * tanh(DRAG_TANH_DERIVATIVE * dragOffsetY / maxOffset)

        val maxDragScale = expandDp * density / h
        val angle = atan2(dragOffsetY, dragOffsetX)
        val dragScaleX =
            maxDragScale * abs(cos(angle) * dragOffsetX / maxDim) * min(w / h, 1f)
        val dragScaleY =
            maxDragScale * abs(sin(angle) * dragOffsetY / maxDim) * min(h / w, 1f)

        return ViewTransform(
            scaleX = baseScale + dragScaleX,
            scaleY = baseScale + dragScaleY,
            translationX = translationX,
            translationY = translationY,
        )
    }
}

/**
 * iOS-style thumb lens: [bandPx] refraction band from the rim, [amountPx] displacement at the rim.
 * Inverts the renderer's lens formula so both land at the given pixel values.
 */
internal fun com.matrix.prismal.PrismalFrameLayout.applyThumbLens(
    bandPx: Float,
    amountPx: Float,
    press: Float,
    dome: Float,
    displacement: Float,
) {
    val band = bandPx.coerceAtLeast(1f)
    setLiquidDomeStrength(dome)
    setDisplacementScale(displacement)
    setHeightBlurFactor(band / (1f + 0.55f * dome))
    setLensRefractionScale(amountPx / ((1f + 0.45f * press) * band * 2f * displacement.coerceAtLeast(0.01f)))
}
