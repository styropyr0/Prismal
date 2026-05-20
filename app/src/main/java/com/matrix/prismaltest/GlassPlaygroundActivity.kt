package com.matrix.prismaltest

import android.os.Bundle
import android.graphics.Color
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import android.widget.RadioGroup
import com.matrix.prismal.DownsampleMode
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalLiquidGlass
import com.matrix.prismal.PrismalSlider

/**
 * Full glass playground: every tunable [PrismalFrameLayout] / renderer parameter with live sliders.
 * Persists to [GlassPlaygroundPrefs] and applies on home / drag-show layouts via [GlassPlaygroundPrefs.applyTo].
 */
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
            TypedValue.COMPLEX_UNIT_DIP,
            v,
            resources.displayMetrics
        )

        val tmp = mutableListOf<(Int) -> Unit>()
        fun wireSeek(slider: PrismalSlider, max: Int = 100, on: (Int) -> Unit) {
            slider.setMaxValue(max.toFloat())
            tmp.add(on)
            slider.setOnValueChangedListener { value ->
                on(value.toInt())
                hero.updateBackground()
                persistFromUi()
            }
        }

        val seekBlur = findViewById<PrismalSlider>(R.id.seekBlur)
        val seekHeight = findViewById<PrismalSlider>(R.id.seekRefractionHeight)
        val seekLens = findViewById<PrismalSlider>(R.id.seekRefractionAmount)
        val seekChroma = findViewById<PrismalSlider>(R.id.seekChromatic)
        val seekCorner = findViewById<PrismalSlider>(R.id.seekCorner)
        val seekDome = findViewById<PrismalSlider>(R.id.seekLiquidDome)
        val seekFresnel = findViewById<PrismalSlider>(R.id.seekFresnel)
        val seekIor = findViewById<PrismalSlider>(R.id.seekIor)
        val seekThickness = findViewById<PrismalSlider>(R.id.seekThickness)
        val seekNormal = findViewById<PrismalSlider>(R.id.seekNormalStrength)
        val seekDisp = findViewById<PrismalSlider>(R.id.seekDisplacement)
        val seekSmooth = findViewById<PrismalSlider>(R.id.seekMinSmooth)
        val seekHi = findViewById<PrismalSlider>(R.id.seekHighlightWidth)
        val seekBright = findViewById<PrismalSlider>(R.id.seekBrightness)
        val seekInset = findViewById<PrismalSlider>(R.id.seekRefractionInset)
        val seekEdge = findViewById<PrismalSlider>(R.id.seekEdgeFalloff)
        val seekLx = findViewById<PrismalSlider>(R.id.seekLightX)
        val seekLy = findViewById<PrismalSlider>(R.id.seekLightY)
        val seekSpec = findViewById<PrismalSlider>(R.id.seekSpecular)
        val seekShine = findViewById<PrismalSlider>(R.id.seekShininess)
        val seekRim = findViewById<PrismalSlider>(R.id.seekRim)
        val seekDr = findViewById<PrismalSlider>(R.id.seekDispersionR)
        val seekDb = findViewById<PrismalSlider>(R.id.seekDispersionB)
        val seekCaustic = findViewById<PrismalSlider>(R.id.seekCaustic)
        val seekTrans = findViewById<PrismalSlider>(R.id.seekTransmittance)
        val seekShSoft = findViewById<PrismalSlider>(R.id.seekShadowSoft)
        val seekShAlpha = findViewById<PrismalSlider>(R.id.seekShadowAlpha)

        wireSeek(seekBlur) { hero.setBlurRadius(GlassPlaygroundMappings.blurFromProgress(it)) }
        wireSeek(seekHeight, max = GlassPlaygroundMappings.HEIGHT_PROGRESS_MAX) {
            hero.setHeightBlurFactor(GlassPlaygroundMappings.heightBlurFromProgress(it))
        }
        wireSeek(seekLens) {
            hero.setLensRefractionScale(
                GlassPlaygroundMappings.lensScaleFromProgress(
                    it
                )
            )
        }
        wireSeek(seekChroma) {
            hero.setChromaticAberration(
                GlassPlaygroundMappings.chromaFromProgress(
                    it
                )
            )
        }
        wireSeek(seekCorner) {
            hero.setCornerRadius(
                dp(
                    GlassPlaygroundMappings.cornerDpFromProgress(
                        it
                    )
                )
            )
        }
        wireSeek(seekDome) { hero.setLiquidDomeStrength(GlassPlaygroundMappings.domeFromProgress(it)) }
        wireSeek(seekFresnel) {
            hero.setFresnelReflectStrength(
                GlassPlaygroundMappings.fresnelFromProgress(
                    it
                )
            )
        }
        wireSeek(seekIor) { hero.setIOR(GlassPlaygroundMappings.iorFromProgress(it)) }
        wireSeek(seekThickness) {
            hero.setThickness(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    GlassPlaygroundMappings.thicknessDpFromProgress(it),
                    resources.displayMetrics
                )
            )
        }
        wireSeek(seekNormal) {
            hero.setNormalStrength(
                GlassPlaygroundMappings.normalStrengthFromProgress(
                    it
                )
            )
        }
        wireSeek(seekDisp) {
            hero.setDisplacementScale(
                GlassPlaygroundMappings.displacementFromProgress(
                    it
                )
            )
        }
        wireSeek(seekSmooth) {
            hero.setMinSmoothing(
                GlassPlaygroundMappings.minSmoothingFromProgress(
                    it
                )
            )
        }
        wireSeek(seekHi) {
            hero.setHighlightWidth(
                GlassPlaygroundMappings.highlightWidthFromProgress(
                    it
                )
            )
        }
        wireSeek(seekBright) { hero.setBrightness(GlassPlaygroundMappings.brightnessFromProgress(it)) }
        wireSeek(seekInset) {
            hero.setRefractionInset(
                dp(GlassPlaygroundMappings.refractionInsetDpFromProgress(it))
            )
        }
        wireSeek(seekEdge) {
            hero.setEdgeRefractionFalloff(
                GlassPlaygroundMappings.edgeFalloffFromProgress(
                    it
                )
            )
        }
        wireSeek(seekLx) { lx ->
            val ly = GlassPlaygroundMappings.lightYFromProgress(seekLy.getValue().toInt())
            hero.setLightDirection(GlassPlaygroundMappings.lightXFromProgress(lx), ly)
        }
        wireSeek(seekLy) { ly ->
            val lx = GlassPlaygroundMappings.lightXFromProgress(seekLx.getValue().toInt())
            hero.setLightDirection(lx, GlassPlaygroundMappings.lightYFromProgress(ly))
        }
        wireSeek(seekSpec) {
            hero.setSpecular(
                GlassPlaygroundMappings.specularFromProgress(it),
                GlassPlaygroundMappings.shininessFromProgress(seekShine.getValue().toInt())
            )
        }
        wireSeek(seekShine) {
            hero.setSpecular(
                GlassPlaygroundMappings.specularFromProgress(seekSpec.getValue().toInt()),
                GlassPlaygroundMappings.shininessFromProgress(it)
            )
        }
        wireSeek(seekRim) { hero.setRimStrength(GlassPlaygroundMappings.rimFromProgress(it)) }
        wireSeek(seekDr) {
            hero.setDispersion(
                GlassPlaygroundMappings.dispersionChannelFromProgress(it),
                GlassPlaygroundMappings.dispersionChannelFromProgress(seekDb.getValue().toInt())
            )
        }
        wireSeek(seekDb) {
            hero.setDispersion(
                GlassPlaygroundMappings.dispersionChannelFromProgress(seekDr.getValue().toInt()),
                GlassPlaygroundMappings.dispersionChannelFromProgress(it)
            )
        }
        wireSeek(seekCaustic) {
            hero.setCausticIntensity(
                GlassPlaygroundMappings.causticFromProgress(
                    it
                )
            )
        }
        wireSeek(seekTrans) {
            hero.setTransmittance(
                GlassPlaygroundMappings.transmittanceFromProgress(
                    it
                )
            )
        }
        wireSeek(seekShSoft) {
            hero.setShadowProperties(
                Color.argb(
                    GlassPlaygroundMappings.shadowAlphaFromProgress(seekShAlpha.getValue().toInt()),
                    255,
                    255,
                    255
                ),
                GlassPlaygroundMappings.shadowSoftFromProgress(it)
            )
        }
        wireSeek(seekShAlpha) {
            hero.setShadowProperties(
                Color.argb(
                    GlassPlaygroundMappings.shadowAlphaFromProgress(it),
                    255,
                    255,
                    255
                ),
                GlassPlaygroundMappings.shadowSoftFromProgress(seekShSoft.getValue().toInt())
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
            pBlur = a[0],
            pHeight = a[1],
            pLens = a[2],
            pChroma = a[3],
            pCorner = a[4],
            pDome = a[5],
            pFresnel = a[6],
            pIor = a[7],
            pThick = a[8],
            pNormal = a[9],
            pDisp = a[10],
            pSmooth = a[11],
            pHi = a[12],
            pBright = a[13],
            pInset = a[14],
            pEdge = a[15],
            pLx = a[16],
            pLy = a[17],
            pSpec = a[18],
            pShine = a[19],
            pRim = a[20],
            pDr = a[21],
            pDb = a[22],
            pCaustic = a[23],
            pTrans = a[24],
            pShSoft = a[25],
            pShAlpha = a[26],
            showNormals = switchShowNormals.isChecked,
            downsampleMode = selectedDownsampleMode()
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
