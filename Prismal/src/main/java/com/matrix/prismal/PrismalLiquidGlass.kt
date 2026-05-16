package com.matrix.prismal

import android.util.TypedValue
import androidx.core.graphics.toColorInt

/**
 * Liquid Glass material for [PrismalFrameLayout]
 *
 * Applies the fixed optical recipe (IOR, thickness, normals, lighting, frost tint, etc.).
 * Tunable parameters — blur, refraction band, lens scale, chroma, corner radius, dome, Fresnel
 *
 * @author Saurav Sajeev
 */
object PrismalLiquidGlass {
    private const val IOR = 1.55f
    private const val THICKNESS_DP = 18f
    private const val NORMAL_STRENGTH = 1.15f
    private const val DISPLACEMENT_SCALE = 1.15f
    private const val SMOOTHING = 1.8f
    private const val BRIGHTNESS = 1.08f
    private const val HIGHLIGHT_WIDTH = 1f
    private const val HEIGHT_BLUR_DP = 19f
    private const val CAUSTIC = 0.28f
    private const val RIM = 1.22f
    private const val SPECULAR = 1.52f
    private const val SHININESS = 88f
    private const val LIGHT_X = -0.5f
    private const val LIGHT_Y = -0.8f
    private const val REFRACTION_INSET = 20f
    private const val EDGE_FALLOFF = 4f
    private const val DISPERSION_R = 1f
    private const val DISPERSION_B = 1f
    private const val SHADOW_SOFTNESS = 10f
    private val SHADOW_COLOR = "#23FFFFFF".toColorInt()
    private val GLASS_COLOR = "#230000FF".toColorInt()

    /**
     * Replaces material defaults that are not meant to be tweaked per slider.
     * Safe to call multiple times (e.g. after XML inflation).
     */
    @JvmStatic
    fun applyBase(layout: PrismalFrameLayout) {
        val dm = layout.resources.displayMetrics
        val thickPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THICKNESS_DP, dm)
        val heightBlurPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HEIGHT_BLUR_DP, dm)

        layout.setIOR(IOR)
        layout.setThickness(thickPx)
        layout.setHeightBlurFactor(heightBlurPx)
        layout.setNormalStrength(NORMAL_STRENGTH)
        layout.setDisplacementScale(DISPLACEMENT_SCALE)
        layout.setMinSmoothing(SMOOTHING)
        layout.setBrightness(BRIGHTNESS)
        layout.setHighlightWidth(HIGHLIGHT_WIDTH)
        layout.setCausticIntensity(CAUSTIC)
        layout.setRimStrength(RIM)
        layout.setSpecular(SPECULAR, SHININESS)
        layout.setLightDirection(LIGHT_X, LIGHT_Y)
        layout.setShadowProperties(SHADOW_COLOR, SHADOW_SOFTNESS)
        layout.setGlassColor(GLASS_COLOR)
        layout.setTransmittance(1f)
        layout.setEdgeRefractionFalloff(EDGE_FALLOFF)
        layout.setRefractionInset(REFRACTION_INSET)
        layout.setDispersion(DISPERSION_R, DISPERSION_B)
    }
}
