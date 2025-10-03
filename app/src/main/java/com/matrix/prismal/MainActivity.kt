package com.matrix.prismal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var liquidButton: LiquidGlassButton
    private lateinit var rootLayout: FrameLayout
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.root)
        label = findViewById(R.id.txt)
        liquidButton = findViewById(R.id.liquidButton)

        val normalMap = BitmapFactory.decodeResource(resources, R.drawable.normal_map)
        liquidButton.setNormalMap(normalMap)

        liquidButton.post {
            if (liquidButton.width > 0 && liquidButton.height > 0)
            captureBackground()
        }

        liquidButton.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Toast.makeText(this, "Button Pressed!", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }


    private fun captureBackground() {
        val bmp = Bitmap.createBitmap(
            liquidButton.width,
            liquidButton.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bmp)
        canvas.translate(-liquidButton.left.toFloat(), -liquidButton.top.toFloat())

        rootLayout.draw(canvas)

        liquidButton.setBackgroundBitmap(bmp)
    }
}
