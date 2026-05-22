package com.matrix.prismal.compose

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.matrix.prismal.PrismalButton

/**
 * A Jetpack Compose wrapper around [PrismalButton] — an interactive glass-material button with
 * a spring press-scale animation and refractive "glass pulse" feedback.
 *
 * Compose content (labels, icons, etc.) is placed inside the glass surface via [content].
 * The click animation and touch handling are managed by [PrismalButton] internally.
 *
 * ### Example
 * ```kotlin
 * GlassButton(
 *     onClick = { /* handle tap */ },
 *     modifier = Modifier.width(180.dp).height(56.dp),
 *     cornerRadius = 28.dp
 * ) {
 *     Text("Tap me", modifier = Modifier.align(Alignment.Center))
 * }
 * ```
 *
 * @param onClick Invoked when the button is released.
 * @param modifier Layout modifier.
 * @param ior Index of refraction (default 1.85).
 * @param blurRadius Blur radius for the glass frost (default 3).
 * @param cornerRadius Edge rounding (default 32 dp).
 * @param normalStrength Normal-map intensity driving the glass pulse animation (default 12).
 * @param displacementScale Background warp multiplier (default 10).
 * @param brightness Brightness multiplier (default 1.6).
 * @param chromaticAberration Colour-fringe strength (default 8).
 * @param glassColor Additive tint (alpha controls strength; default transparent).
 * @param update Called after every parameter update — use for advanced setters.
 * @param content Compose content rendered on top of the glass surface.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ior: Float = 1.85f,
    blurRadius: Float = 3f,
    cornerRadius: Dp = 32.dp,
    normalStrength: Float = 12f,
    displacementScale: Float = 10f,
    brightness: Float = 1.6f,
    chromaticAberration: Float = 8f,
    glassColor: Color = Color.Transparent,
    update: (PrismalButton) -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val onClickState = rememberUpdatedState(onClick)
    val contentState = rememberUpdatedState(content)
    val updateRef = rememberUpdatedState(update)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PrismalButton(ctx).also { button ->
                // Content sits on top of the glass surface; pass touches through
                val cv = ComposeView(ctx).apply {
                    isClickable = false
                    isFocusable = false
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent { contentState.value() }
                }
                button.addView(
                    cv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                // Capture onClickState lazily so the latest lambda is always called
                button.setOnClickListener { onClickState.value() }
            }
        },
        update = { button ->
            with(density) {
                button.setIOR(ior)
                button.setBlurRadius(blurRadius)
                button.setCornerRadius(cornerRadius.toPx())
                button.setNormalStrength(normalStrength)
                button.setDisplacementScale(displacementScale)
                button.setBrightness(brightness)
                button.setChromaticAberration(chromaticAberration)
                button.setGlassColor(glassColor.toArgb())
            }
            updateRef.value(button)
        }
    )
}