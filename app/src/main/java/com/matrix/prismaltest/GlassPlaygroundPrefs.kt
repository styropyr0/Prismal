package com.matrix.prismaltest

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalLiquidGlass
import kotlin.math.roundToInt

/**
 * Maps SeekBar progress to [PrismalFrameLayout] values. Height slider uses 0–200 range; others 0–100.
 */
object GlassPlaygroundMappings {

    fun blurFromProgress(p: Int) = 0.45f + p * 0.6f
    fun progressFromBlur(blur: Float) =
        ((blur - 0.45f) / 0.6f).roundToInt().coerceIn(0, 100)

    /** [p] is 0–200 (wide refraction band control). */
    fun heightBlurFromProgress(p: Int) = 2f + (p.coerceIn(0, 200) / 200f) * 44f
    fun progressFromHeightBlur(h: Float) =
        (((h - 2f) / 44f) * 200f).roundToInt().coerceIn(0, 200)

    fun lensScaleFromProgress(p: Int) = 0.35f + p / 95f
    fun progressFromLensScale(s: Float) =
        ((s - 0.35f) * 95f).roundToInt().coerceIn(0, 100)

    fun chromaFromProgress(p: Int) = p * 0.2f
    fun progressFromChroma(c: Float) =
        (c / 0.2f).roundToInt().coerceIn(0, 100)

    fun cornerDpFromProgress(p: Int) = 8f + 52f * (p / 100f)
    fun progressFromCornerDp(dp: Float) =
        (((dp - 8f) / 52f) * 100f).roundToInt().coerceIn(0, 100)

    fun domeFromProgress(p: Int) = p / 100f
    fun progressFromDome(d: Float) =
        (d * 100f).roundToInt().coerceIn(0, 100)

    fun fresnelFromProgress(p: Int) = (p / 100f) * 2.0f
    fun progressFromFresnel(f: Float) =
        ((f / 2.0f) * 100f).roundToInt().coerceIn(0, 100)

    fun iorFromProgress(p: Int) = 1.22f + (p / 100f) * 0.78f
    fun progressFromIor(ior: Float) =
        (((ior - 1.22f) / 0.78f) * 100f).roundToInt().coerceIn(0, 100)

    fun thicknessDpFromProgress(p: Int) = 6f + (p / 100f) * 26f
    fun progressFromThicknessDp(dp: Float) =
        (((dp - 6f) / 26f) * 100f).roundToInt().coerceIn(0, 100)

    fun normalStrengthFromProgress(p: Int) = 0.35f + (p / 100f) * 4.65f
    fun progressFromNormalStrength(v: Float) =
        (((v - 0.35f) / 4.65f) * 100f).roundToInt().coerceIn(0, 100)

    fun displacementFromProgress(p: Int) = 0.3f + (p / 100f) * 4.2f
    fun progressFromDisplacement(v: Float) =
        (((v - 0.3f) / 4.2f) * 100f).roundToInt().coerceIn(0, 100)

    fun minSmoothingFromProgress(p: Int) = 0.5f + (p / 100f) * 3.5f
    fun progressFromMinSmoothing(v: Float) =
        (((v - 0.5f) / 3.5f) * 100f).roundToInt().coerceIn(0, 100)

    fun highlightWidthFromProgress(p: Int) = 0.35f + (p / 100f) * 7.65f
    fun progressFromHighlightWidth(v: Float) =
        (((v - 0.35f) / 7.65f) * 100f).roundToInt().coerceIn(0, 100)

    fun brightnessFromProgress(p: Int) = 0.86f + (p / 100f) * 0.58f
    fun progressFromBrightness(v: Float) =
        (((v - 0.86f) / 0.58f) * 100f).roundToInt().coerceIn(0, 100)

    fun refractionInsetDpFromProgress(p: Int) = 4f + (p / 100f) * 44f
    fun progressFromRefractionInsetDp(dp: Float) =
        (((dp - 4f) / 44f) * 100f).roundToInt().coerceIn(0, 100)

    fun edgeFalloffFromProgress(p: Int) = 1f + (p / 100f) * 11f
    fun progressFromEdgeFalloff(v: Float) =
        (((v - 1f) / 11f) * 100f).roundToInt().coerceIn(0, 100)

