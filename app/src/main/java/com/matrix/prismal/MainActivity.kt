package com.matrix.prismal

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var liquidButton: PrismalFrameLayout
    private lateinit var rootLayout: FrameLayout
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        label = findViewById(R.id.txt)
        liquidButton = findViewById(R.id.liquidButton)
        liquidButton.apply {
            // Size & shape
            setGlassSize(1100f, 300f)
            setCornerRadius(2f)

            // Optics
            setIOR(1f)
            setThickness(100f)
            setNormalStrength(40f)
            setMinSmoothing(100f)
            setDisplacementScale(30f)
            setRefractionInset(20f)
            setHeightBlurFactor(5f)

            // Post‑effects
            setBlurRadius(6f)
            setChromaticAberration(4f)
            setBrightness(1.3f)

            // Debug
            setShowNormals(false)
        }
    }
}