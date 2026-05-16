package com.matrix.prismaltest

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.matrix.prismal.PrismalFrameLayout
import androidx.core.net.toUri

class DragShowActivity : AppCompatActivity() {

    private lateinit var prismalLayout: PrismalFrameLayout

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.drag_show_layout)

        val parent = findViewById<LinearLayout>(R.id.parentContainer)

        intent.getStringExtra("BACKGROUND_URI")?.let { uriString ->
            val uri = uriString.toUri()
            parent.background = contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.drawable.Drawable.createFromStream(input, uriString)
            }
        } ?: run {
            val bgResId = intent.getIntExtra("BACKGROUND_RES_ID", -1)
            if (bgResId != -1) parent.setBackgroundResource(bgResId)
        }

        val draggableLayout = findViewById<LinearLayout>(R.id.draggableLayout)
        val container = findViewById<FrameLayout>(R.id.draggableContainer)
        prismalLayout = findViewById(R.id.prismalFrame4)

        GlassPlaygroundPrefs.applyTo(this, prismalLayout)

        var dX = 0f
        var dY = 0f

        draggableLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    var newX = event.rawX + dX
                    var newY = event.rawY + dY

                    newX = newX.coerceIn(0f, container.width - v.width.toFloat())
                    newY = newY.coerceIn(0f, container.height - v.height.toFloat())

                    v.animate().x(newX).y(newY).setDuration(0).start().also {
                        prismalLayout.updateBackground()
                    }
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        GlassPlaygroundPrefs.applyTo(this, prismalLayout)
        prismalLayout.post { prismalLayout.updateBackground() }
    }
}
