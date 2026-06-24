package com.matrix.prismaltest

import android.os.Bundle
import android.graphics.Color
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import android.widget.RadioGroup
import com.matrix.prismal.DownsampleMode
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalLiquidGlass
import com.matrix.prismal.PrismalSlider

class GlassPlaygroundActivity : AppCompatActivity() {

    private lateinit var hero: PrismalFrameLayout
    private lateinit var bars: List<PrismalSlider>
    private lateinit var switchShowNormals: SwitchCompat
    private lateinit var rgDownsample: RadioGroup
    private lateinit var updates: List<(Int) -> Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_glass_playground)

        val root = findViewById<FrameLayout>(R.id.glassPlayRoot)

        intent.getStringExtra("BACKGROUND_URI")?.let { uriString ->
            val uri = uriString.toUri()
            root.background = contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.drawable.Drawable.createFromStream(input, uriString)
            }
        } ?: run {
            val bgResId = intent.getIntExtra("BACKGROUND_RES_ID", R.drawable.bg2)
            if (bgResId != -1) root.setBackgroundResource(bgResId)
        }

        hero = findViewById(R.id.playgroundHeroGlass)
        PrismalLiquidGlass.applyBase(hero)

        fun dp(v: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
        )

        val tmp = mutableListOf<(Int) -> Unit>()
        fun wireSeek(
            slider: PrismalSlider,
            label: TextView? = null,
            fmt: ((Int) -> String)? = null,
            max: Int = 100,
            on: (Int) -> Unit
        ) {
            val baseText = label?.text?.toString().orEmpty()
            slider.setMaxValue(max.toFloat())
            val fullOn: (Int) -> Unit = { p ->
                on(p)
                if (label != null && fmt != null) label.text = "$baseText  (${fmt(p)})"
            }
            tmp.add(fullOn)
            slider.setOnValueChangedListener { value ->
                fullOn(value.toInt())
                hero.updateBackground()
                persistFromUi()
            }
        }

        val M = GlassPlaygroundMappings

        val lblBlur    = findViewById<TextView>(R.id.lblBlur)
        val lblHeight  = findViewById<TextView>(R.id.lblHeight)
        val lblLens    = findViewById<TextView>(R.id.lblLens)
        val lblChroma  = findViewById<TextView>(R.id.lblChroma)
        val lblCorner  = findViewById<TextView>(R.id.lblCorner)
        val lblDome    = findViewById<TextView>(R.id.lblDome)
        val lblFresnel = findViewById<TextView>(R.id.lblFresnel)
        val lblIor     = findViewById<TextView>(R.id.lblIor)
        val lblThick   = findViewById<TextView>(R.id.lblThick)
        val lblNormal  = findViewById<TextView>(R.id.lblNormal)
        val lblDisp    = findViewById<TextView>(R.id.lblDisp)
        val lblSmooth  = findViewById<TextView>(R.id.lblSmooth)
        val lblHi      = findViewById<TextView>(R.id.lblHi)
        val lblBright  = findViewById<TextView>(R.id.lblBright)
        val lblInset   = findViewById<TextView>(R.id.lblInset)
        val lblEdge    = findViewById<TextView>(R.id.lblEdge)
        val lblLx      = findViewById<TextView>(R.id.lblLx)
        val lblLy      = findViewById<TextView>(R.id.lblLy)
        val lblSpec    = findViewById<TextView>(R.id.lblSpec)
        val lblShine   = findViewById<TextView>(R.id.lblShine)
        val lblRim     = findViewById<TextView>(R.id.lblRim)
        val lblDr      = findViewById<TextView>(R.id.lblDr)
        val lblDb      = findViewById<TextView>(R.id.lblDb)
        val lblCaustic = findViewById<TextView>(R.id.lblCaustic)
        val lblTrans   = findViewById<TextView>(R.id.lblTrans)
        val lblShSoft  = findViewById<TextView>(R.id.lblShSoft)
        val lblShAlpha = findViewById<TextView>(R.id.lblShAlpha)

        val seekBlur      = findViewById<PrismalSlider>(R.id.seekBlur)
        val seekHeight    = findViewById<PrismalSlider>(R.id.seekRefractionHeight)
        val seekLens      = findViewById<PrismalSlider>(R.id.seekRefractionAmount)
        val seekChroma    = findViewById<PrismalSlider>(R.id.seekChromatic)
        val seekCorner    = findViewById<PrismalSlider>(R.id.seekCorner)
        val seekDome      = findViewById<PrismalSlider>(R.id.seekLiquidDome)
        val seekFresnel   = findViewById<PrismalSlider>(R.id.seekFresnel)
        val seekIor       = findViewById<PrismalSlider>(R.id.seekIor)
        val seekThickness = findViewById<PrismalSlider>(R.id.seekThickness)
        val seekNormal    = findViewById<PrismalSlider>(R.id.seekNormalStrength)
        val seekDisp      = findViewById<PrismalSlider>(R.id.seekDisplacement)
        val seekSmooth    = findViewById<PrismalSlider>(R.id.seekMinSmooth)
        val seekHi        = findViewById<PrismalSlider>(R.id.seekHighlightWidth)
        val seekBright    = findViewById<PrismalSlider>(R.id.seekBrightness)
        val seekInset     = findViewById<PrismalSlider>(R.id.seekRefractionInset)
        val seekEdge      = findViewById<PrismalSlider>(R.id.seekEdgeFalloff)
        val seekLx        = findViewById<PrismalSlider>(R.id.seekLightX)
        val seekLy        = findViewById<PrismalSlider>(R.id.seekLightY)
        val seekSpec      = findViewById<PrismalSlider>(R.id.seekSpecular)
        val seekShine     = findViewById<PrismalSlider>(R.id.seekShininess)
        val seekRim       = findViewById<PrismalSlider>(R.id.seekRim)
        val seekDr        = findViewById<PrismalSlider>(R.id.seekDispersionR)
        val seekDb        = findViewById<PrismalSlider>(R.id.seekDispersionB)
        val seekCaustic   = findViewById<PrismalSlider>(R.id.seekCaustic)
        val seekTrans     = findViewById<PrismalSlider>(R.id.seekTransmittance)
        val seekShSoft    = findViewById<PrismalSlider>(R.id.seekShadowSoft)
        val seekShAlpha   = findViewById<PrismalSlider>(R.id.seekShadowAlpha)

        wireSeek(seekBlur, lblBlur, { "%.1f".format(M.blurFromProgress(it)) }) {
            hero.setBlurRadius(M.blurFromProgress(it))
        }
        wireSeek(seekHeight, lblHeight, { "%.1f".format(M.heightBlurFromProgress(it)) }, max = M.HEIGHT_PROGRESS_MAX) {
            hero.setHeightBlurFactor(M.heightBlurFromProgress(it))
        }
        wireSeek(seekLens, lblLens, { "%.2f".format(M.lensScaleFromProgress(it)) }) {
            hero.setLensRefractionScale(M.lensScaleFromProgress(it))
        }
        wireSeek(seekChroma, lblChroma, { "%.2f".format(M.chromaFromProgress(it)) }) {
            hero.setChromaticAberration(M.chromaFromProgress(it))
        }
        wireSeek(seekCorner, lblCorner, { "%.0fdp".format(M.cornerDpFromProgress(it)) }) {
            hero.setCornerRadius(dp(M.cornerDpFromProgress(it)))
        }
        wireSeek(seekDome, lblDome, { "%.2f".format(M.domeFromProgress(it)) }) {
            hero.setLiquidDomeStrength(M.domeFromProgress(it))
        }
        wireSeek(seekFresnel, lblFresnel, { "%.2f".format(M.fresnelFromProgress(it)) }) {
            hero.setFresnelReflectStrength(M.fresnelFromProgress(it))
        }
        wireSeek(seekIor, lblIor, { "%.2f".format(M.iorFromProgress(it)) }) {
            hero.setIOR(M.iorFromProgress(it))
        }
        wireSeek(seekThickness, lblThick, { "%.0fdp".format(M.thicknessDpFromProgress(it)) }) {
            hero.setThickness(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, M.thicknessDpFromProgress(it), resources.displayMetrics)
            )
        }
        wireSeek(seekNormal, lblNormal, { "%.2f".format(M.normalStrengthFromProgress(it)) }) {
            hero.setNormalStrength(M.normalStrengthFromProgress(it))
        }
        wireSeek(seekDisp, lblDisp, { "%.2f".format(M.displacementFromProgress(it)) }) {
            hero.setDisplacementScale(M.displacementFromProgress(it))
        }
        wireSeek(seekSmooth, lblSmooth, { "%.2f".format(M.minSmoothingFromProgress(it)) }) {
            hero.setMinSmoothing(M.minSmoothingFromProgress(it))
        }
        wireSeek(seekHi, lblHi, { "%.2f".format(M.highlightWidthFromProgress(it)) }) {
            hero.setHighlightWidth(M.highlightWidthFromProgress(it))
        }
        wireSeek(seekBright, lblBright, { "%.2f".format(M.brightnessFromProgress(it)) }) {
            hero.setBrightness(M.brightnessFromProgress(it))
        }
        wireSeek(seekInset, lblInset, { "%.0fdp".format(M.refractionInsetDpFromProgress(it)) }) {
            hero.setRefractionInset(dp(M.refractionInsetDpFromProgress(it)))
        }
        wireSeek(seekEdge, lblEdge, { "%.1f".format(M.edgeFalloffFromProgress(it)) }) {
            hero.setEdgeRefractionFalloff(M.edgeFalloffFromProgress(it))
        }
        wireSeek(seekLx, lblLx, { "%.2f".format(M.lightXFromProgress(it)) }) { lx ->
            val ly = M.lightYFromProgress(seekLy.getValue().toInt())
            hero.setLightDirection(M.lightXFromProgress(lx), ly)
        }
        wireSeek(seekLy, lblLy, { "%.2f".format(M.lightYFromProgress(it)) }) { ly ->
            val lx = M.lightXFromProgress(seekLx.getValue().toInt())
            hero.setLightDirection(lx, M.lightYFromProgress(ly))
        }
        wireSeek(seekSpec, lblSpec, { "%.2f".format(M.specularFromProgress(it)) }) {
            hero.setSpecular(M.specularFromProgress(it), M.shininessFromProgress(seekShine.getValue().toInt()))
        }
        wireSeek(seekShine, lblShine, { "%.0f".format(M.shininessFromProgress(it)) }) {
            hero.setSpecular(M.specularFromProgress(seekSpec.getValue().toInt()), M.shininessFromProgress(it))
        }
        wireSeek(seekRim, lblRim, { "%.2f".format(M.rimFromProgress(it)) }) {
            hero.setRimStrength(M.rimFromProgress(it))
        }
        wireSeek(seekDr, lblDr, { "%.2f".format(M.dispersionChannelFromProgress(it)) }) {
            hero.setDispersion(M.dispersionChannelFromProgress(it), M.dispersionChannelFromProgress(seekDb.getValue().toInt()))
        }
        wireSeek(seekDb, lblDb, { "%.2f".format(M.dispersionChannelFromProgress(it)) }) {
            hero.setDispersion(M.dispersionChannelFromProgress(seekDr.getValue().toInt()), M.dispersionChannelFromProgress(it))
        }
        wireSeek(seekCaustic, lblCaustic, { "%.3f".format(M.causticFromProgress(it)) }) {
            hero.setCausticIntensity(M.causticFromProgress(it))
        }
        wireSeek(seekTrans, lblTrans, { "%.2f".format(M.transmittanceFromProgress(it)) }) {
            hero.setTransmittance(M.transmittanceFromProgress(it))
        }
        wireSeek(seekShSoft, lblShSoft, { "%.2f".format(M.shadowSoftFromProgress(it)) }) {
            hero.setShadowProperties(
                Color.argb(M.shadowAlphaFromProgress(seekShAlpha.getValue().toInt()), 255, 255, 255),
                M.shadowSoftFromProgress(it)
            )
        }
        wireSeek(seekShAlpha, lblShAlpha, { "${M.shadowAlphaFromProgress(it)}" }) {
            hero.setShadowProperties(
                Color.argb(M.shadowAlphaFromProgress(it), 255, 255, 255),
                M.shadowSoftFromProgress(seekShSoft.getValue().toInt())
            )
        }

        updates = tmp
        bars = listOf(
            seekBlur, seekHeight, seekLens, seekChroma, seekCorner, seekDome, seekFresnel,
            seekIor, seekThickness, seekNormal, seekDisp, seekSmooth, seekHi, seekBright,
            seekInset, seekEdge, seekLx, seekLy, seekSpec, seekShine, seekRim, seekDr, seekDb,
            seekCaustic, seekTrans, seekShSoft, seekShAlpha
        )

        switchShowNormals = findViewById(R.id.switchShowNormals)
        switchShowNormals.setOnCheckedChangeListener { _, _ ->
            hero.setShowNormals(switchShowNormals.isChecked)
            hero.updateBackground()
            persistFromUi()
        }

        rgDownsample = findViewById(R.id.rgDownsample)
        rgDownsample.check(R.id.rbDownsampleBalanced)
        rgDownsample.setOnCheckedChangeListener { _, _ ->
            hero.setCaptureDownsample(selectedDownsampleMode())
            hero.updateBackground()
            persistFromUi()
        }

        GlassPlaygroundPrefs.load(this)?.let { saved ->
            val prog = GlassPlaygroundPrefs.seekProgressFromParams(saved)
            for (i in bars.indices) {
                bars[i].setValue(prog[i].toFloat())
            }
            switchShowNormals.isChecked = saved.showNormals
            applyDownsampleModeToRadioGroup(saved.downsampleMode)
        }

        for (i in bars.indices) {
            updates[i](bars[i].getValue().toInt())
        }
        switchShowNormals.isChecked.let { hero.setShowNormals(it) }
        hero.setCaptureDownsample(selectedDownsampleMode())

        hero.post { hero.updateBackground() }
        persistFromUi()
    }

    private fun persistFromUi() {
        val a = IntArray(bars.size) { bars[it].getValue().toInt() }
        val p = GlassParams.fromControls(
            pBlur = a[0], pHeight = a[1], pLens = a[2], pChroma = a[3], pCorner = a[4],
            pDome = a[5], pFresnel = a[6], pIor = a[7], pThick = a[8], pNormal = a[9],
            pDisp = a[10], pSmooth = a[11], pHi = a[12], pBright = a[13], pInset = a[14],
            pEdge = a[15], pLx = a[16], pLy = a[17], pSpec = a[18], pShine = a[19],
            pRim = a[20], pDr = a[21], pDb = a[22], pCaustic = a[23], pTrans = a[24],
            pShSoft = a[25], pShAlpha = a[26],
            showNormals = switchShowNormals.isChecked, downsampleMode = selectedDownsampleMode()
        )
        GlassPlaygroundPrefs.save(this, p)
    }

    private fun selectedDownsampleMode(): DownsampleMode =
        when (rgDownsample.checkedRadioButtonId) {
            R.id.rbDownsampleOff -> DownsampleMode.OFF
            R.id.rbDownsampleSubtle -> DownsampleMode.SUBTLE
            R.id.rbDownsampleAggressive -> DownsampleMode.AGGRESSIVE
            else -> DownsampleMode.BALANCED
        }

    private fun applyDownsampleModeToRadioGroup(mode: DownsampleMode) {
        val id = when (mode) {
            DownsampleMode.OFF -> R.id.rbDownsampleOff
            DownsampleMode.SUBTLE -> R.id.rbDownsampleSubtle
            DownsampleMode.BALANCED -> R.id.rbDownsampleBalanced
            DownsampleMode.AGGRESSIVE -> R.id.rbDownsampleAggressive
        }
        rgDownsample.check(id)
    }
}