    fun lightXFromProgress(p: Int) = -1f + (p / 100f) * 2f
    fun progressFromLightX(v: Float) =
        (((v + 1f) / 2f) * 100f).roundToInt().coerceIn(0, 100)

    fun lightYFromProgress(p: Int) = -1f + (p / 100f) * 2f
    fun progressFromLightY(v: Float) =
        (((v + 1f) / 2f) * 100f).roundToInt().coerceIn(0, 100)

    fun specularFromProgress(p: Int) = 0.12f + (p / 100f) * 3.38f
    fun progressFromSpecular(v: Float) =
        (((v - 0.12f) / 3.38f) * 100f).roundToInt().coerceIn(0, 100)

    fun shininessFromProgress(p: Int) = 14f + (p / 100f) * 210f
    fun progressFromShininess(v: Float) =
        (((v - 14f) / 210f) * 100f).roundToInt().coerceIn(0, 100)

    fun rimFromProgress(p: Int) = 0.18f + (p / 100f) * 2.32f
    fun progressFromRim(v: Float) =
        (((v - 0.18f) / 2.32f) * 100f).roundToInt().coerceIn(0, 100)

    fun dispersionChannelFromProgress(p: Int) = 0.3f + (p / 100f) * 1.5f
    fun progressFromDispersionChannel(v: Float) =
        (((v - 0.3f) / 1.5f) * 100f).roundToInt().coerceIn(0, 100)

    fun causticFromProgress(p: Int) = (p / 100f) * 0.85f
    fun progressFromCaustic(v: Float) =
        ((v / 0.85f) * 100f).roundToInt().coerceIn(0, 100)

    fun transmittanceFromProgress(p: Int) = 0.32f + (p / 100f) * 0.68f
    fun progressFromTransmittance(v: Float) =
        (((v - 0.32f) / 0.68f) * 100f).roundToInt().coerceIn(0, 100)

    /** Shadow softness (shader: ≤1 direct, &gt;1 scaled by /20). */
    fun shadowSoftFromProgress(p: Int) = 0.05f + (p / 100f) * 19.95f
    fun progressFromShadowSoft(v: Float) =
        (((v - 0.05f) / 19.95f) * 100f).roundToInt().coerceIn(0, 100)

    fun shadowAlphaFromProgress(p: Int) = (p * 255 / 100).coerceIn(0, 255)
    fun progressFromShadowAlpha(a: Int) =
        ((a / 255f) * 100f).roundToInt().coerceIn(0, 100)
}

/**
 * Full snapshot of playground-controlled glass (persisted).
 */
