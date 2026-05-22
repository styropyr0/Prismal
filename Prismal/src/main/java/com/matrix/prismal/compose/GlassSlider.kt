package com.matrix.prismal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.matrix.prismal.PrismalSlider
import kotlin.math.abs

/**
 * A Jetpack Compose wrapper around [PrismalSlider] — an iOS-style liquid-glass slider with a
 * draggable frosted thumb that reveals live glass on press.
 *
 * This is a *controlled* component: [value] drives the thumb position and [onValueChange] is
 * called continuously while dragging. If you do not update [value] in [onValueChange], the
 * thumb will be snapped back on next recomposition.
 *
 * ### Example
 * ```kotlin
 * var brightness by remember { mutableStateOf(50f) }
 * GlassSlider(
 *     value = brightness,
 *     onValueChange = { brightness = it },
 *     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
 * )
 * ```
 *
 * @param value Current slider value in `[0, maxValue]`.
 * @param onValueChange Called with the new value on every drag frame.
 * @param modifier Layout modifier.
 * @param maxValue Upper bound of the value range (default 100).
 * @param accentColor Track fill colour (default `#0088FF`).
 * @param thumbIOR Index of refraction for the thumb glass (default 1.3).
 * @param thumbBrightness Thumb brightness multiplier (default 1.12).
 * @param thumbBlurRadius Resting blur radius for the thumb (default 3).
 * @param update Called after every parameter update — use for advanced thumb setters.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f,
    accentColor: Color = Color(0xFF0088FF.toInt()),
    thumbIOR: Float = 1.3f,
    thumbBrightness: Float = 1.12f,
    thumbBlurRadius: Float = 3f,
    update: (PrismalSlider) -> Unit = {}
) {
    val onValueChangeRef = rememberUpdatedState(onValueChange)
    val updateRef = rememberUpdatedState(update)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PrismalSlider(ctx).also { slider ->
                slider.setOnValueChangedListener { v -> onValueChangeRef.value(v) }
            }
        },
        update = { slider ->
            slider.setMaxValue(maxValue)
            slider.setAccentColor(accentColor.toArgb())
            slider.setThumbIOR(thumbIOR)
            slider.setThumbBrightness(thumbBrightness)
            slider.setThumbBlurRadius(thumbBlurRadius)
            // Only push an external value change; skip when the drag loop already reflects it
            if (abs(slider.getValue() - value) > 0.001f) {
                slider.setValue(value)
            }
            updateRef.value(slider)
        }
    )
}