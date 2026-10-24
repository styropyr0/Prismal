package com.matrix.prismaltest

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.matrix.prismal.PrismalFrameLayout
import com.matrix.prismal.PrismalLiquidGlass
import com.matrix.prismal.PrismalSlider
import com.matrix.prismal.PrismalSwitch
import com.matrix.prismal.PrismalTabBar

class DragShowActivity : AppCompatActivity() {

    private lateinit var prismalLayout: PrismalFrameLayout
    private lateinit var shapes: List<PrismalFrameLayout>
    private lateinit var slider: PrismalSlider
    private lateinit var switch: PrismalSwitch
    private lateinit var tabBar: PrismalTabBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.drag_show_layout)

        val parent = findViewById<FrameLayout>(R.id.parentContainer)

        intent.getStringExtra("BACKGROUND_URI")?.let { uriString ->
            val uri = uriString.toUri()
            parent.background = contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.drawable.Drawable.createFromStream(input, uriString)
            }
        } ?: run {
            val bgResId = intent.getIntExtra("BACKGROUND_RES_ID", -1)
            if (bgResId != -1) parent.setBackgroundResource(bgResId)
        }

        val container = findViewById<FrameLayout>(R.id.draggableContainer)
        prismalLayout = findViewById(R.id.prismalFrame4)
        val circle = findViewById<PrismalFrameLayout>(R.id.glassCircle)
        val pill = findViewById<PrismalFrameLayout>(R.id.glassPill)
        val square = findViewById<PrismalFrameLayout>(R.id.glassSquare)
        shapes = listOf(circle, pill, square)
        slider = findViewById(R.id.dragSlider)
        switch = findViewById(R.id.dragSwitch)
        tabBar = findViewById(R.id.dragTabBar)

        GlassPlaygroundPrefs.applyTo(this, prismalLayout)
        configureShape(circle, 110f, 55f)
        configureShape(pill, 68f, 34f)
        configureShape(square, 120f, 32f)
        slider.setValue(40f)

        for (v in listOf(prismalLayout) + shapes) makeDraggable(v, container)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun configureShape(f: PrismalFrameLayout, minDimDp: Float, cornerDp: Float) {
        PrismalLiquidGlass.applyBase(f)
        f.setCornerRadius(dp(cornerDp))
        f.setThickness(dp((minDimDp * 0.06f).coerceIn(3f, 8f)))
        f.setHeightBlurFactor(dp((minDimDp * 0.18f).coerceIn(8f, 20f)))
        f.setBlurRadius(3f)
        f.setChromaticAberration(2f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(v: PrismalFrameLayout, container: FrameLayout) {
        var dX = 0f
        var dY = 0f
        v.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2.5f))
                        .setUpdateListener { v.updateBackground() }.start()
                }

                MotionEvent.ACTION_MOVE -> {
                    v.x = (event.rawX + dX).coerceIn(0f, container.width - v.width.toFloat())
                    v.y = (event.rawY + dY).coerceIn(0f, container.height - v.height.toFloat())
                    v.updateBackground()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(OvershootInterpolator(2f))
                        .setUpdateListener { v.updateBackground() }.start()
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        GlassPlaygroundPrefs.applyTo(this, prismalLayout)
        prismalLayout.post {
            prismalLayout.updateBackground()
            shapes.forEach { it.updateBackground() }
            tabBar.updateBackground()
        }
    }
}
