package com.matrix.prismal

import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var rootScrollListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var pendingBitmap: Bitmap? = null
    private var isCapturing = false
    private var captureScheduled = false

    init {
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glSurface.setOnTouchListener(this)

        addView(glSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        isChildrenDrawingOrderEnabled = true

        setupListeners()
    }

    private fun setupListeners() {
        // Listen for layout changes
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (width > 0 && height > 0) {
                if (width != lastWidth || height != lastHeight) {
                    lastWidth = width
                    lastHeight = height
                    updateGlassSize()
                    scheduleCaptureBackground()
                }
            }
        }

        // Listen for draw events (content updates)
        drawListener = ViewTreeObserver.OnDrawListener {
            if (!isCapturing && width > 0 && height > 0) {
                scheduleCaptureBackground()
            }
        }

        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnDrawListener(drawListener)
    }

    private fun attachRootScrollListener() {
        rootView?.viewTreeObserver?.let { observer ->
            rootScrollListener = ViewTreeObserver.OnScrollChangedListener {
                if (width > 0 && height > 0) {
                    scheduleCaptureBackground()
                }
            }
            observer.addOnScrollChangedListener(rootScrollListener)
        }
    }

    private fun detachRootScrollListener() {
        rootScrollListener?.let { listener ->
            rootView?.viewTreeObserver?.removeOnScrollChangedListener(listener)
            rootScrollListener = null
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            updateGlassSize()
            scheduleCaptureBackground()
        }
    }

    private fun updateGlassSize() {
        glSurface.queueEvent {
            renderer.setGlassSize(width.toFloat(), height.toFloat())
        }
    }

    private fun scheduleCaptureBackground() {
        if (captureScheduled) return
        captureScheduled = true

        // Debounce rapid updates
        postDelayed({
            captureScheduled = false
            captureAndSetBackground()
        }, 16) // ~1 frame delay
    }

    private fun captureAndSetBackground() {
        if (width <= 0 || height <= 0 || isCapturing) return

        isCapturing = true

        try {
            val root = rootView ?: return

            // Ensure we're capturing after the layout is stable
            if (!root.isLaidOut) {
                isCapturing = false
                postDelayed({ captureAndSetBackground() }, 50)
                return
            }

            val fullBitmap = createBitmap(root.width, root.height)
            val canvas = Canvas(fullBitmap)

            // Draw the actual background
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val window = (context as? android.app.Activity)?.window
            val decorView = window?.decorView

            // Draw the background from the decor view
            decorView?.background?.let { bg ->
                bg.setBounds(0, 0, canvas.width, canvas.height)
                bg.draw(canvas)
            }

            // Draw all views except this one
            drawViewHierarchyExcludingThis(root, canvas)

            // Get locations for cropping
            val rootLoc = IntArray(2)
            root.getLocationInWindow(rootLoc)
            val thisLoc = IntArray(2)
            getLocationInWindow(thisLoc)

            val cropX = (thisLoc[0] - rootLoc[0]).coerceAtLeast(0)
            val cropY = (thisLoc[1] - rootLoc[1]).coerceAtLeast(0)
            val cropW = width.coerceAtMost(fullBitmap.width - cropX)
            val cropH = height.coerceAtMost(fullBitmap.height - cropY)

            if (cropW > 0 && cropH > 0) {
                val cropped = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)

                // Clean up old pending bitmap
                synchronized(this) {
                    pendingBitmap?.recycle()
                    pendingBitmap = cropped
                }

                // Queue the bitmap to be loaded on GL thread
                glSurface.queueEvent {
                    synchronized(this) {
                        pendingBitmap?.let { bitmap ->
                            if (!bitmap.isRecycled) {
                                renderer.setBackgroundTexture(bitmap)
                            }
                            bitmap.recycle()
                            pendingBitmap = null
                        }
                    }
                }
            }

            fullBitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isCapturing = false
        }
    }

    private fun drawViewHierarchyExcludingThis(view: View, canvas: Canvas) {
        if (view == this) {
            // Skip drawing this view entirely
            return
        }

        if (view is ViewGroup) {
            canvas.save()

            // Draw the view's background
            view.background?.let { bg ->
                bg.setBounds(0, 0, view.width, view.height)
                bg.draw(canvas)
            }

            // Draw children
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.visibility == View.VISIBLE) {
                    canvas.save()
                    canvas.translate(child.left.toFloat(), child.top.toFloat())

                    // Apply transformations
                    canvas.translate(child.translationX, child.translationY)
                    canvas.scale(child.scaleX, child.scaleY, child.pivotX, child.pivotY)
                    canvas.rotate(child.rotation, child.pivotX, child.pivotY)

                    drawViewHierarchyExcludingThis(child, canvas)
                    canvas.restore()
                }
            }
            canvas.restore()
        } else {
            // Draw leaf view
            canvas.save()
            view.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Call this to manually trigger a background recapture when content changes
     */
    fun requestBackgroundUpdate() {
        scheduleCaptureBackground()
    }

    /**
     * Enable or disable automatic background updates
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        if (enabled) {
            if (drawListener == null) {
                drawListener = ViewTreeObserver.OnDrawListener {
                    if (!isCapturing && width > 0 && height > 0) {
                        scheduleCaptureBackground()
                    }
                }
                viewTreeObserver.addOnDrawListener(drawListener)
            }
        } else {
            drawListener?.let {
                viewTreeObserver.removeOnDrawListener(it)
                drawListener = null
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachRootScrollListener()
        postDelayed({
            updateGlassSize()
            captureAndSetBackground()
        }, 100) // Give time for background to be set
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        layoutListener?.let { viewTreeObserver.removeOnGlobalLayoutListener(it) }
        drawListener?.let { viewTreeObserver.removeOnDrawListener(it) }
        detachRootScrollListener()

        // Clean up any pending bitmap
        synchronized(this) {
            pendingBitmap?.recycle()
            pendingBitmap = null
        }
    }

    fun setRefractionInset(value: Float) = glSurface.queueEvent { renderer.setRefractionInset(value) }
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
    fun setShadowProperties(color: Int, offset: FloatArray, softness: Float) {
        glSurface.queueEvent { renderer.setShadowProperties(color, softness) }
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
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
        return if (i == childCount - 1) 0 else i + 1
    }
}