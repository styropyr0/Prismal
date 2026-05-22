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
import com.matrix.prismal.PrismalFrameLayout

/**
 * A Jetpack Compose wrapper around [PrismalFrameLayout] that renders an iOS-style liquid-glass
 * surface over whatever is drawn behind it in the window.
 *
 * Arbitrary Compose content can be placed inside the glass via [content]; it appears on top of
 * the glass effect. All glass optical parameters default to the same values used in XML.
 *
 * Use the [update] escape hatch to call any [PrismalFrameLayout] setter that is not directly
 * exposed as a parameter.
 *
 * ### Example
 * ```kotlin
 * GlassBox(
 *     modifier = Modifier.fillMaxWidth().height(120.dp),
 *     ior = 1.55f,
 *     blurRadius = 4f,
 *     cornerRadius = 24.dp,
 *     onClick = { /* spring press + glow */ }
 * ) {
 *     Text("Hello glass", modifier = Modifier.align(Alignment.Center))
 * }
 * ```
 *
 * @param modifier Layout modifier applied to the underlying [AndroidView].
 * @param ior Index of refraction for the glass material (default 1.42).
 * @param blurRadius Gaussian blur radius in shader units (default 2).
 * @param cornerRadius Corner rounding radius (default 28 dp).
 * @param thickness SDF edge-ramp width — keep below ~40 % of min(w,h)/2 (default 18 dp).
 * @param normalStrength Surface normal map intensity (default 3.65).
 * @param displacementScale Background warp multiplier (default 1.0).
 * @param brightness Overall brightness multiplier (default 1.21).
 * @param chromaticAberration Colour-fringe intensity at edges (default 26).
 * @param rimStrength Fresnel rim-glow strength (default 0.18).
 * @param glassColor Additive tint colour (alpha controls strength; default transparent).
 * @param onClick When non-null, enables a spring press-scale and radial glow on tap.
 * @param update Called after every parameter update — use to invoke any advanced setters.
 * @param content Compose content drawn on top of the glass surface.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    ior: Float = 1.42222f,
    blurRadius: Float = 2f,
    cornerRadius: Dp = 28.dp,
    thickness: Dp = 18.dp,
    normalStrength: Float = 3.6515f,
    displacementScale: Float = 1f,
    brightness: Float = 1.21f,
    chromaticAberration: Float = 26f,
    rimStrength: Float = 0.18f,
    glassColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    update: (PrismalFrameLayout) -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val contentState = rememberUpdatedState(content)
    val updateRef = rememberUpdatedState(update)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PrismalFrameLayout(ctx).also { glass ->
                val cv = ComposeView(ctx).apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent { contentState.value() }
                }
                glass.addView(
                    cv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        update = { glass ->
            with(density) {
                glass.setIOR(ior)
                glass.setBlurRadius(blurRadius)
                glass.setCornerRadius(cornerRadius.toPx())
                glass.setThickness(thickness.toPx())
                glass.setNormalStrength(normalStrength)
                glass.setDisplacementScale(displacementScale)
                glass.setBrightness(brightness)
                glass.setChromaticAberration(chromaticAberration)
                glass.setRimStrength(rimStrength)
                glass.setGlassColor(glassColor.toArgb())
                glass.setOnClickWithAnimationListener(onClick)
            }
            updateRef.value(glass)
        }
    )
}