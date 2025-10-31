package com.matrix.prismal

import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap

class PrismalFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), View.OnTouchListener {

    private val glSurface: GLSurfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        setZOrderMediaOverlay(true)
    }
    private val renderer: PrismalGlassRenderer = PrismalGlassRenderer(context)
    private var lastWidth = 0
    private var lastHeight = 0
    private var captureScheduled = false
    private var debug = false

    // Scroll listener to detect changes
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        scheduleCaptureBackground()
    }

    private val drawListener = ViewTreeObserver.OnDrawListener {
        scheduleCaptureBackground()
    }

    init {
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glSurface.setOnTouchListener(this)

        addView(glSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        isChildrenDrawingOrderEnabled = true

        viewTreeObserver.addOnGlobalLayoutListener {
            if (width != lastWidth || height != lastHeight) {
                lastWidth = width
                lastHeight = height
                glSurface.queueEvent {
                    renderer.setGlassSize(width.toFloat(), height.toFloat())
                }
                scheduleCaptureBackground()
            }
        }

        viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        viewTreeObserver.addOnDrawListener(drawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        viewTreeObserver.removeOnDrawListener(drawListener)
    }

    private fun scheduleCaptureBackground() {
        if (!captureScheduled) {
            captureScheduled = true
            post {
                captureScheduled = false
                captureAndSetBackground()
            }
        }
    }

    private fun captureAndSetBackground() {
        if (width <= 0 || height <= 0) return

        try {
            val root = rootView ?: return

            val fullBitmap = createBitmap(root.width, root.height)
            val canvas = Canvas(fullBitmap)

            val wasVisible = visibility
            visibility = INVISIBLE

            canvas.drawColor(Color.WHITE)
            root.draw(canvas)

            visibility = wasVisible

            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            val btnLoc = IntArray(2)
            getLocationOnScreen(btnLoc)

            val cropX = (btnLoc[0] - loc[0]).coerceAtLeast(0)
            val cropY = (btnLoc[1] - loc[1]).coerceAtLeast(0)
            val cropW = width.coerceAtMost(fullBitmap.width - cropX)
            val cropH = height.coerceAtMost(fullBitmap.height - cropY)

            if (cropW > 0 && cropH > 0) {
                val cropped = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
                glSurface.queueEvent { renderer.setBackgroundTexture(cropped) }
            }

            fullBitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateBackground() {
        scheduleCaptureBackground()
    }

    fun setRefractionInset(value: Float) = glSurface.queueEvent { renderer.setRefractionInset(value) }
    fun setGlassSize(width: Float, height: Float) = glSurface.queueEvent { renderer.setGlassSize(width, height) }
    fun setCornerRadius(radius: Float) = glSurface.queueEvent { renderer.setCornerRadius(radius) }
    fun setIOR(value: Float) = glSurface.queueEvent { renderer.setIOR(value) }
    fun setThickness(value: Float) = glSurface.queueEvent { renderer.setGlassThickness(value) }
    fun setNormalStrength(value: Float) = glSurface.queueEvent { renderer.setNormalStrength(value) }
    fun setDisplacementScale(value: Float) = glSurface.queueEvent { renderer.setDisplacementScale(value) }
    fun setHeightBlurFactor(value: Float) = glSurface.queueEvent { renderer.setHeightBlurFactor(value) }
    fun setMinSmoothing(value: Float) = glSurface.queueEvent { renderer.setSminSmoothing(value) }
    fun setBlurRadius(value: Float) = glSurface.queueEvent { renderer.setBlurRadius(value) }
    fun setHighlightWidth(value: Float) = glSurface.queueEvent { renderer.setHighlightWidth(value) }
    fun setChromaticAberration(value: Float) = glSurface.queueEvent { renderer.setChromaticAberration(value) }
    fun setBrightness(value: Float) = glSurface.queueEvent { renderer.setBrightness(value) }
    fun setShowNormals(show: Boolean) = glSurface.queueEvent { renderer.setShowNormals(show) }
    fun setShadowProperties(color: Int, softness: Float) {
        glSurface.queueEvent { renderer.setShadowProperties(color, softness) }
    }
    fun setEdgeRefractionFalloff(value: Float) = glSurface.queueEvent { renderer.setEdgeRefractionFalloff(value) }

    fun setDebug(value: Boolean) {
        debug = value
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (debug)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    glSurface.queueEvent {
                        renderer.setTouching(true)
                        renderer.setMousePosition(event.x, event.y)
                    }
                }

                MotionEvent.ACTION_MOVE -> glSurface.queueEvent {
                    renderer.setMousePosition(event.x, event.y)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> glSurface.queueEvent {
                    renderer.setTouching(false)
                }
            }
        return true
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        return if (i == 0) 0 else i
    }
}