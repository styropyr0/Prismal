package com.matrix.prismaltest

import android.os.Bundle
import android.graphics.Color
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalLiquidGlass

/**
 * Full glass playground: every tunable [PrismalFrameLayout] / renderer parameter with live sliders.
 * Persists to [GlassPlaygroundPrefs] and applies on home / drag-show layouts via [GlassPlaygroundPrefs.applyTo].
 */
class GlassPlaygroundActivity : AppCompatActivity() {

    private lateinit var hero: PrismalFrameLayout
    private lateinit var bars: List<SeekBar>
    private lateinit var switchShowNormals: SwitchCompat
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
            val bgResId = intent.getIntExtra("BACKGROUND_RES_ID", R.drawable.bg4)
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
        fun wireSeek(bar: SeekBar, on: (Int) -> Unit) {
            tmp.add(on)
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    on(progress)
                    hero.updateBackground()
                    persistFromUi()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val seekBlur = findViewById<SeekBar>(R.id.seekBlur)
        val seekHeight = findViewById<SeekBar>(R.id.seekRefractionHeight).apply { max = 200 }
        val seekLens = findViewById<SeekBar>(R.id.seekRefractionAmount)
        val seekChroma = findViewById<SeekBar>(R.id.seekChromatic)
        val seekCorner = findViewById<SeekBar>(R.id.seekCorner)
        val seekDome = findViewById<SeekBar>(R.id.seekLiquidDome)
        val seekFresnel = findViewById<SeekBar>(R.id.seekFresnel)
        val seekIor = findViewById<SeekBar>(R.id.seekIor)
        val seekThickness = findViewById<SeekBar>(R.id.seekThickness)
        val seekNormal = findViewById<SeekBar>(R.id.seekNormalStrength)
        val seekDisp = findViewById<SeekBar>(R.id.seekDisplacement)
        val seekSmooth = findViewById<SeekBar>(R.id.seekMinSmooth)
        val seekHi = findViewById<SeekBar>(R.id.seekHighlightWidth)
        val seekBright = findViewById<SeekBar>(R.id.seekBrightness)
        val seekInset = findViewById<SeekBar>(R.id.seekRefractionInset)
        val seekEdge = findViewById<SeekBar>(R.id.seekEdgeFalloff)
        val seekLx = findViewById<SeekBar>(R.id.seekLightX)
        val seekLy = findViewById<SeekBar>(R.id.seekLightY)
        val seekSpec = findViewById<SeekBar>(R.id.seekSpecular)
        val seekShine = findViewById<SeekBar>(R.id.seekShininess)
        val seekRim = findViewById<SeekBar>(R.id.seekRim)
        val seekDr = findViewById<SeekBar>(R.id.seekDispersionR)
        val seekDb = findViewById<SeekBar>(R.id.seekDispersionB)
        val seekCaustic = findViewById<SeekBar>(R.id.seekCaustic)
        val seekTrans = findViewById<SeekBar>(R.id.seekTransmittance)
        val seekShSoft = findViewById<SeekBar>(R.id.seekShadowSoft)
        val seekShAlpha = findViewById<SeekBar>(R.id.seekShadowAlpha)

        wireSeek(seekBlur) { hero.setBlurRadius(GlassPlaygroundMappings.blurFromProgress(it)) }
        wireSeek(seekHeight) { hero.setHeightBlurFactor(GlassPlaygroundMappings.heightBlurFromProgress(it)) }
        wireSeek(seekLens) { hero.setLensRefractionScale(GlassPlaygroundMappings.lensScaleFromProgress(it)) }
        wireSeek(seekChroma) { hero.setChromaticAberration(GlassPlaygroundMappings.chromaFromProgress(it)) }
        wireSeek(seekCorner) { hero.setCornerRadius(dp(GlassPlaygroundMappings.cornerDpFromProgress(it))) }
        wireSeek(seekDome) { hero.setLiquidDomeStrength(GlassPlaygroundMappings.domeFromProgress(it)) }
        wireSeek(seekFresnel) { hero.setFresnelReflectStrength(GlassPlaygroundMappings.fresnelFromProgress(it)) }
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
        wireSeek(seekNormal) { hero.setNormalStrength(GlassPlaygroundMappings.normalStrengthFromProgress(it)) }
        wireSeek(seekDisp) { hero.setDisplacementScale(GlassPlaygroundMappings.displacementFromProgress(it)) }
        wireSeek(seekSmooth) { hero.setMinSmoothing(GlassPlaygroundMappings.minSmoothingFromProgress(it)) }
        wireSeek(seekHi) { hero.setHighlightWidth(GlassPlaygroundMappings.highlightWidthFromProgress(it)) }
        wireSeek(seekBright) { hero.setBrightness(GlassPlaygroundMappings.brightnessFromProgress(it)) }
        wireSeek(seekInset) {
            hero.setRefractionInset(
                dp(GlassPlaygroundMappings.refractionInsetDpFromProgress(it))
            )
        }
        wireSeek(seekEdge) { hero.setEdgeRefractionFalloff(GlassPlaygroundMappings.edgeFalloffFromProgress(it)) }
        wireSeek(seekLx) { lx ->
            val ly = GlassPlaygroundMappings.lightYFromProgress(seekLy.progress)
            hero.setLightDirection(GlassPlaygroundMappings.lightXFromProgress(lx), ly)
        }
        wireSeek(seekLy) { ly ->
            val lx = GlassPlaygroundMappings.lightXFromProgress(seekLx.progress)
            hero.setLightDirection(lx, GlassPlaygroundMappings.lightYFromProgress(ly))
        }
        wireSeek(seekSpec) {
            hero.setSpecular(
                GlassPlaygroundMappings.specularFromProgress(it),
                GlassPlaygroundMappings.shininessFromProgress(seekShine.progress)
            )
        }
        wireSeek(seekShine) {
            hero.setSpecular(
                GlassPlaygroundMappings.specularFromProgress(seekSpec.progress),
                GlassPlaygroundMappings.shininessFromProgress(it)
            )
        }
        wireSeek(seekRim) { hero.setRimStrength(GlassPlaygroundMappings.rimFromProgress(it)) }
        wireSeek(seekDr) {
            hero.setDispersion(
                GlassPlaygroundMappings.dispersionChannelFromProgress(it),
                GlassPlaygroundMappings.dispersionChannelFromProgress(seekDb.progress)
            )
        }
        wireSeek(seekDb) {
            hero.setDispersion(
                GlassPlaygroundMappings.dispersionChannelFromProgress(seekDr.progress),
                GlassPlaygroundMappings.dispersionChannelFromProgress(it)
            )
        }
        wireSeek(seekCaustic) { hero.setCausticIntensity(GlassPlaygroundMappings.causticFromProgress(it)) }
        wireSeek(seekTrans) { hero.setTransmittance(GlassPlaygroundMappings.transmittanceFromProgress(it)) }
        wireSeek(seekShSoft) {
            hero.setShadowProperties(
                Color.argb(
                    GlassPlaygroundMappings.shadowAlphaFromProgress(seekShAlpha.progress),
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
                GlassPlaygroundMappings.shadowSoftFromProgress(seekShSoft.progress)
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

        GlassPlaygroundPrefs.load(this)?.let { saved ->
            val prog = GlassPlaygroundPrefs.seekProgressFromParams(saved)
            for (i in bars.indices) {
                bars[i].progress = prog[i]
            }
            switchShowNormals.isChecked = saved.showNormals
        }

        for (i in bars.indices) {
            updates[i](bars[i].progress)
        }
        switchShowNormals.isChecked.let { hero.setShowNormals(it) }

        hero.post { hero.updateBackground() }
        persistFromUi()
    }

    private fun persistFromUi() {
        val a = IntArray(bars.size) { bars[it].progress }
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
            showNormals = switchShowNormals.isChecked
        )
        GlassPlaygroundPrefs.save(this, p)
    }
}
