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
    val liquidDomeStrength: Float = 1f,
    val fresnelReflectionStrength: Float = 0.79f,

    val blurRadius: Float = 2f,
    val chromaticAberration: Float = 0f,
    val dispersionR: Float = 1.0f,
    val dispersionB: Float = 1.0f,

    val brightness: Float = 1.0f,
    val highlightWidth: Float = 3f,
    val shadowColor: Int = Color.argb(0, 0, 0, 0),
    val shadowSoftness: Float = 1f,
    val transmittance: Float = 1.0f,

    val lightDirX: Float = 1f,
    val lightDirY: Float = 0.62f,
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
        prismal.setLiquidDomeStrength(liquidDomeStrength)
        prismal.setFresnelReflectStrength(fresnelReflectionStrength)
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
            refractionInset = 25f,
            cornerRadius = 20f,
            ior = 1.52f,
            thickness = 18f,
            normalStrength = 2.5f,
            liquidDomeStrength = 1.2f,
            displacementScale = 0.08f,
            heightBlurFactor = 40f,
            minSmoothing = 2.5f,
            blurRadius = 10f,
            highlightWidth = 0.6f,
            chromaticAberration = 0f,
            dispersionR = 0.7f,
            dispersionB = 0.7f,
            brightness = 1.04f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 28f,
            fresnelReflectionStrength = 0.45f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.08f,
            shininess = 64f,
            rimStrength = 0.35f,
            causticIntensity = 0f,
            transmittance = 1.0f
        )

        /**
         * Classic frosted glass - uniform blur, no dispersion, standard crown glass IOR.
         */
        val FROSTED = PrismalFilter(
            name = "Frosted",
            refractionInset = 20f,
            cornerRadius = 24f,
            ior = 1.52f,
            thickness = 35f,
            normalStrength = 3.5f,
            liquidDomeStrength = 1.8f,
            displacementScale = 0.1f,
            heightBlurFactor = 75f,
            minSmoothing = 3f,
            blurRadius = 38f,
            highlightWidth = 0.4f,
            chromaticAberration = 0f,
            dispersionR = 0.0f,
            dispersionB = 0.0f,
            brightness = 1.0f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 18f,
            fresnelReflectionStrength = 0.5f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.06f,
            shininess = 24f,
            rimStrength = 0.25f,
            causticIntensity = 0f,
            transmittance = 1.0f
        )

        /**
         * Heavy, dense flint glass - strong refraction, thick depth, noticeable edge glow.
         */
        val HEAVY = PrismalFilter(
            name = "Heavy",
            refractionInset = 30f,
            cornerRadius = 28f,
            ior = 1.65f,
            thickness = 75f,
            normalStrength = 12f,
            liquidDomeStrength = 4.2f,
            displacementScale = 0.4f,
            heightBlurFactor = 130f,
            minSmoothing = 3f,
            blurRadius = 22f,
            highlightWidth = 1.5f,
            chromaticAberration = 8f,
            dispersionR = 1.5f,
            dispersionB = 1.5f,
            brightness = 0.96f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 12f,
            fresnelReflectionStrength = 0.9f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.25f,
            shininess = 96f,
            rimStrength = 1.2f,
            causticIntensity = 0.15f,
            transmittance = 1.0f
        )

        /**
         * Crystal clear lead-crystal - high IOR, crisp specular glint, strong chromatic dispersion.
         * Closest to iOS Liquid Glass quality at its most expressive.
         */
        val CRYSTAL = PrismalFilter(
            name = "Crystal",
            refractionInset = 55f,
            cornerRadius = 16f,
            ior = 1.75f,
            thickness = 62f,
            normalStrength = 14f,
            liquidDomeStrength = 3.5f,
            displacementScale = 0.22f,
            heightBlurFactor = 90f,
            minSmoothing = 0.8f,
            blurRadius = 7f,
            highlightWidth = 2.8f,
            chromaticAberration = 22f,
            dispersionR = 2.2f,
            dispersionB = 2.2f,
            brightness = 1.1f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 36f,
            fresnelReflectionStrength = 1.0f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.5f,
            shininess = 200f,
            rimStrength = 1.8f,
            causticIntensity = 0.28f,
            transmittance = 1.0f
        )

        /**
         * Soft, dreamy borosilicate - gentle distortion, warm blur, low dispersion.
         */
        val DREAMY = PrismalFilter(
            name = "Dreamy",
            refractionInset = 22f,
            cornerRadius = 32f,
            ior = 1.47f,
            thickness = 42f,
            normalStrength = 4.5f,
            liquidDomeStrength = 2.2f,
            displacementScale = 0.08f,
            heightBlurFactor = 100f,
            minSmoothing = 4f,
            blurRadius = 30f,
            highlightWidth = 0.4f,
            chromaticAberration = 5f,
            dispersionR = 0.6f,
            dispersionB = 0.6f,
            brightness = 1.08f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 16f,
            fresnelReflectionStrength = 0.6f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.07f,
            shininess = 16f,
            rimStrength = 0.45f,
            causticIntensity = 0.05f,
            transmittance = 1.0f
        )

        /**
         * Sharp high-flint glass - razor specular, very little blur, strong edge acuity.
         */
        val SHARP = PrismalFilter(
            name = "Sharp",
            refractionInset = 65f,
            cornerRadius = 12f,
            ior = 1.70f,
            thickness = 52f,
            normalStrength = 18f,
            liquidDomeStrength = 4.8f,
            displacementScale = 0.55f,
            heightBlurFactor = 62f,
            minSmoothing = 0.5f,
            blurRadius = 4f,
            highlightWidth = 4.5f,
            chromaticAberration = 28f,
            dispersionR = 2.0f,
            dispersionB = 2.0f,
            brightness = 1.15f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 40f,
            fresnelReflectionStrength = 1.1f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.85f,
            shininess = 256f,
            rimStrength = 1.5f,
            causticIntensity = 0.18f,
            transmittance = 1.0f
        )

        /**
         * Vibrant prism - maximum chromatic rainbow dispersion, exaggerated IOR.
         */
        val PRISM = PrismalFilter(
            name = "Prism",
            refractionInset = 28f,
            cornerRadius = 20f,
            ior = 1.85f,
            thickness = 68f,
            normalStrength = 10f,
            liquidDomeStrength = 3.8f,
            displacementScale = 0.28f,
            heightBlurFactor = 108f,
            minSmoothing = 1.8f,
            blurRadius = 16f,
            highlightWidth = 2.0f,
            chromaticAberration = 55f,
            dispersionR = 3.0f,
            dispersionB = 2.5f,
            brightness = 1.05f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 16f,
            fresnelReflectionStrength = 0.85f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.35f,
            shininess = 64f,
            rimStrength = 1.0f,
            causticIntensity = 0.22f,
            transmittance = 1.0f
        )

        /**
         * Heavy frosted privacy glass - maximum blur, minimal highlights.
         */
        val OPAQUE = PrismalFilter(
            name = "Opaque",
            refractionInset = 12f,
            cornerRadius = 36f,
            ior = 1.52f,
            thickness = 100f,
            normalStrength = 2.0f,
            liquidDomeStrength = 1.2f,
            displacementScale = 0.05f,
            heightBlurFactor = 149f,
            minSmoothing = 5f,
            blurRadius = 55f,
            highlightWidth = 0.38f,
            chromaticAberration = 0f,
            dispersionR = 0.0f,
            dispersionB = 0.0f,
            brightness = 0.92f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 8f,
            fresnelReflectionStrength = 0.35f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.04f,
            shininess = 16f,
            rimStrength = 0.15f,
            causticIntensity = 0f,
            transmittance = 1.0f
        )

        /**
         * Whisper-thin glass - barely-there, almost invisible, very subtle effects.
         */
        val WHISPER = PrismalFilter(
            name = "Whisper",
            refractionInset = 72f,
            cornerRadius = 18f,
            ior = 1.40f,
            thickness = 10f,
            normalStrength = 1.5f,
            liquidDomeStrength = 1.0f,
            displacementScale = 0.05f,
            heightBlurFactor = 28f,
            minSmoothing = 1f,
            blurRadius = 5f,
            highlightWidth = 0.38f,
            chromaticAberration = 0f,
            dispersionR = 0.4f,
            dispersionB = 0.4f,
            brightness = 1.12f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 36f,
            fresnelReflectionStrength = 0.3f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.05f,
            shininess = 48f,
            rimStrength = 0.22f,
            causticIntensity = 0f,
            transmittance = 1.0f
        )

        /**
         * Bold, dramatic - intense bending, bright caustics, punchy specular.
         */
        val DRAMATIC = PrismalFilter(
            name = "Dramatic",
            refractionInset = 18f,
            cornerRadius = 30f,
            ior = 1.90f,
            thickness = 100f,
            normalStrength = 22f,
            liquidDomeStrength = 6.5f,
            displacementScale = 0.75f,
            heightBlurFactor = 149f,
            minSmoothing = 3f,
            blurRadius = 28f,
            highlightWidth = 5.5f,
            chromaticAberration = 45f,
            dispersionR = 2.5f,
            dispersionB = 2.5f,
            brightness = 1.02f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 1f,
            edgeRefractionFalloff = 8f,
            fresnelReflectionStrength = 1.2f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 1.5f,
            shininess = 128f,
            rimStrength = 2.4f,
            causticIntensity = 0.4f,
            transmittance = 1.0f
        )

        /**
         * iOS-style Liquid Glass - faithful recreation of Apple's visionOS / iOS 26 aesthetic:
         * moderate blur, strong Fresnel rim, vivid specular, crisp directional edge arc, subtle dispersion.
         */
        val IOS = PrismalFilter(
            name = "iOS",
            refractionInset = 51f,
            cornerRadius = 31f,
            ior = 1.55f,
            thickness = 100f,
            fresnelReflectionStrength = 0.79f,
            normalStrength = 8.38f,
            liquidDomeStrength = 3.2f,
            displacementScale = 0.15f,
            heightBlurFactor = 149.3f,
            minSmoothing = 4f,
            blurRadius = 19f,
            highlightWidth = 0.35f,
            chromaticAberration = 3.85f,
            dispersionR = 1.31f,
            dispersionB = 1.31f,
            brightness = 1.07f,
            shadowColor = Color.argb(0, 0, 0, 0),
            shadowSoftness = 0.5f,
            edgeRefractionFalloff = 40f,
            lightDirX = 1f,
            lightDirY = 0.62f,
            specular = 0.12f,
            shininess = 128f,
            rimStrength = 0.78f,
            causticIntensity = 0f,
            transmittance = 1.0f,
        )
    }
}

/**
 * Extension to conveniently apply a [PrismalFilter] to a [PrismalFrameLayout].
 */
fun PrismalFrameLayout.applyFilter(filter: PrismalFilter) = filter.applyTo(this)
