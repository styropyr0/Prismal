package com.matrix.prismaltest

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.matrix.prismal.DownsampleMode
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalIconButton
import com.matrix.prismal.PrismalLiquidGlass
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.core.content.edit

/**
 * Maps slider progress (0..[STEPS]) to [PrismalFrameLayout] values.
 * Ranges follow the library clamps; curves with gamma > 1 give finer control at the low end.
 */
object GlassPlaygroundMappings {
    const val STEPS = 1000
    const val HEIGHT_PROGRESS_MAX = STEPS

    private class Curve(val min: Float, val max: Float, val gamma: Float = 1f) {
        fun value(p: Int): Float {
            val t = (p.coerceIn(0, STEPS) / STEPS.toFloat()).toDouble()
            return min + (max - min) * t.pow(gamma.toDouble()).toFloat()
        }

        fun progress(v: Float): Int {
            val t = ((v - min) / (max - min)).coerceIn(0f, 1f).toDouble()
            return (t.pow(1.0 / gamma) * STEPS).roundToInt().coerceIn(0, STEPS)
        }
    }

    private val blur = Curve(0.45f, 60f, 2f)
    private val heightDp = Curve(2f, 48f, 1.5f)
    private val lens = Curve(0.25f, 2.5f, 1.3f)
    private val chroma = Curve(0f, 40f, 1.8f)
    private val cornerDp = Curve(4f, 60f)
    private val dome = Curve(0f, 2f, 1.2f)
    private val fresnel = Curve(0f, 5f, 1.5f)
    private val ior = Curve(1f, 2.4f, 1.3f)
    private val thicknessDp = Curve(0f, 60f, 1.6f)
    private val normal = Curve(0f, 12f, 2f)
    private val displacement = Curve(0f, 6f, 2f)
    private val minSmoothing = Curve(0.5f, 4f)
    private val highlightWidth = Curve(0.35f, 8f, 1.5f)
    private val brightness = Curve(0.6f, 1.6f)
    private val insetDp = Curve(0f, 60f, 1.6f)
    private val edgeFalloff = Curve(0.5f, 40f, 1.8f)
    private val lightDir = Curve(-1f, 1f)
    private val specular = Curve(0f, 4f, 1.6f)
    private val shininess = Curve(4f, 256f, 2f)
    private val rim = Curve(0f, 3f, 1.5f)
    private val dispersion = Curve(0f, 2f)
    private val caustic = Curve(0f, 1f, 1.6f)
    private val transmittance = Curve(0f, 1f)
    private val shadowSoft = Curve(0.05f, 20f, 1.6f)
    private val shadowAlpha = Curve(0f, 255f, 1.4f)

    fun blurFromProgress(p: Int) = blur.value(p)
    fun progressFromBlur(v: Float) = blur.progress(v)

    /** Refraction band depth in dp */
    fun heightBlurDpFromProgress(p: Int) = heightDp.value(p)
    fun progressFromHeightBlurDp(dp: Float) = heightDp.progress(dp)

    /** @deprecated name kept for call sites - value is dp, convert to px before applying. */
    fun heightBlurFromProgress(p: Int) = heightBlurDpFromProgress(p)
    fun progressFromHeightBlur(h: Float) = progressFromHeightBlurDp(h)

    /** Multiplier on `refractionHeight × 2 × displacementScale` */
    fun lensScaleFromProgress(p: Int) = lens.value(p)
    fun progressFromLensScale(v: Float) = lens.progress(v)

    fun chromaFromProgress(p: Int) = chroma.value(p)
    fun progressFromChroma(v: Float) = chroma.progress(v)

    fun cornerDpFromProgress(p: Int) = cornerDp.value(p)
    fun progressFromCornerDp(v: Float) = cornerDp.progress(v)

    fun domeFromProgress(p: Int) = dome.value(p)
    fun progressFromDome(v: Float) = dome.progress(v)

    fun fresnelFromProgress(p: Int) = fresnel.value(p)
    fun progressFromFresnel(v: Float) = fresnel.progress(v)

