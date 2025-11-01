package com.matrix.prismal

import android.graphics.Color

/**
 * Preset filter styles for [PrismalFrameLayout], defining configurable parameters for realistic glass effects.
 * Values are calibrated based on the shader's internal scaling (e.g., chromatic aberration is multiplied by 0.003 in the fragment shader)
 * and real-world optical properties where applicable (e.g., Index of Refraction drawn from common glass types).<grok-card data-id="89996f" data-type="citation_card"></grok-card><grok-card data-id="212c78" data-type="citation_card"></grok-card><grok-card data-id="ad5fb7" data-type="citation_card"></grok-card>
 *
 * ### Calibration Notes:
 * - **IOR (Index of Refraction)**: Adjusted to match typical values:
 *   - Soda-lime/crown glass: ~1.52
 *   - Borosilicate: ~1.47
 *   - Flint glass: 1.58–1.75
 *   - Dense/heavy flint (crystal-like): ~1.65–1.75
 *   - Exaggerated for dramatic/prism effects: up to 1.90
 * - Other parameters (e.g., blur, thickness) are artistic tunings for visual appeal on mobile screens.
 * - Apply via [applyTo] or the extension [PrismalFrameLayout.applyFilter].
 *
 * @see [PrismalFrameLayout.setIOR] for runtime adjustments
 * @author Saurav Sajeev
 */
