package com.matrix.prismal

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.matrix.prismal.utils.SpringAnimator
import com.matrix.prismal.utils.ThumbShadowView
import com.matrix.prismal.utils.applyThumbLens
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * **PrismalTabBar** - An iOS 26 style liquid-glass tab bar.
 *
 * A 64 dp frosted glass capsule holding the tabs, with a selection pill behind the
 * current tab. Pressing the pill turns it into a clear glass lens that can be dragged
 * between tabs; tapping another tab slides the lens across. Content under the lens is
 * drawn in the tint colour, as on iOS.
 *
 * ### Usage Example
 * ```kotlin
 * val tabs = PrismalTabBar(context).apply {
 *     addTab(R.drawable.ic_home, "Home")
 *     addTab(R.drawable.ic_search, "Search")
 *     setOnTabSelectedListener { index -> showPage(index) }
 * }
 * ```
 *
 * XML attributes: `ptb_menu`, `ptb_selectedIndex`, `ptb_tintColor`, `ptb_itemColor`,
 * `ptb_containerColor` (see [R.styleable.PrismalTabBar]).
 *
 * @author Saurav Sajeev
 */
@SuppressLint("ClickableViewAccessibility")
class PrismalTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val barH = dp(64f)
    private val pillH = dp(56f)
    private val pad = dp(4f)
    private val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private var tintColor = if (night) Color.rgb(0x00, 0x91, 0xFF) else Color.rgb(0x00, 0x88, 0xFF)
    private var itemColor = if (night) Color.WHITE else Color.BLACK
    private var containerColor = if (night) Color.argb(102, 0x12, 0x12, 0x12) else Color.argb(102, 0xFA, 0xFA, 0xFA)
    private val restPillColor = if (night) Color.argb(26, 255, 255, 255) else Color.argb(26, 0, 0, 0)

    private var selectedIndex = 0
    private var highlightIndex = 0
    private var onTabSelected: ((Int) -> Unit)? = null

    private class Tab(val view: LinearLayout, val icon: ImageView, val label: TextView)

    private val tabs = ArrayList<Tab>()

    private val bar = PrismalFrameLayout(context)
    private val tint = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            paint.color = containerColor
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, paint)
        }
    }
    private val restPill = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            paint.color = restPillColor
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, paint)
        }
    }
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(pad.toInt(), 0, pad.toInt(), 0)
    }
    private val droplet = PrismalFrameLayout(context)
    private val dropletShadow = ThumbShadowView(context, dp(6f))

    private val valueSpring = SpringAnimator(1f, 1000f)
    private val pressSpring = SpringAnimator(1f, 1000f)
    private val scaleXSpring = SpringAnimator(0.6f, 250f)
    private val scaleYSpring = SpringAnimator(0.7f, 250f)
    private val offsetSpring = SpringAnimator(0.5f, 300f, threshold = 0.5f)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var lastX = 0f
    private var onDroplet = false
    private var dragging = false
    private var pressed = false
    private var releasePending = false
    private var dragValue = 0f
    private var dropletParked = true

    private val loc = IntArray(2)
    private val srcLoc = IntArray(2)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var barSnapshot: Bitmap? = null
    private var spareSnapshot: Bitmap? = null
    private var snapshotInFlight = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint()
    private val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        maskFilter = BlurMaskFilter(dp(2.5f), BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()
    private val clip = Path()
    private val clipRect = RectF()
    private val snapSrc = Rect()
    private val snapRect = RectF()

    private companion object {
        const val PRESSED_SCALE = 78f / 56f
        const val PARKED_Y = -100000f
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun tabWidth() = if (tabs.isEmpty()) 0f else (width - 2f * pad) / tabs.size

    private fun maxValue() = (tabs.size - 1).coerceAtLeast(0).toFloat()

    private fun panelOffset(): Float {
        if (width == 0) return 0f
        val f = (offsetSpring.value / width).coerceIn(-1f, 1f)
        return pad * f.sign * (1f - (1f - abs(f)).pow(2))
    }

    init {
        clipChildren = false
        clipToPadding = false
        minimumHeight = barH.toInt()

        bar.setCornerRadius(barH / 2f)
        bar.addView(tint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        bar.addView(restPill, LayoutParams(0, pillH.toInt()).apply { gravity = Gravity.CENTER_VERTICAL })
        bar.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, pillH.toInt()).apply { gravity = Gravity.CENTER_VERTICAL })
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, barH.toInt()).apply { gravity = Gravity.CENTER_VERTICAL })

        dropletShadow.alpha = 0f
        addView(dropletShadow, LayoutParams(0, pillH.toInt()).apply { gravity = Gravity.CENTER_VERTICAL })
        droplet.setSurfaceOnTop()
        droplet.setBackdropHandledByChild(true)
        droplet.onCaptureDrawn = ::drawTabsIntoDroplet
        droplet.translationY = PARKED_Y
        addView(droplet, LayoutParams(0, pillH.toInt()).apply { gravity = Gravity.CENTER_VERTICAL })

        valueSpring.onUpdate = {
            if (releasePending && abs(it - valueSpring.target) < maxValue().coerceAtLeast(1f) * 0.025f) {
                releasePending = false
                release()
            }
            applyState()
        }
        pressSpring.onUpdate = { applyPress(it); applyState() }
        scaleXSpring.onUpdate = { applyState() }
        scaleYSpring.onUpdate = { applyState() }
        offsetSpring.onUpdate = { applyState() }
        pressSpring.snapTo(0f)
        scaleXSpring.snapTo(1f)
        scaleYSpring.snapTo(1f)

        context.obtainStyledAttributes(attrs, R.styleable.PrismalTabBar).use { a ->
            tintColor = a.getColor(R.styleable.PrismalTabBar_ptb_tintColor, tintColor)
            itemColor = a.getColor(R.styleable.PrismalTabBar_ptb_itemColor, itemColor)
            containerColor = a.getColor(R.styleable.PrismalTabBar_ptb_containerColor, containerColor)
            val menuRes = a.getResourceId(R.styleable.PrismalTabBar_ptb_menu, 0)
            if (menuRes != 0) {
                val menu = PopupMenu(context, this).menu
                android.view.MenuInflater(context).inflate(menuRes, menu)
                for (i in 0 until menu.size()) {
                    val item = menu.getItem(i)
                    addTab(item.icon, item.title ?: "")
                }
            }
            selectedIndex = a.getInt(R.styleable.PrismalTabBar_ptb_selectedIndex, 0)
                .coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        }
        highlightIndex = selectedIndex
        valueSpring.snapTo(selectedIndex.toFloat())

        post { configureGlass(); applyPress(0f) }
    }

    private fun configureGlass() {
        bar.setOnBackdropCapturedListener {
            mainHandler.postDelayed({ refreshBarSnapshot { if (!dropletParked) droplet.updateBackground() } }, 32L)
        }
        PrismalLiquidGlass.applyBase(bar)
        bar.setBlurRadius(dp(8f))
        bar.setThickness(dp(4f))
        bar.setGlassColor(Color.TRANSPARENT)
        bar.setChromaticAberration(0f)
        bar.applyThumbLens(bandPx = dp(24f), amountPx = dp(24f), press = 0f, dome = 0.2f, displacement = 0.6f)

        droplet.setBackdropPassEnabled(false)
        droplet.setCornerRadius(pillH / 2f)
        droplet.setNormalStrength(0.2f)
        droplet.setFresnelReflectStrength(1.2f)
        droplet.setRimStrength(3f)
        droplet.setSpecular(1.2f, 120f)
        droplet.setCausticIntensity(0f)
        droplet.setMinSmoothing(2.5f)
        droplet.setHighlightWidth(0.45f)
        droplet.setLightDirection(-0.35f, -0.75f)
        droplet.setRefractionInset(dp(2f))
        droplet.setDispersion(0.4f, 0.4f)
        droplet.setTransmittance(1f)
        droplet.setGlassColor(Color.TRANSPARENT)
        droplet.setParallaxScale(0.4f)
        droplet.setBlurRadius(0f)
        droplet.setIOR(1.4f)
        droplet.setThickness(0.2f)
        droplet.setBrightness(1f)
    }

    private fun applyPress(t: Float) {
        val p = t.coerceIn(0f, 1f)
        droplet.applyThumbLens(bandPx = dp(14f) * p, amountPx = dp(4f) * p, press = p, dome = 0.2f, displacement = 0.6f)
        droplet.setChromaticAberration(20f * p)
        droplet.setShadowProperties(Color.argb((38 * p).toInt(), 0, 0, 0), 0.25f)
        droplet.setPressInteraction(
            progress = p,
            applyTransform = false,
            backdropPinch = 1f,
            glowStrength = p * 0.08f,
        )
        val park = !pressed && p < 0.01f
        if (park != dropletParked) {
            dropletParked = park
            droplet.translationY = if (park) PARKED_Y else 0f
            updateItemColors()
        }
    }

    private fun applyState() {
        val n = tabs.size
        if (n == 0 || width == 0) return
        val tw = tabWidth()
        val p = pressSpring.value.coerceIn(0f, 1f)
        val offset = panelOffset()
        val value = valueSpring.value

        val barScale = lerp(1f, 1f + dp(16f) / width, p)
        bar.translationX = offset
        bar.pivotX = width / 2f
        bar.pivotY = barH / 2f
        bar.scaleX = barScale
        bar.scaleY = barScale
        val itemScale = lerp(1f, 1.05f, p)
        for (tab in tabs) {
            tab.view.scaleX = itemScale
            tab.view.scaleY = itemScale
        }

        val x = pad + value * tw
        restPill.translationX = x
        restPill.alpha = 1f - p

        val v = (valueSpring.velocity / maxValue().coerceAtLeast(1f) / 10f)
        val sx = scaleXSpring.value / (1f - (v * 0.75f).coerceIn(-0.2f, 0.2f))
        val sy = scaleYSpring.value * (1f - (v * 0.25f).coerceIn(-0.2f, 0.2f))
        droplet.pivotX = tw / 2f
        droplet.pivotY = pillH / 2f
        droplet.translationX = x + offset
        droplet.scaleX = sx
        droplet.scaleY = sy
        dropletShadow.follow(droplet)
        dropletShadow.translationY = 0f
        dropletShadow.alpha = p

        if (!dropletParked) {
            droplet.updateBackdropScale()
            droplet.updateBackgroundIfMoved()
        }
        bar.updateBackdropScale()
    }

    private fun updateItemColors() {
        for ((i, tab) in tabs.withIndex()) {
            val c = if (dropletParked && i == highlightIndex) tintColor else itemColor
            tab.icon.setColorFilter(c, PorterDuff.Mode.SRC_IN)
            tab.label.setTextColor(c)
        }
    }

    /** Copies the bar's rendered glass so the lens shows the bar exactly as it looks. */
    private fun refreshBarSnapshot(then: () -> Unit) {
        val sv = bar.surfaceView
        if (snapshotInFlight || sv.width <= 0 || sv.height <= 0 || !sv.holder.surface.isValid) {
            then()
            return
        }
        val target = spareSnapshot?.takeIf { it.width == sv.width && it.height == sv.height }
            ?: createBitmap(sv.width, sv.height)
        snapshotInFlight = true
        try {
            PixelCopy.request(sv, target, { result ->
                snapshotInFlight = false
                if (result == PixelCopy.SUCCESS) {
                    spareSnapshot = barSnapshot
                    barSnapshot = target
                }
                then()
            }, mainHandler)
        } catch (e: IllegalArgumentException) {
            snapshotInFlight = false
            then()
        }
    }

    private fun press() {
        pressed = true
        applyPress(pressSpring.value)
        refreshBarSnapshot { if (!dropletParked) droplet.updateBackground() }
        pressSpring.animateTo(1f)
        scaleXSpring.animateTo(PRESSED_SCALE)
        scaleYSpring.animateTo(PRESSED_SCALE)
    }

    private fun release() {
        pressed = false
        pressSpring.animateTo(0f)
        scaleXSpring.animateTo(1f)
        scaleYSpring.animateTo(1f)
    }

    private fun releaseWhenSettled() {
        if (abs(valueSpring.value - valueSpring.target) < maxValue().coerceAtLeast(1f) * 0.025f) release()
        else releasePending = true
    }

    private fun select(index: Int, notify: Boolean) {
        val changed = index != selectedIndex
        selectedIndex = index
        highlightIndex = index
        updateItemColors()
        if (changed && notify) onTabSelected?.invoke(index)
    }

    /**
     * Draws a shrunken copy of the bar - its glass, tint and tinted tab content - into the
     * lens backdrop, so the lens shows a smaller bar whose edges and end caps bend at the rim.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun drawTabsIntoDroplet(c: Canvas, source: ViewGroup, bitmap: Bitmap) {
        source.getLocationInWindow(srcLoc)
        bar.getLocationInWindow(loc)
        val scale = bar.scaleX
        val left = (loc[0] - srcLoc[0]).toFloat()
        val top = (loc[1] - srcLoc[1]).toFloat()
        val inset = dp(6f) * scale
        snapRect.set(left, top, left + bar.width * scale, top + bar.height * scale)
        val insetX = inset * 1.4f
        rect.set(snapRect.left + insetX, snapRect.top + inset, snapRect.right - insetX, snapRect.bottom - inset)

        c.withSave {
            val feather = saveLayer(null, null)
            val reach = dp(2.5f)
            clipRect.set(rect.left - reach, rect.top - reach, rect.right + reach, rect.bottom + reach)
            clip.reset()
            clip.addRoundRect(clipRect, clipRect.height() / 2f, clipRect.height() / 2f, Path.Direction.CW)
            clipPath(clip)
            barSnapshot?.let {
                val core = (it.height * 0.3f).toInt()
                snapSrc.set(core, core, it.width - core, it.height - core)
                drawBitmap(it, snapSrc, clipRect, bitmapPaint)
            }

            fillPaint.color = containerColor
            drawPaint(fillPaint)

            layerPaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            withSave {
                translate(left, top)
                scale(scale, scale)
                saveLayer(0f, 0f, bar.width.toFloat(), bar.height.toFloat(), layerPaint)
                translate(row.left.toFloat(), row.top.toFloat())
                for (tab in tabs) {
                    withSave {
                        translate(tab.view.left.toFloat(), tab.view.top.toFloat())
                        scale(tab.view.scaleX, tab.view.scaleY, tab.view.width / 2f, tab.view.height / 2f)
                        tab.view.draw(this)
                    }
                }
                restore()
            }
            drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, featherPaint)
            restoreToCount(feather)
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child === bar && droplet.isCapturing) return false
        if ((child === droplet || child === dropletShadow) && bar.isCapturing) return false
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun tabAt(x: Float): Int {
        val tw = tabWidth()
        if (tw <= 0f) return -1
        return ((x - pad - panelOffset()) / tw).toInt().coerceIn(0, tabs.size - 1)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (tabs.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                lastX = e.x
                dragging = false
                val tw = tabWidth()
                val px = pad + valueSpring.value * tw + panelOffset()
                onDroplet = e.x >= px && e.x <= px + tw
                if (onDroplet) {
                    dragValue = valueSpring.target
                    press()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                lastX = e.x
                if (!dragging && onDroplet && abs(e.x - downX) > touchSlop) dragging = true
                if (dragging) {
                    dragValue = (dragValue + dx / tabWidth()).coerceIn(0f, maxValue())
                    valueSpring.animateTo(dragValue)
                    offsetSpring.snapTo(offsetSpring.value + dx)
                    val h = dragValue.roundToInt()
                    if (h != highlightIndex) {
                        highlightIndex = h
                        updateItemColors()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (onDroplet) {
                    val index = valueSpring.target.roundToInt().coerceIn(0, tabs.size - 1)
                    valueSpring.animateTo(index.toFloat())
                    offsetSpring.animateTo(0f)
                    select(index, notify = e.actionMasked == MotionEvent.ACTION_UP)
                    releaseWhenSettled()
                } else if (e.actionMasked == MotionEvent.ACTION_UP && abs(e.x - downX) <= touchSlop) {
                    val index = tabAt(e.x)
                    if (index >= 0) {
                        performClick()
                        animateTo(index)
                        select(index, notify = true)
                    }
                }
                onDroplet = false
                dragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun animateTo(index: Int) {
        if (index.toFloat() == valueSpring.target && !pressed) return
        press()
        valueSpring.animateTo(index.toFloat())
        releasePending = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val tw = ((MeasureSpec.getSize(widthMeasureSpec) - 2f * pad) / tabs.size.coerceAtLeast(1)).toInt()
        restPill.layoutParams.width = tw
        droplet.layoutParams.width = tw
        dropletShadow.layoutParams.width = tw
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        post { applyState() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? ViewGroup)?.let {
            it.clipChildren = false
            it.clipToPadding = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
        valueSpring.cancel(); pressSpring.cancel(); offsetSpring.cancel()
        scaleXSpring.cancel(); scaleYSpring.cancel()
    }

    /**
     * Appends a tab.
     *
     * @param icon Tab icon, tinted with the item / tint colour.
     * @param label Text shown under the icon.
     * @return The index of the new tab.
     */
    fun addTab(icon: Drawable?, label: CharSequence): Int {
        val iconView = ImageView(context).apply { setImageDrawable(icon?.mutate()) }
        val labelView = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(iconView, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()))
            addView(labelView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f).toInt() })
        }
        row.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        tabs.add(Tab(item, iconView, labelView))
        updateItemColors()
        requestLayout()
        return tabs.size - 1
    }

    /** @see addTab */
    fun addTab(iconRes: Int, label: CharSequence): Int =
        addTab(ContextCompat.getDrawable(context, iconRes), label)

    /** Removes every tab. */
    fun clearTabs() {
        row.removeAllViews()
        tabs.clear()
        selectedIndex = 0
        highlightIndex = 0
        valueSpring.snapTo(0f)
    }

    /**
     * Selects a tab programmatically. Does not invoke the selection listener.
     *
     * @param index Tab index.
     * @param animate When `true`, slides the glass lens to the tab.
     */
    fun setSelectedTab(index: Int, animate: Boolean = true) {
        if (tabs.isEmpty()) return
        val i = index.coerceIn(0, tabs.size - 1)
        if (animate && isLaidOut) animateTo(i) else valueSpring.snapTo(i.toFloat())
        select(i, notify = false)
    }

    /** @return The selected tab index. */
    fun getSelectedTab(): Int = selectedIndex

    /**
     * Registers a listener invoked when the user selects a tab (tap or drag release).
     *
     * @param l Callback receiving the selected index.
     */
    fun setOnTabSelectedListener(l: (Int) -> Unit) {
        onTabSelected = l
    }

    /**
     * Sets the colour of the selected tab and of everything seen through the lens.
     *
     * @param color ARGB color int (default iOS blue).
     */
    fun setTintColor(color: Int) {
        tintColor = color
        updateItemColors()
    }

    /**
     * Sets the colour of unselected tab icons and labels.
     *
     * @param color ARGB color int.
     */
    fun setItemColor(color: Int) {
        itemColor = color
        updateItemColors()
    }

    /**
     * Sets the frosted tint drawn over the bar glass.
     *
     * @param color ARGB color int; alpha controls tint strength.
     */
    fun setContainerColor(color: Int) {
        containerColor = color
        tint.invalidate()
    }

    /**
     * Re-captures the backdrop. Call when content beneath the bar changes
     * (e.g. after scrolling).
     */
    fun updateBackground() {
        bar.updateBackground()
        if (!dropletParked) droplet.updateBackground()
    }

    /** Returns the [PrismalFrameLayout] behind the tabs, for further glass customisation. */
    fun getBar(): PrismalFrameLayout = bar
}