    fun iorFromProgress(p: Int) = ior.value(p)
    fun progressFromIor(v: Float) = ior.progress(v)

    fun thicknessDpFromProgress(p: Int) = thicknessDp.value(p)
    fun progressFromThicknessDp(v: Float) = thicknessDp.progress(v)

    fun normalStrengthFromProgress(p: Int) = normal.value(p)
    fun progressFromNormalStrength(v: Float) = normal.progress(v)

    fun displacementFromProgress(p: Int) = displacement.value(p)
    fun progressFromDisplacement(v: Float) = displacement.progress(v)

    fun minSmoothingFromProgress(p: Int) = minSmoothing.value(p)
    fun progressFromMinSmoothing(v: Float) = minSmoothing.progress(v)

    fun highlightWidthFromProgress(p: Int) = highlightWidth.value(p)
    fun progressFromHighlightWidth(v: Float) = highlightWidth.progress(v)

    fun brightnessFromProgress(p: Int) = brightness.value(p)
    fun progressFromBrightness(v: Float) = brightness.progress(v)

    fun refractionInsetDpFromProgress(p: Int) = insetDp.value(p)
    fun progressFromRefractionInsetDp(v: Float) = insetDp.progress(v)

    fun edgeFalloffFromProgress(p: Int) = edgeFalloff.value(p)
    fun progressFromEdgeFalloff(v: Float) = edgeFalloff.progress(v)

    fun lightXFromProgress(p: Int) = lightDir.value(p)
    fun progressFromLightX(v: Float) = lightDir.progress(v)

    fun lightYFromProgress(p: Int) = lightDir.value(p)
    fun progressFromLightY(v: Float) = lightDir.progress(v)

    fun specularFromProgress(p: Int) = specular.value(p)
    fun progressFromSpecular(v: Float) = specular.progress(v)

    fun shininessFromProgress(p: Int) = shininess.value(p)
    fun progressFromShininess(v: Float) = shininess.progress(v)

    fun rimFromProgress(p: Int) = rim.value(p)
    fun progressFromRim(v: Float) = rim.progress(v)

    fun dispersionChannelFromProgress(p: Int) = dispersion.value(p)
    fun progressFromDispersionChannel(v: Float) = dispersion.progress(v)

    fun causticFromProgress(p: Int) = caustic.value(p)
    fun progressFromCaustic(v: Float) = caustic.progress(v)

    fun transmittanceFromProgress(p: Int) = transmittance.value(p)
    fun progressFromTransmittance(v: Float) = transmittance.progress(v)

    /** Shadow softness (shader: ≤1 direct, &gt;1 scaled by /20). */
    fun shadowSoftFromProgress(p: Int) = shadowSoft.value(p)
    fun progressFromShadowSoft(v: Float) = shadowSoft.progress(v)

    fun shadowAlphaFromProgress(p: Int) = shadowAlpha.value(p).roundToInt().coerceIn(0, 255)
    fun progressFromShadowAlpha(a: Int) = shadowAlpha.progress(a.toFloat())
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
    val showNormals: Boolean,
    val downsampleMode: DownsampleMode
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
            showNormals: Boolean,
            downsampleMode: DownsampleMode = DownsampleMode.BALANCED
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
            showNormals = showNormals,
            downsampleMode = downsampleMode
        )
    }
}

object GlassPlaygroundPrefs {

    private const val PREFS = "prismal_glass_playground"
    private const val KEY_SAVED = "saved_v3"

    private const val K_SHOW_NORMALS = "show_normals"

    private fun prefKeys() = arrayOf(
        "blur", "height_blur", "lens_scale", "chroma", "corner_dp", "dome", "fresnel",
        "ior", "thick_dp", "normal", "disp", "smooth", "hi_w", "bright",
        "inset_dp", "edge", "lx", "ly", "spec", "shine", "rim", "dr", "db",
        "caustic", "trans", "sh_soft"
    )