data class GlassParams(
    val blurRadius: Float,
    val heightBlurFactor: Float,
    val lensRefractionScale: Float,
    val chromaticAberration: Float,
    val cornerDp: Float,
    val liquidDome: Float,
    val fresnelReflect: Float,
    val ior: Float,
    val thicknessDp: Float,
    val normalStrength: Float,
    val displacementScale: Float,
    val minSmoothing: Float,
    val highlightWidth: Float,
    val brightness: Float,
    val refractionInsetDp: Float,
    val edgeRefractionFalloff: Float,
    val lightDirX: Float,
    val lightDirY: Float,
    val specular: Float,
    val shininess: Float,
    val rimStrength: Float,
    val dispersionR: Float,
    val dispersionB: Float,
    val causticIntensity: Float,
    val transmittance: Float,
    val shadowSoftness: Float,
    val shadowAlpha: Int,
    val showNormals: Boolean
) {
    companion object {
        fun fromControls(
            pBlur: Int,
            pHeight: Int,
            pLens: Int,
            pChroma: Int,
            pCorner: Int,
            pDome: Int,
            pFresnel: Int,
            pIor: Int,
            pThick: Int,
            pNormal: Int,
            pDisp: Int,
            pSmooth: Int,
            pHi: Int,
            pBright: Int,
            pInset: Int,
            pEdge: Int,
            pLx: Int,
            pLy: Int,
            pSpec: Int,
            pShine: Int,
            pRim: Int,
            pDr: Int,
            pDb: Int,
            pCaustic: Int,
            pTrans: Int,
            pShSoft: Int,
            pShAlpha: Int,
            showNormals: Boolean
        ) = GlassParams(
            blurRadius = GlassPlaygroundMappings.blurFromProgress(pBlur),
            heightBlurFactor = GlassPlaygroundMappings.heightBlurFromProgress(pHeight),
            lensRefractionScale = GlassPlaygroundMappings.lensScaleFromProgress(pLens),
            chromaticAberration = GlassPlaygroundMappings.chromaFromProgress(pChroma),
            cornerDp = GlassPlaygroundMappings.cornerDpFromProgress(pCorner),
            liquidDome = GlassPlaygroundMappings.domeFromProgress(pDome),
            fresnelReflect = GlassPlaygroundMappings.fresnelFromProgress(pFresnel),
            ior = GlassPlaygroundMappings.iorFromProgress(pIor),
            thicknessDp = GlassPlaygroundMappings.thicknessDpFromProgress(pThick),
            normalStrength = GlassPlaygroundMappings.normalStrengthFromProgress(pNormal),
            displacementScale = GlassPlaygroundMappings.displacementFromProgress(pDisp),
            minSmoothing = GlassPlaygroundMappings.minSmoothingFromProgress(pSmooth),
            highlightWidth = GlassPlaygroundMappings.highlightWidthFromProgress(pHi),
            brightness = GlassPlaygroundMappings.brightnessFromProgress(pBright),
            refractionInsetDp = GlassPlaygroundMappings.refractionInsetDpFromProgress(pInset),
            edgeRefractionFalloff = GlassPlaygroundMappings.edgeFalloffFromProgress(pEdge),
            lightDirX = GlassPlaygroundMappings.lightXFromProgress(pLx),
            lightDirY = GlassPlaygroundMappings.lightYFromProgress(pLy),
            specular = GlassPlaygroundMappings.specularFromProgress(pSpec),
            shininess = GlassPlaygroundMappings.shininessFromProgress(pShine),
            rimStrength = GlassPlaygroundMappings.rimFromProgress(pRim),
            dispersionR = GlassPlaygroundMappings.dispersionChannelFromProgress(pDr),
            dispersionB = GlassPlaygroundMappings.dispersionChannelFromProgress(pDb),
            causticIntensity = GlassPlaygroundMappings.causticFromProgress(pCaustic),
            transmittance = GlassPlaygroundMappings.transmittanceFromProgress(pTrans),
            shadowSoftness = GlassPlaygroundMappings.shadowSoftFromProgress(pShSoft),
            shadowAlpha = GlassPlaygroundMappings.shadowAlphaFromProgress(pShAlpha),
            showNormals = showNormals
        )
    }
}

object GlassPlaygroundPrefs {

    private const val PREFS = "prismal_glass_playground"
    private const val KEY_SAVED = "saved_v2"
    private const val KEY_SAVED_LEGACY = "saved_v1"

    private const val K_SHOW_NORMALS = "show_normals"

    private fun prefKeys() = arrayOf(
        "blur", "height_blur", "lens_scale", "chroma", "corner_dp", "dome", "fresnel",
        "ior", "thick_dp", "normal", "disp", "smooth", "hi_w", "bright",
        "inset_dp", "edge", "lx", "ly", "spec", "shine", "rim", "dr", "db",
        "caustic", "trans", "sh_soft"
    )

