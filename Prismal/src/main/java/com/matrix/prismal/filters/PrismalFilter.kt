package com.matrix.prismal.filters

import android.graphics.Color
import com.matrix.prismal.PrismalFrameLayout

/**
 * Preset filter styles for [PrismalFrameLayout], defining calibrated glass-optical parameters.
 *
 * Values are tuned to produce iOS-quality glass rendering: physically motivated IOR, separable
 * Gaussian pre-blur, Blinn-Phong specular highlights, Fresnel rim glow, per-channel chromatic
 * dispersion, caustic light-focus, and overall transmittance.
 *
 * ### Apply via:
 * ```kotlin
 * filter.applyTo(prismalView)
 * // or
 * prismalView.applyFilter(PrismalFilter.CRYSTAL)
 * ```
 *
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
    val edgeRefractionFalloff: Float = 2.0f,

    val blurRadius: Float = 2f,
    val chromaticAberration: Float = 0f,
    val dispersionR: Float = 1.0f,
    val dispersionB: Float = 1.0f,

    val brightness: Float = 1.0f,
    val highlightWidth: Float = 3f,
    val shadowColor: Int = Color.argb(25, 0, 0, 0),
    val shadowSoftness: Float = 10f,
    val transmittance: Float = 1.0f,

    val lightDirX: Float = -0.5f,
    val lightDirY: Float = -0.8f,
    val specular: Float = 0.8f,
    val shininess: Float = 48f,
    val rimStrength: Float = 0.6f,
    val causticIntensity: Float = 0.15f,

    val showNormals: Boolean = false
) {

    /**
     * Applies all properties of this filter to the given [PrismalFrameLayout].
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
        prismal.setEdgeRefractionFalloff(edgeRefractionFalloff)
        prismal.setBlurRadius(blurRadius)
        prismal.setChromaticAberration(chromaticAberration)
        prismal.setDispersion(dispersionR, dispersionB)
        prismal.setBrightness(brightness)
        prismal.setHighlightWidth(highlightWidth)
        prismal.setShadowProperties(shadowColor, shadowSoftness)
        prismal.setTransmittance(transmittance)
        prismal.setLightDirection(lightDirX, lightDirY)
        prismal.setSpecular(specular, shininess)
        prismal.setRimStrength(rimStrength)
        prismal.setCausticIntensity(causticIntensity)
        prismal.setShowNormals(showNormals)
    }

    companion object {

        /**
         * Clean, minimal glass - thin soda-lime sheet with barely-there refraction.
         */
        val SUBTLE = PrismalFilter(
            name = "Subtle",
            refractionInset = 8f,
            cornerRadius = 20f,
            ior = 1.52f,
            thickness = 8f,
            normalStrength = 0.5f,
            displacementScale = 0.6f,
            heightBlurFactor = 10f,
            minSmoothing = 1.5f,
            blurRadius = 1.5f,
            highlightWidth = 2f,
            chromaticAberration = 0f,
            dispersionR = 0.5f,
            dispersionB = 0.5f,
            brightness = 1.05f,
            shadowColor = Color.argb(15, 0, 0, 0),
            shadowSoftness = 8f,
            edgeRefractionFalloff = 2.8f,
            lightDirX = -0.5f,
            lightDirY = -0.8f,
            specular = 0.4f,
            shininess = 32f,
            rimStrength = 0.3f,
            causticIntensity = 0.08f,
            transmittance = 1.0f
        )

        /**
         * Classic frosted glass - uniform blur, no dispersion, standard crown glass IOR.
         */
        val FROSTED = PrismalFilter(
            name = "Frosted",
            refractionInset = 5f,
            cornerRadius = 24f,
            ior = 1.52f,
            thickness = 15f,
            normalStrength = 0.8f,
            displacementScale = 1.0f,
            heightBlurFactor = 15f,
            minSmoothing = 2f,
            blurRadius = 3f,
            highlightWidth = 3f,
            chromaticAberration = 0f,
            dispersionR = 0.0f,
            dispersionB = 0.0f,
            brightness = 1.0f,
            shadowColor = Color.argb(25, 0, 0, 0),
            shadowSoftness = 10f,
            edgeRefractionFalloff = 2.0f,
            lightDirX = -0.5f,
            lightDirY = -0.8f,
            specular = 0.3f,
            shininess = 16f,
            rimStrength = 0.2f,
            causticIntensity = 0.05f,
            transmittance = 1.0f
        )

        /**
         * Heavy, dense flint glass - strong refraction, thick depth, noticeable edge glow.
         */
        val HEAVY = PrismalFilter(
            name = "Heavy",
            refractionInset = 3f,
            cornerRadius = 28f,
            ior = 1.65f,
            thickness = 25f,
            normalStrength = 1.2f,
            displacementScale = 1.3f,
            heightBlurFactor = 20f,
            minSmoothing = 3f,
            blurRadius = 5f,
            highlightWidth = 4f,
            chromaticAberration = 30f,
            dispersionR = 0.8f,
            dispersionB = 0.8f,
            brightness = 0.95f,
            shadowColor = Color.argb(40, 0, 0, 0),
            shadowSoftness = 15f,
            edgeRefractionFalloff = 1.6f,
            lightDirX = -0.4f,
            lightDirY = -0.9f,
            specular = 1.2f,
            shininess = 64f,
            rimStrength = 0.8f,
            causticIntensity = 0.3f,
            transmittance = 1.0f
        )

        /**
         * Crystal clear lead-crystal - high IOR, crisp specular glint, strong chromatic dispersion.
         * Closest to iOS Liquid Glass quality at its most expressive.
         */
        val CRYSTAL = PrismalFilter(
            name = "Crystal",
            refractionInset = 10f,
            cornerRadius = 16f,
            ior = 1.75f,
            thickness = 18f,
            normalStrength = 1.5f,
            displacementScale = 1.4f,
            heightBlurFactor = 8f,
            minSmoothing = 0.8f,
            blurRadius = 1.0f,
            highlightWidth = 5f,
            chromaticAberration = 60f,
            dispersionR = 2.0f,
            dispersionB = 2.0f,
            brightness = 1.1f,
            shadowColor = Color.argb(20, 0, 0, 0),
            shadowSoftness = 6f,
            edgeRefractionFalloff = 3.2f,
            lightDirX = -0.6f,
            lightDirY = -0.7f,
            specular = 2.0f,
            shininess = 128f,
            rimStrength = 1.5f,
            causticIntensity = 0.5f,
            transmittance = 1.0f
        )

        /**
         * Soft, dreamy borosilicate - gentle distortion, warm blur, low dispersion.
         */
        val DREAMY = PrismalFilter(
            name = "Dreamy",
            refractionInset = 6f,
            cornerRadius = 32f,
            ior = 1.47f,
            thickness = 12f,
            normalStrength = 0.6f,
            displacementScale = 0.7f,
            heightBlurFactor = 18f,
            minSmoothing = 4f,
            blurRadius = 4f,
            highlightWidth = 2.5f,
            chromaticAberration = 20f,
            dispersionR = 0.3f,
            dispersionB = 0.3f,
            brightness = 1.08f,
            shadowColor = Color.argb(18, 100, 100, 150),
            shadowSoftness = 12f,
            edgeRefractionFalloff = 2.4f,
            lightDirX = -0.3f,
            lightDirY = -0.9f,
            specular = 0.5f,
            shininess = 8f,
            rimStrength = 0.4f,
            causticIntensity = 0.15f,
            transmittance = 1.0f
        )

        /**
         * Sharp high-flint glass - razor specular, very little blur, strong edge acuity.
         */
        val SHARP = PrismalFilter(
            name = "Sharp",
            refractionInset = 12f,
            cornerRadius = 12f,
            ior = 1.70f,
            thickness = 14f,
            normalStrength = 1.8f,
            displacementScale = 1.5f,
            heightBlurFactor = 6f,
            minSmoothing = 0.5f,
            blurRadius = 0.5f,
            highlightWidth = 6f,
            chromaticAberration = 80f,
            dispersionR = 1.5f,
            dispersionB = 1.5f,
            brightness = 1.15f,
            shadowColor = Color.argb(30, 0, 0, 0),
            shadowSoftness = 5f,
            edgeRefractionFalloff = 3.8f,
            lightDirX = -0.7f,
            lightDirY = -0.6f,
            specular = 2.5f,
            shininess = 256f,
            rimStrength = 1.2f,
            causticIntensity = 0.3f,
            transmittance = 1.0f
        )

        /**
         * Vibrant prism - maximum chromatic rainbow dispersion, exaggerated IOR.
         */
        val PRISM = PrismalFilter(
            name = "Prism",
            refractionInset = 7f,
            cornerRadius = 20f,
            ior = 1.85f,
            thickness = 22f,
            normalStrength = 1.4f,
            displacementScale = 1.4f,
            heightBlurFactor = 12f,
            minSmoothing = 1.8f,
            blurRadius = 2f,
            highlightWidth = 4.5f,
            chromaticAberration = 120f,
            dispersionR = 3.0f,
            dispersionB = 2.5f,
            brightness = 1.05f,
            shadowColor = Color.argb(22, 50, 0, 100),
            shadowSoftness = 10f,
            edgeRefractionFalloff = 2.2f,
            lightDirX = -0.5f,
            lightDirY = -0.8f,
            specular = 1.5f,
            shininess = 48f,
            rimStrength = 1.0f,
            causticIntensity = 0.4f,
            transmittance = 1.0f
        )

        /**
         * Heavy frosted privacy glass - maximum blur, minimal highlights.
         */
        val OPAQUE = PrismalFilter(
            name = "Opaque",
            refractionInset = 2f,
            cornerRadius = 36f,
            ior = 1.52f,
            thickness = 30f,
            normalStrength = 0.4f,
            displacementScale = 0.5f,
            heightBlurFactor = 25f,
            minSmoothing = 5f,
            blurRadius = 8f,
            highlightWidth = 2f,
            chromaticAberration = 0f,
            dispersionR = 0.0f,
            dispersionB = 0.0f,
            brightness = 0.9f,
            shadowColor = Color.argb(50, 0, 0, 0),
            shadowSoftness = 20f,
            edgeRefractionFalloff = 1.4f,
            lightDirX = -0.5f,
            lightDirY = -0.8f,
            specular = 0.1f,
            shininess = 8f,
            rimStrength = 0.1f,
            causticIntensity = 0.0f,
            transmittance = 1.0f
        )

        /**
         * Whisper-thin glass - barely-there, almost invisible, very subtle effects.
         */
        val WHISPER = PrismalFilter(
            name = "Whisper",
            refractionInset = 15f,
            cornerRadius = 18f,
            ior = 1.40f,
            thickness = 5f,
            normalStrength = 0.3f,
            displacementScale = 0.4f,
            heightBlurFactor = 6f,
            minSmoothing = 1f,
            blurRadius = 1f,
            highlightWidth = 1.5f,
            chromaticAberration = 0f,
            dispersionR = 0.2f,
            dispersionB = 0.2f,
            brightness = 1.12f,
            shadowColor = Color.argb(10, 0, 0, 0),
            shadowSoftness = 5f,
            edgeRefractionFalloff = 3.5f,
            lightDirX = -0.5f,
            lightDirY = -0.8f,
            specular = 0.2f,
            shininess = 16f,
            rimStrength = 0.2f,
            causticIntensity = 0.05f,
            transmittance = 1.0f
        )

        /**
         * Bold, dramatic - intense bending, bright caustics, punchy specular.
         */
        val DRAMATIC = PrismalFilter(
            name = "Dramatic",
            refractionInset = 3f,
            cornerRadius = 30f,
            ior = 1.90f,
            thickness = 28f,
            normalStrength = 2.0f,
            displacementScale = 1.8f,
            heightBlurFactor = 22f,
            minSmoothing = 3f,
            blurRadius = 4.5f,
            highlightWidth = 7f,
            chromaticAberration = 100f,
            dispersionR = 2.0f,
            dispersionB = 2.0f,
            brightness = 1.0f,
            shadowColor = Color.argb(45, 0, 0, 0),
            shadowSoftness = 16f,
            edgeRefractionFalloff = 1.5f,
            lightDirX = -0.4f,
            lightDirY = -0.85f,
            specular = 3.0f,
            shininess = 96f,
            rimStrength = 2.0f,
            causticIntensity = 0.6f,
            transmittance = 1.0f
        )

        /**
         * iOS-style Liquid Glass - faithful recreation of Apple's visionOS / iOS 26 aesthetic:
         * moderate blur, strong Fresnel rim, vivid specular, crisp directional edge arc, subtle dispersion.
         */
        val IOS = PrismalFilter(
            name = "iOS",
            refractionInset = 6f,
            cornerRadius = 22f,
            ior = 1.55f,
            thickness = 16f,
            normalStrength = 1.0f,
            displacementScale = 1.1f,
            heightBlurFactor = 12f,
            minSmoothing = 2f,
            blurRadius = 3.5f,
            highlightWidth = 3.5f,
            chromaticAberration = 35f,
            dispersionR = 1.2f,
            dispersionB = 1.0f,
            brightness = 1.05f,
            shadowColor = Color.argb(20, 0, 0, 0),
            shadowSoftness = 10f,
            edgeRefractionFalloff = 2.5f,
            lightDirX = -0.5f,
            lightDirY = -0.85f,
            specular = 1.4f,
            shininess = 72f,
            rimStrength = 1.0f,
            causticIntensity = 0.25f,
            transmittance = 1.0f
        )

        /** All preset filters in a convenient list. */
        val ALL_FILTERS = listOf(
            SUBTLE, FROSTED, HEAVY, CRYSTAL, DREAMY,
            SHARP, PRISM, OPAQUE, WHISPER, DRAMATIC, IOS
        )
    }
}

/**
 * Extension to conveniently apply a [PrismalFilter] to a [PrismalFrameLayout].
 */
fun PrismalFrameLayout.applyFilter(filter: PrismalFilter) = filter.applyTo(this)