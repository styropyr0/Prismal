package com.matrix.prismal

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var liquidButton: PrismalButton
    private lateinit var rootLayout: FrameLayout
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        label = findViewById(R.id.txt)
        liquidButton = findViewById(R.id.liquidButton)

        liquidButton.apply {
            setGlassSize(800f, 300f)
            setCornerRadius(24f)
            setIOR(1.8f)
            setThickness(15f)
            setNormalStrength(2f)
            setBlurRadius(1.5f)
            setChromaticAberration(6f)
            setBrightness(1.05f)
            setShowNormals(false)
        }
    }
}