    fun save(context: Context, params: GlassParams) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SAVED, true)
            .putBoolean(K_SHOW_NORMALS, params.showNormals)
        val f = floatArrayOf(
            params.blurRadius, params.heightBlurFactor, params.lensRefractionScale,
            params.chromaticAberration, params.cornerDp, params.liquidDome, params.fresnelReflect,
            params.ior, params.thicknessDp, params.normalStrength, params.displacementScale,
            params.minSmoothing, params.highlightWidth, params.brightness,
            params.refractionInsetDp, params.edgeRefractionFalloff,
            params.lightDirX, params.lightDirY, params.specular, params.shininess,
            params.rimStrength, params.dispersionR, params.dispersionB,
            params.causticIntensity, params.transmittance, params.shadowSoftness
        )
        prefKeys().zip(f.toList()).forEach { (k, v) -> e.putFloat(k, v) }
        e.putInt("sh_alpha_i", params.shadowAlpha)
        e.apply()
    }

    fun load(context: Context): GlassParams? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasV2 = p.getBoolean(KEY_SAVED, false)
        val hasLegacy = p.getBoolean(KEY_SAVED_LEGACY, false)
        if (!hasV2 && !hasLegacy) return null
        val d = defaultParams()
        fun gf(key: String, fallback: Float) =
            if (p.contains(key)) p.getFloat(key, fallback) else fallback
        fun gi(key: String, fallback: Int) =
            if (p.contains(key)) p.getInt(key, fallback) else fallback
        return GlassParams(
            blurRadius = gf("blur", d.blurRadius),
            heightBlurFactor = gf("height_blur", d.heightBlurFactor),
            lensRefractionScale = gf("lens_scale", d.lensRefractionScale),
            chromaticAberration = gf("chroma", d.chromaticAberration),
            cornerDp = gf("corner_dp", d.cornerDp),
            liquidDome = gf("dome", d.liquidDome),
            fresnelReflect = gf("fresnel", d.fresnelReflect),
            ior = gf("ior", d.ior),
            thicknessDp = gf("thick_dp", d.thicknessDp),
            normalStrength = gf("normal", d.normalStrength),
            displacementScale = gf("disp", d.displacementScale),
            minSmoothing = gf("smooth", d.minSmoothing),
            highlightWidth = gf("hi_w", d.highlightWidth),
            brightness = gf("bright", d.brightness),
            refractionInsetDp = gf("inset_dp", d.refractionInsetDp),
            edgeRefractionFalloff = gf("edge", d.edgeRefractionFalloff),
            lightDirX = gf("lx", d.lightDirX),
            lightDirY = gf("ly", d.lightDirY),
            specular = gf("spec", d.specular),
            shininess = gf("shine", d.shininess),
            rimStrength = gf("rim", d.rimStrength),
            dispersionR = gf("dr", d.dispersionR),
            dispersionB = gf("db", d.dispersionB),
            causticIntensity = gf("caustic", d.causticIntensity),
            transmittance = gf("trans", d.transmittance),
            shadowSoftness = gf("sh_soft", d.shadowSoftness),
            shadowAlpha = gi("sh_alpha_i", d.shadowAlpha),
            showNormals = p.getBoolean(K_SHOW_NORMALS, d.showNormals)
        )
    }

    /** Defaults aligned with [PrismalLiquidGlass] + typical playground positions. */
    fun defaultParams(): GlassParams = GlassParams.fromControls(
        pBlur = 40,
        pHeight = 60,
        pLens = 50,
        pChroma = 40,
        pCorner = 45,
        pDome = 78,
        pFresnel = 52,
        pIor = GlassPlaygroundMappings.progressFromIor(1.55f),
        pThick = GlassPlaygroundMappings.progressFromThicknessDp(18f),
        pNormal = GlassPlaygroundMappings.progressFromNormalStrength(1.15f),
        pDisp = GlassPlaygroundMappings.progressFromDisplacement(1.15f),
        pSmooth = GlassPlaygroundMappings.progressFromMinSmoothing(1.8f),
        pHi = GlassPlaygroundMappings.progressFromHighlightWidth(1f),
        pBright = GlassPlaygroundMappings.progressFromBrightness(1.08f),
        pInset = GlassPlaygroundMappings.progressFromRefractionInsetDp(20f),
        pEdge = GlassPlaygroundMappings.progressFromEdgeFalloff(4f),
        pLx = GlassPlaygroundMappings.progressFromLightX(-0.5f),
        pLy = GlassPlaygroundMappings.progressFromLightY(-0.8f),
        pSpec = GlassPlaygroundMappings.progressFromSpecular(1.52f),
        pShine = GlassPlaygroundMappings.progressFromShininess(88f),
        pRim = GlassPlaygroundMappings.progressFromRim(1.22f),
        pDr = GlassPlaygroundMappings.progressFromDispersionChannel(1f),
        pDb = GlassPlaygroundMappings.progressFromDispersionChannel(1f),
        pCaustic = GlassPlaygroundMappings.progressFromCaustic(0.28f),
        pTrans = GlassPlaygroundMappings.progressFromTransmittance(1f),
        pShSoft = GlassPlaygroundMappings.progressFromShadowSoft(10f),
        pShAlpha = GlassPlaygroundMappings.progressFromShadowAlpha(35),
        showNormals = false
    )

    fun seekProgressFromParams(params: GlassParams): IntArray = intArrayOf(
        GlassPlaygroundMappings.progressFromBlur(params.blurRadius),
        GlassPlaygroundMappings.progressFromHeightBlur(params.heightBlurFactor),
        GlassPlaygroundMappings.progressFromLensScale(params.lensRefractionScale),
        GlassPlaygroundMappings.progressFromChroma(params.chromaticAberration),
        GlassPlaygroundMappings.progressFromCornerDp(params.cornerDp),
        GlassPlaygroundMappings.progressFromDome(params.liquidDome),
        GlassPlaygroundMappings.progressFromFresnel(params.fresnelReflect),
        GlassPlaygroundMappings.progressFromIor(params.ior),
        GlassPlaygroundMappings.progressFromThicknessDp(params.thicknessDp),
        GlassPlaygroundMappings.progressFromNormalStrength(params.normalStrength),
        GlassPlaygroundMappings.progressFromDisplacement(params.displacementScale),
        GlassPlaygroundMappings.progressFromMinSmoothing(params.minSmoothing),
        GlassPlaygroundMappings.progressFromHighlightWidth(params.highlightWidth),
        GlassPlaygroundMappings.progressFromBrightness(params.brightness),
        GlassPlaygroundMappings.progressFromRefractionInsetDp(params.refractionInsetDp),
        GlassPlaygroundMappings.progressFromEdgeFalloff(params.edgeRefractionFalloff),
        GlassPlaygroundMappings.progressFromLightX(params.lightDirX),
        GlassPlaygroundMappings.progressFromLightY(params.lightDirY),
        GlassPlaygroundMappings.progressFromSpecular(params.specular),
        GlassPlaygroundMappings.progressFromShininess(params.shininess),
        GlassPlaygroundMappings.progressFromRim(params.rimStrength),
        GlassPlaygroundMappings.progressFromDispersionChannel(params.dispersionR),
        GlassPlaygroundMappings.progressFromDispersionChannel(params.dispersionB),
        GlassPlaygroundMappings.progressFromCaustic(params.causticIntensity),
        GlassPlaygroundMappings.progressFromTransmittance(params.transmittance),
        GlassPlaygroundMappings.progressFromShadowSoft(params.shadowSoftness),
        GlassPlaygroundMappings.progressFromShadowAlpha(params.shadowAlpha)
    )

    fun applyTo(context: Context, vararg frames: PrismalFrameLayout) {
        val params = load(context) ?: defaultParams()
        val dm = context.resources.displayMetrics
        val cornerPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.cornerDp, dm)
        val insetPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.refractionInsetDp, dm)
        val thickPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.thicknessDp, dm)
        val shColor = Color.argb(params.shadowAlpha, 255, 255, 255)
        for (f in frames) {
            PrismalLiquidGlass.applyBase(f)
            f.setBlurRadius(params.blurRadius)
            f.setHeightBlurFactor(params.heightBlurFactor)
            f.setLensRefractionScale(params.lensRefractionScale)
            f.setChromaticAberration(params.chromaticAberration)
            f.setCornerRadius(cornerPx)
            f.setLiquidDomeStrength(params.liquidDome)
            f.setFresnelReflectStrength(params.fresnelReflect)
            f.setIOR(params.ior)
            f.setThickness(thickPx)
            f.setNormalStrength(params.normalStrength)
            f.setDisplacementScale(params.displacementScale)
            f.setMinSmoothing(params.minSmoothing)
            f.setHighlightWidth(params.highlightWidth)
            f.setBrightness(params.brightness)
            f.setRefractionInset(insetPx)
            f.setEdgeRefractionFalloff(params.edgeRefractionFalloff)
            f.setLightDirection(params.lightDirX, params.lightDirY)
            f.setSpecular(params.specular, params.shininess)
            f.setRimStrength(params.rimStrength)
            f.setDispersion(params.dispersionR, params.dispersionB)
            f.setCausticIntensity(params.causticIntensity)
            f.setTransmittance(params.transmittance)
            f.setShadowProperties(shColor, params.shadowSoftness)
            f.setShowNormals(params.showNormals)
        }
    }
}
