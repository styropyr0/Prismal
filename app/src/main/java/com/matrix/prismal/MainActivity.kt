package com.matrix.prismal

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var liquidButton: PrismalFrameLayout
    private lateinit var liquidSlider: PrismalSlider
    private lateinit var rootLayout: FrameLayout
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        label = findViewById(R.id.txt)
        liquidSlider = findViewById(R.id.liquidSlider)
        liquidButton = findViewById(R.id.liquidButton)
        liquidButton.apply {
            setCornerRadius(200f)

            // Optics
            setIOR(1.55f)
            setThickness(40f)
            setNormalStrength(10f)
            setMinSmoothing(10f)

            setDisplacementScale(10f)
            // Edge‑blend
            setRefractionInset(100f)
            setHeightBlurFactor(30f)

            // Post‑effects
            setBlurRadius(3f)
            setChromaticAberration(20f)
            setBrightness(1.3f)

            // Debug
            setShowNormals(false)
        }
    }
}