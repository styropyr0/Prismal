package com.matrix.prismal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.matrix.prismal.PrismalSwitch

/**
 * A Jetpack Compose wrapper around [PrismalSwitch] — an iOS-style liquid-glass toggle with tap
 * and drag support.
 *
 * This is a *controlled* component: [checked] drives the switch position and [onCheckedChange]
 * is called whenever the user taps or finishes a drag. If you do not update [checked] in
 * [onCheckedChange], the thumb will animate back to its previous position.
 *
 * ### Example
 * ```kotlin
 * var enabled by remember { mutableStateOf(false) }
 * GlassSwitch(
 *     checked = enabled,
 *     onCheckedChange = { enabled = it }
 * )
 * ```
 *
 * @param checked Current on/off state.
 * @param onCheckedChange Called with the new state after a tap or drag release.
 * @param modifier Layout modifier.
 * @param onColor Track colour in the on position (default iOS green `#34C759`).
 * @param offColor Track colour in the off position (default translucent grey).
 * @param thumbIOR Index of refraction for the thumb glass (default 1.3).
 * @param thumbBrightness Thumb brightness multiplier (default 1.12).
 * @param thumbBlurRadius Resting blur radius for the thumb (default 3).
 * @param update Called after every parameter update — use for advanced thumb setters.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = Color(0xFF34C759.toInt()),
    offColor: Color = Color(0x8C787878.toInt()),
    thumbIOR: Float = 1.3f,
    thumbBrightness: Float = 1.12f,
    thumbBlurRadius: Float = 3f,
    update: (PrismalSwitch) -> Unit = {}
) {
    val onCheckedRef = rememberUpdatedState(onCheckedChange)
    val updateRef = rememberUpdatedState(update)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PrismalSwitch(ctx).also { switch ->
                switch.setOnToggleChangedListener { isOn -> onCheckedRef.value(isOn) }
            }
        },
        update = { switch ->
            switch.setOnColor(onColor.toArgb())
            switch.setOffColor(offColor.toArgb())
            switch.setThumbIOR(thumbIOR)
            switch.setThumbBrightness(thumbBrightness)
            switch.setThumbBlurRadius(thumbBlurRadius)
            // Snap to Compose state without animation; user-driven animation runs internally
            switch.setOn(checked, animate = false)
            updateRef.value(switch)
        }
    )
}