    fun save(context: Context, params: GlassParams) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SAVED, true)
            putBoolean(K_SHOW_NORMALS, params.showNormals)
            val f = floatArrayOf(
                params.blurRadius,
                params.heightBlurFactor,
                params.lensRefractionScale,
                params.chromaticAberration,
                params.cornerDp,
                params.liquidDome,
                params.fresnelReflect,
                params.ior,
                params.thicknessDp,
                params.normalStrength,
                params.displacementScale,
                params.minSmoothing,
                params.highlightWidth,
                params.brightness,
                params.refractionInsetDp,
                params.edgeRefractionFalloff,
                params.lightDirX,
                params.lightDirY,
                params.specular,
                params.shininess,
                params.rimStrength,
                params.dispersionR,
                params.dispersionB,
                params.causticIntensity,
                params.transmittance,
                params.shadowSoftness
            )
            prefKeys().zip(f.toList()).forEach { (k, v) -> putFloat(k, v) }
            putInt("sh_alpha_i", params.shadowAlpha)
            putInt("downsample_mode", params.downsampleMode.ordinal)
        }
    }

    fun load(context: Context): GlassParams? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_SAVED, false)) return null
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
            showNormals = p.getBoolean(K_SHOW_NORMALS, d.showNormals),
            downsampleMode = DownsampleMode.entries[
                gi("downsample_mode", d.downsampleMode.ordinal).coerceIn(0, DownsampleMode.entries.lastIndex)
            ]
        )
    }

    /** Defaults tuned for iOS style refraction on the 280 dp hero card. */
    fun defaultParams(): GlassParams = GlassParams.fromControls(
        pBlur = GlassPlaygroundMappings.progressFromBlur(48f),
        pHeight = GlassPlaygroundMappings.progressFromHeightBlurDp(16f),
        pLens = GlassPlaygroundMappings.progressFromLensScale(1.0f),
        pChroma = GlassPlaygroundMappings.progressFromChroma(22f),
        pCorner = GlassPlaygroundMappings.progressFromCornerDp(31f),
        pDome = GlassPlaygroundMappings.progressFromDome(2f),
        pFresnel = GlassPlaygroundMappings.progressFromFresnel(2.5f),
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
        showNormals = false,
        downsampleMode = DownsampleMode.BALANCED
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

    fun applyToIconButtons(context: Context, vararg buttons: PrismalIconButton) {
        val params = load(context) ?: defaultParams()
        val shColor = Color.argb(params.shadowAlpha, 255, 255, 255)
        for (b in buttons) {
            val s = b.glassSurface
            b.setBlurRadius(params.blurRadius)
            s.setIOR(params.ior)
            s.setHighlightWidth(params.highlightWidth)
            s.setBrightness(params.brightness)
            s.setLightDirection(params.lightDirX, params.lightDirY)
            s.setSpecular(params.specular, params.shininess)
            s.setRimStrength(params.rimStrength)
            s.setDispersion(params.dispersionR, params.dispersionB)
            s.setCausticIntensity(params.causticIntensity)
            s.setTransmittance(params.transmittance)
            s.setShadowProperties(shColor, params.shadowSoftness)
            s.setMinSmoothing(params.minSmoothing)
            s.setShowNormals(params.showNormals)
            s.updateBackground()
        }
    }

    /** Applies saved playground tuning to card-sized [PrismalFrameLayout] surfaces only. */
    fun applyTo(context: Context, vararg frames: PrismalFrameLayout) {
        val params = load(context) ?: defaultParams()
        val dm = context.resources.displayMetrics
        val cornerPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.cornerDp, dm)
        val insetPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.refractionInsetDp, dm)
        val thickPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, params.thicknessDp, dm)
        val heightBlurPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            params.heightBlurFactor,
            dm
        )
        val shColor = Color.argb(params.shadowAlpha, 255, 255, 255)
        for (f in frames) {
            PrismalLiquidGlass.applyBase(f)
            f.setBlurRadius(params.blurRadius)
            f.setHeightBlurFactor(heightBlurPx)
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
            f.setCaptureDownsample(if (params.downsampleMode == DownsampleMode.OFF) null else params.downsampleMode)
        }
    }
}
