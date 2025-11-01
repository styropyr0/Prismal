package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap

/**
 * A custom FrameLayout that renders a realistic glass overlay effect using OpenGL ES 2.0.
 * This view captures the underlying content as a texture and applies refraction, blur, displacement,
 * and other glass-like distortions via shaders. It supports touch interactions for dynamic effects
 * like ripples or distortions under the finger.
 *
 * ### Key Features:
 * - **Real-time background capture**: Automatically captures and updates the texture from the view hierarchy
 *   beneath it (e.g., on layout changes or scrolls).
 * - **Customizable glass properties**: Adjust IOR, thickness, corner radius, blur, chromatic aberration,
 *   and more via public setter methods.
 * - **Touch handling**: Responds to touch events to simulate interactive distortions.
 * - **Performance notes**: Uses `RENDERMODE_CONTINUOUSLY` for smooth animations; consider throttling
 *   background captures for large hierarchies to avoid jank.
 *
 * ### Usage:
 * Add to your layout XML as `<com.matrix.prismal.PrismalFrameLayout>`, and place child views (e.g., buttons or text)
 * inside it for overlay rendering. The glass effect will be drawn on top of captured background.
 *
 * ### Example:
 * ```
 * <com.matrix.prismal.PrismalFrameLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="60dp">
 *     <TextView android:text="Press Me" ... />
 * </com.matrix.prismal.PrismalFrameLayout>
 * ```
 *
 * @see [setRefractionInset] for edge refraction control
 * @see [setIOR] for material realism
 * @see [updateBackground] to manually refresh the captured texture
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
open class PrismalFrameLayout @JvmOverloads constructor(
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
    private var lastCaptureTime = 0L
    private val minCaptureInterval = 50L

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        scheduleCaptureBackground()
    }

    init {
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glSurface.setOnTouchListener(this)

        context.theme.obtainStyledAttributes(attrs, R.styleable.PrismalFrameLayout, 0, 0).apply {
            try {
                getFloat(R.styleable.PrismalFrameLayout_glassWidth, -1f).takeIf { it > 0 }
                    ?.let { w ->
                        getFloat(
                            R.styleable.PrismalFrameLayout_glassHeight,
                            -1f
                        ).takeIf { it > 0 }?.let { h -> setGlassSize(w, h) }
                    }
                setIOR(getFloat(R.styleable.PrismalFrameLayout_ior, 1.5f))
                setThickness(getDimension(R.styleable.PrismalFrameLayout_glassThickness, 15f))
                setNormalStrength(getFloat(R.styleable.PrismalFrameLayout_normalStrength, 1.2f))
                setDisplacementScale(getFloat(R.styleable.PrismalFrameLayout_displacementScale, 1.0f))
                setHeightBlurFactor(getFloat(R.styleable.PrismalFrameLayout_heightTransitionWidth, 8f))
                setMinSmoothing(getFloat(R.styleable.PrismalFrameLayout_minSmoothing, 1.0f))
                setBlurRadius(getFloat(R.styleable.PrismalFrameLayout_blurRadius, 2.5f))
                setHighlightWidth(getFloat(R.styleable.PrismalFrameLayout_highlightWidth, 4.0f))
                setChromaticAberration(getFloat(R.styleable.PrismalFrameLayout_chromaticAberration, 2.0f))
                setBrightness(getFloat(R.styleable.PrismalFrameLayout_brightness, 1.15f))
                setShowNormals(getBoolean(R.styleable.PrismalFrameLayout_showNormals, false))
                setCornerRadius(getDimension(R.styleable.PrismalFrameLayout_cornerRadius, 10f))
            } finally {
                recycle()
            }
        }
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
    }

    private fun scheduleCaptureBackground() {
        if (!captureScheduled) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCaptureTime

            if (elapsed < minCaptureInterval) {
                postDelayed({
                    captureScheduled = false
                    scheduleCaptureBackground()
                }, minCaptureInterval - elapsed)
                captureScheduled = true
            } else {
                captureScheduled = true
                post {
                    captureScheduled = false
                    captureAndSetBackground()
                    lastCaptureTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun captureAndSetBackground() {
        if (width <= 0 || height <= 0) return

        try {
            val root = rootView ?: return

            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            val btnLoc = IntArray(2)
            getLocationOnScreen(btnLoc)

            val cropX = (btnLoc[0] - loc[0]).coerceAtLeast(0)
            val cropY = (btnLoc[1] - loc[1]).coerceAtLeast(0)
            val cropW = width.coerceAtMost(root.width - cropX)
            val cropH = height.coerceAtMost(root.height - cropY)

            if (cropW <= 0 || cropH <= 0) return

            val croppedBitmap = createBitmap(cropW, cropH)
            val canvas = Canvas(croppedBitmap)
            canvas.translate(-cropX.toFloat(), -cropY.toFloat())

            val wasVisible = visibility
            visibility = INVISIBLE

            canvas.drawColor(Color.WHITE)
            root.draw(canvas)

            visibility = wasVisible

            glSurface.queueEvent { renderer.setBackgroundTexture(croppedBitmap) }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Triggers an update to the background texture by scheduling a capture of the underlying view hierarchy.
     * This is useful for refreshing the glass effect when the content beneath changes (e.g., after scrolling or layout updates).
     */
    fun updateBackground() = scheduleCaptureBackground()

    /**
     * Sets the refraction inset value, which controls the distance from the edge where refraction effects are applied.
     *
     * @param value The inset value in pixels (default: 20f).
     */
    fun setRefractionInset(value: Float) = glSurface.queueEvent { renderer.setRefractionInset(value) }

    /**
     * Sets the size of the glass effect area.
     *
     * @param width The width of the glass in pixels.
     * @param height The height of the glass in pixels.
     */
    fun setGlassSize(width: Float, height: Float) = glSurface.queueEvent { renderer.setGlassSize(width, height) }

    /**
     * Sets the corner radius for rounded corners on the glass effect.
     *
     * @param radius The radius in pixels (default: 30f).
     */
    fun setCornerRadius(radius: Float) = glSurface.queueEvent { renderer.setCornerRadius(radius) }

    /**
     * Sets the Index of Refraction (IOR) for the glass material, affecting light bending.
     *
     * @param value The IOR value (typical range: 1.3-1.6 for glass; default: 1.5f).
     */
    fun setIOR(value: Float) = glSurface.queueEvent { renderer.setIOR(value) }

    /**
     * Sets the thickness of the glass, influencing distortion and depth effects.
     *
     * @param value The thickness in pixels (default: 15f).
     */
    fun setThickness(value: Float) = glSurface.queueEvent { renderer.setGlassThickness(value) }

    /**
     * Sets the strength of the normal mapping, controlling surface bumpiness and light interaction.
     *
     * @param value The strength multiplier (default: 1.2f).
     */
    fun setNormalStrength(value: Float) = glSurface.queueEvent { renderer.setNormalStrength(value) }

    /**
     * Sets the scale for displacement mapping, which warps the texture based on height data.
     *
     * @param value The displacement scale multiplier (default: 1.0f).
     */
    fun setDisplacementScale(value: Float) = glSurface.queueEvent { renderer.setDisplacementScale(value) }

    /**
     * Sets the blur factor applied based on the simulated glass height/depth.
     *
     * @param value The height-to-blur scaling factor (default: 8f).
     */
    fun setHeightBlurFactor(value: Float) = glSurface.queueEvent { renderer.setHeightBlurFactor(value) }

    /**
     * Sets the minimum smoothing value for SDF (Signed Distance Field) operations in the shader.
     *
     * @param value The smoothing threshold (default: 1.0f).
     */
    fun setMinSmoothing(value: Float) = glSurface.queueEvent { renderer.setSminSmoothing(value) }

    /**
     * Sets the radius for the overall blur effect in the glass rendering.
     *
     * @param value The blur radius in pixels (default: 2.5f).
     */
    fun setBlurRadius(value: Float) = glSurface.queueEvent { renderer.setBlurRadius(value) }

    /**
     * Sets the width of highlight edges in the glass effect.
     *
     * @param value The highlight width in pixels (default: 4.0f).
     */
    fun setHighlightWidth(value: Float) = glSurface.queueEvent { renderer.setHighlightWidth(value) }

    /**
     * Sets the intensity of chromatic aberration, simulating color fringing at edges.
     *
     * @param value The aberration strength (default: 2.0f).
     */
    fun setChromaticAberration(value: Float) = glSurface.queueEvent { renderer.setChromaticAberration(value) }

    /**
     * Sets the overall brightness multiplier for the glass effect.
     *
     * @param value The brightness factor (default: 1.15f).
     */
    fun setBrightness(value: Float) = glSurface.queueEvent { renderer.setBrightness(value) }

    /**
     * Toggles the display of normal vectors for debugging the surface normals.
     *
     * @param show True to show normals, false to hide (default: false).
     */
    fun setShowNormals(show: Boolean) = glSurface.queueEvent { renderer.setShowNormals(show) }

    /**
     * Sets the shadow properties for the glass effect, including color and softness.
     *
     * @param color The shadow color as an Android ColorInt (ARGB).
     * @param softness The softness/feathering of the shadow (0.0-1.0; default: 0.2f).
     */
    fun setShadowProperties(color: Int, softness: Float) {
        glSurface.queueEvent { renderer.setShadowProperties(color, softness) }
    }

    /**
     * Sets the falloff rate for refraction effects at the edges of the glass.
     *
     * @param value The falloff sharpness (higher values = sharper transition; default: 4f).
     */
    fun setEdgeRefractionFalloff(value: Float) = glSurface.queueEvent { renderer.setEdgeRefractionFalloff(value) }

    /**
     * Enables or disables debug mode for the glass renderer (e.g., visualizing internals).
     *
     * @param value True to enable debug visuals, false to disable.
     */
    fun setDebug(value: Boolean) {
        debug = value
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (!debug) return false

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