data class PrismalFilter(
    val name: String,
    val refractionInset: Float = 5f,
    val cornerRadius: Float = 24f,
    val ior: Float = 1.5f,
    val thickness: Float = 20f,
    val normalStrength: Float = 1.0f,
    val displacementScale: Float = 1.0f,
    val heightBlurFactor: Float = 15f,
    val minSmoothing: Float = 2f,
    val blurRadius: Float = 2f,
    val highlightWidth: Float = 3f,
    val chromaticAberration: Float = 0f, // Shader scales by 0.003, so 100 = 0.3 actual pixels of offset
    val brightness: Float = 1.0f,
    val shadowColor: Int = Color.argb(25, 0, 0, 0),
    val shadowSoftness: Float = 10f,
    val edgeRefractionFalloff: Float = 2.0f
) {

    /**
     * Applies all properties of this filter to the given [PrismalFrameLayout].
     * Queues updates on the GL thread for thread-safe rendering.
     *
     * @param prismal The [PrismalFrameLayout] to configure.
     */
    fun applyTo(prismal: PrismalFrameLayout) {
        prismal.setRefractionInset(refractionInset)
        prismal.setCornerRadius(cornerRadius)
        prismal.setIOR(ior)
        prismal.setThickness(thickness)
        prismal.setNormalStrength(normalStrength)
        prismal.setDisplacementScale(displacementScale)
        prismal.setHeightBlurFactor(heightBlurFactor)
        prismal.setMinSmoothing(minSmoothing)
        prismal.setBlurRadius(blurRadius)
        prismal.setHighlightWidth(highlightWidth)
        prismal.setChromaticAberration(chromaticAberration)
        prismal.setBrightness(brightness)
        prismal.setShadowProperties(shadowColor, shadowSoftness)
        prismal.setEdgeRefractionFalloff(edgeRefractionFalloff)
    }

    companion object {
        /**
         * Clean, minimal glass effect with subtle refraction, mimicking thin soda-lime glass.
         */
        val SUBTLE = PrismalFilter(
            name = "Subtle",
            refractionInset = 8f,
            cornerRadius = 20f,
            ior = 1.52f, // Typical soda-lime/crown glass
            thickness = 8f,
            normalStrength = 0.5f,
            displacementScale = 0.6f,
            heightBlurFactor = 10f,
            minSmoothing = 1.5f,
            blurRadius = 1.5f,
            highlightWidth = 2f,
            chromaticAberration = 0f,
            brightness = 1.05f,
            shadowColor = Color.argb(15, 0, 0, 0),
            shadowSoftness = 8f,
            edgeRefractionFalloff = 2.8f
        )

        /**
         * Classic frosted glass with moderate blur, based on standard window glass properties.
         */
        val FROSTED = PrismalFilter(
            name = "Frosted",
            refractionInset = 5f,
            cornerRadius = 24f,
            ior = 1.52f, // Soda-lime base (frosting doesn't alter IOR significantly)
            thickness = 15f,
            normalStrength = 0.8f,
            displacementScale = 1.0f,
            heightBlurFactor = 15f,
            minSmoothing = 2f,
            blurRadius = 3f,
            highlightWidth = 3f,
            chromaticAberration = 0f,
            brightness = 1.0f,
            shadowColor = Color.argb(25, 0, 0, 0),
            shadowSoftness = 10f,
            edgeRefractionFalloff = 2.0f
        )

        /**
         * Heavy blur with strong depth, simulating dense flint glass for overlays.
         */
        val HEAVY = PrismalFilter(
            name = "Heavy",
            refractionInset = 3f,
            cornerRadius = 28f,
            ior = 1.65f, // Dense flint glass
            thickness = 25f,
            normalStrength = 1.2f,
            displacementScale = 1.3f,
            heightBlurFactor = 20f,
            minSmoothing = 3f,
            blurRadius = 5f,
            highlightWidth = 4f,
            chromaticAberration = 0f,
            brightness = 0.95f,
            shadowColor = Color.argb(40, 0, 0, 0),
            shadowSoftness = 15f,
            edgeRefractionFalloff = 1.6f
        )

        /**
         * Crystal clear with high refraction and sharp highlights, evoking lead crystal.
         */
        val CRYSTAL = PrismalFilter(
            name = "Crystal",
            refractionInset = 10f,
            cornerRadius = 16f,
            ior = 1.75f, // Heavy flint/lead crystal glass
            thickness = 18f,
            normalStrength = 1.5f,
            displacementScale = 1.4f,
            heightBlurFactor = 8f,
            minSmoothing = 0.8f,
            blurRadius = 0.8f,
            highlightWidth = 5f,
            chromaticAberration = 50f, // Shader scales this by 0.003
            brightness = 1.1f,
            shadowColor = Color.argb(20, 0, 0, 0),
            shadowSoftness = 6f,
            edgeRefractionFalloff = 3.2f
        )

        /**
         * Soft, dreamy effect with gentle distortion, like borosilicate glass.
         */
        val DREAMY = PrismalFilter(
            name = "Dreamy",
            refractionInset = 6f,
            cornerRadius = 32f,
            ior = 1.47f, // Borosilicate (Pyrex-like)
            thickness = 12f,
            normalStrength = 0.6f,
            displacementScale = 0.7f,
            heightBlurFactor = 18f,
            minSmoothing = 4f,
            blurRadius = 4f,
            highlightWidth = 2.5f,
            chromaticAberration = 20f,
            brightness = 1.08f,
            shadowColor = Color.argb(18, 100, 100, 150),
            shadowSoftness = 12f,
            edgeRefractionFalloff = 2.4f
        )

        /**
         * Sharp, precise glass with minimal blur, using high-flint properties.
         */
        val SHARP = PrismalFilter(
            name = "Sharp",
            refractionInset = 12f,
            cornerRadius = 12f,
            ior = 1.70f, // High flint glass
            thickness = 14f,
            normalStrength = 1.8f,
            displacementScale = 1.5f,
            heightBlurFactor = 6f,
            minSmoothing = 0.5f,
            blurRadius = 0.5f,
            highlightWidth = 6f,
            chromaticAberration = 80f, // Strong chroma effect
            brightness = 1.15f,
            shadowColor = Color.argb(30, 0, 0, 0),
            shadowSoftness = 5f,
            edgeRefractionFalloff = 3.8f
        )

        /**
         * Vibrant with strong chromatic aberration for a rainbow prism effect, exaggerated IOR.
         */
        val PRISM = PrismalFilter(
            name = "Prism",
            refractionInset = 7f,
            cornerRadius = 20f,
            ior = 1.85f, // Exaggerated for prism dispersion (rare earth flint ~1.7-1.84)
            thickness = 22f,
            normalStrength = 1.4f,
            displacementScale = 1.4f,
            heightBlurFactor = 12f,
            minSmoothing = 1.8f,
            blurRadius = 2f,
            highlightWidth = 4.5f,
            chromaticAberration = 120f, // Maximum chroma for rainbow effect
            brightness = 1.05f,
            shadowColor = Color.argb(22, 50, 0, 100),
            shadowSoftness = 10f,
            edgeRefractionFalloff = 2.2f
        )

        /**
         * Ultra smooth with maximum blur for privacy mode, standard IOR.
         */
        val OPAQUE = PrismalFilter(
            name = "Opaque",
            refractionInset = 2f,
            cornerRadius = 36f,
            ior = 1.52f, // Soda-lime for broad diffusion
            thickness = 30f,
            normalStrength = 0.4f,
            displacementScale = 0.5f,
            heightBlurFactor = 25f,
            minSmoothing = 5f,
            blurRadius = 8f,
            highlightWidth = 2f,
            chromaticAberration = 0f,
            brightness = 0.9f,
            shadowColor = Color.argb(50, 0, 0, 0),
            shadowSoftness = 20f,
            edgeRefractionFalloff = 1.4f
        )

        /**
         * Thin, barely-there glass effect with low refraction.
         */
        val WHISPER = PrismalFilter(
            name = "Whisper",
            refractionInset = 15f,
            cornerRadius = 18f,
            ior = 1.40f, // Slightly above water for ethereal subtlety
            thickness = 5f,
            normalStrength = 0.3f,
            displacementScale = 0.4f,
            heightBlurFactor = 6f,
            minSmoothing = 1f,
            blurRadius = 1f,
            highlightWidth = 1.5f,
            chromaticAberration = 0f,
            brightness = 1.12f,
            shadowColor = Color.argb(10, 0, 0, 0),
            shadowSoftness = 5f,
            edgeRefractionFalloff = 3.5f
        )

        /**
         * Bold, dramatic glass with strong effects and exaggerated IOR.
         */
        val DRAMATIC = PrismalFilter(
            name = "Dramatic",
            refractionInset = 3f,
            cornerRadius = 30f,
            ior = 1.90f, // Highly exaggerated for intense bending
            thickness = 28f,
            normalStrength = 2.0f,
            displacementScale = 1.8f,
            heightBlurFactor = 22f,
            minSmoothing = 3f,
            blurRadius = 4.5f,
            highlightWidth = 7f,
            chromaticAberration = 100f,
            brightness = 1.0f,
            shadowColor = Color.argb(45, 0, 0, 0),
            shadowSoftness = 16f,
            edgeRefractionFalloff = 1.5f
        )

        /**
         * A list of all available preset filters for easy iteration or selection (e.g., in UI spinners).
         */
        val ALL_FILTERS = listOf(
            SUBTLE,
            FROSTED,
            HEAVY,
            CRYSTAL,
            DREAMY,
            SHARP,
            PRISM,
            OPAQUE,
            WHISPER,
            DRAMATIC
        )
    }
}

/**
 * Extension function to easily apply a [PrismalFilter] to this [PrismalFrameLayout].
 *
 * @receiver The [PrismalFrameLayout] to apply the filter to.
 * @param filter The preset or custom filter to apply.
 * @see PrismalFilter.applyTo
 */
fun PrismalFrameLayout.applyFilter(filter: PrismalFilter) {
    filter.applyTo(this)
}