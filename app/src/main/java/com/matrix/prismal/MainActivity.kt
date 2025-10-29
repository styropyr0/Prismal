package com.matrix.prismal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var rootLayout: FrameLayout
    private lateinit var liquidButton: LiquidGlassButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        liquidButton = findViewById(R.id.liquidButton)

        val normalMap = BitmapFactory.decodeResource(resources, R.drawable.normal_map)
        liquidButton.setNormalMap(normalMap)

        liquidButton.post {
            captureBackgroundAndUpload()
        }
    }

    private fun captureBackgroundAndUpload() {
        if (!::liquidButton.isInitialized || liquidButton.width == 0) return

        val bmp = Bitmap.createBitmap(liquidButton.width, liquidButton.height, Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-liquidButton.left.toFloat(), -liquidButton.top.toFloat())
        rootLayout.draw(canvas)

        liquidButton.queueEvent {
            liquidButton.setBackgroundBitmap(bmp)
        }
        liquidButton.requestRender()
    }
}
