package com.matrix.prismal

import android.graphics.Color

/**
 * Preset filter styles for PrismalFrameLayout
 * Values are carefully calibrated based on the shader's internal scaling and behavior
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
    val chromaticAberration: Float = 0f, // Shader scales by 0.003, so 100 = 0.3 actual
    val brightness: Float = 1.0f,
    val shadowColor: Int = Color.argb(25, 0, 0, 0),
    val shadowSoftness: Float = 10f,
    val edgeRefractionFalloff: Float = 2.0f
) {

    /**
     * Apply this filter to a PrismalFrameLayout
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
         * Clean, minimal glass effect with subtle refraction
         */
        val SUBTLE = PrismalFilter(
            name = "Subtle",
            refractionInset = 8f,
            cornerRadius = 20f,
            ior = 1.3f,
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
         * Classic frosted glass with moderate blur
         */
        val FROSTED = PrismalFilter(
            name = "Frosted",
            refractionInset = 5f,
            cornerRadius = 24f,
            ior = 1.5f,
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
         * Heavy blur with strong depth - ideal for overlays
         */
        val HEAVY = PrismalFilter(
            name = "Heavy",
            refractionInset = 3f,
            cornerRadius = 28f,
            ior = 1.6f,
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
         * Crystal clear with high refraction and sharp highlights
         */
        val CRYSTAL = PrismalFilter(
            name = "Crystal",
            refractionInset = 10f,
            cornerRadius = 16f,
            ior = 1.8f,
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
         * Soft, dreamy effect with gentle distortion
         */
        val DREAMY = PrismalFilter(
            name = "Dreamy",
            refractionInset = 6f,
            cornerRadius = 32f,
            ior = 1.4f,
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
         * Sharp, precise glass with minimal blur
         */
        val SHARP = PrismalFilter(
            name = "Sharp",
            refractionInset = 12f,
            cornerRadius = 12f,
            ior = 1.7f,
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
         * Vibrant with strong chromatic aberration - rainbow prism effect
         */
        val PRISM = PrismalFilter(
            name = "Prism",
            refractionInset = 7f,
            cornerRadius = 20f,
            ior = 1.9f,
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
         * Ultra smooth with maximum blur - privacy mode
         */
        val OPAQUE = PrismalFilter(
            name = "Opaque",
            refractionInset = 2f,
            cornerRadius = 36f,
            ior = 1.35f,
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
         * Thin, barely-there glass effect
         */
        val WHISPER = PrismalFilter(
            name = "Whisper",
            refractionInset = 15f,
            cornerRadius = 18f,
            ior = 1.2f,
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
         * Bold, dramatic glass with strong effects
         */
        val DRAMATIC = PrismalFilter(
            name = "Dramatic",
            refractionInset = 3f,
            cornerRadius = 30f,
            ior = 2.0f,
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
         * All available preset filters
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
 * Extension function for easy filter application
 */
fun PrismalFrameLayout.applyFilter(filter: PrismalFilter) {
    filter.applyTo(this)
}