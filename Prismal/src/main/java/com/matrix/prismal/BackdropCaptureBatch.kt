package com.matrix.prismal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Draws the window once for all root captures requested in the same main-thread turn; each glass
 * view copies its crop from the shared bitmap. Overlapping captures run individually.
 *
 * @author Saurav Sajeev
 */
internal class BackdropCaptureBatch private constructor(root: ViewGroup) : Runnable {

    private val rootRef = WeakReference(root)

    private val pending = ArrayList<PrismalFrameLayout>()
    private val drawCanvas = Canvas()
    private val copyCanvas = Canvas()
    private val copyPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private var shared: Bitmap? = null

    override fun run() {
        val views = ArrayList(pending)
        pending.clear()
        val root = rootRef.get() ?: return

        val crops = ArrayList<Pair<PrismalFrameLayout, PrismalFrameLayout.CaptureCrop>>(views.size)
        for (v in views) {
            if (!v.isAttachedToWindow || v.width <= 0 || v.height <= 0 || v.rootView !== root) continue
            try {
                v.computeCrop(root, inWindow = false)?.let { crops.add(v to it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val separate = crops.filter { (v, c) -> v.onCaptureDrawn == null && crops.none { (o, oc) -> o !== v && overlaps(c, oc) } }
        val scale = separate.groupingBy { it.second.scale }.eachCount().maxByOrNull { it.value }?.key
        val group = separate.filter { it.second.scale == scale }
        val union = Rect()
        group.forEach { (_, c) -> union.union(c.gridX, c.gridY, c.gridX + c.bitmapWidth, c.gridY + c.bitmapHeight) }
        val area = group.sumOf { (_, c) -> c.bitmapWidth.toLong() * c.bitmapHeight }
        val useShared = group.size >= 2 &&
            union.width().toLong() * union.height() <= area * MAX_UNION_OVERDRAW

        if (useShared) drawShared(root, group, union, scale ?: 1f)

        for ((v, _) in crops) {
            if (useShared && group.any { it.first === v }) continue
            try {
                v.captureRegion(root, inWindow = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun drawShared(
        root: ViewGroup,
        group: List<Pair<PrismalFrameLayout, PrismalFrameLayout.CaptureCrop>>,
        union: Rect,
        scale: Float,
    ) {
        val bitmap = obtainShared(union.width(), union.height())
        val canvas = drawCanvas
        canvas.setBitmap(bitmap)
        val saveCount = canvas.save()
        group.forEach { it.first.isCapturing = true }
        try {
            canvas.translate(-union.left.toFloat(), -union.top.toFloat())
            canvas.scale(scale, scale)
            canvas.drawColor(Color.WHITE)
            captureDepth++
            try {
                root.draw(canvas)
            } finally {
                captureDepth--
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        } finally {
            group.forEach { it.first.isCapturing = false }
            canvas.restoreToCount(saveCount)
            canvas.setBitmap(null)
        }

        for ((v, c) in group) {
            val out = v.obtainCaptureBitmap(c.bitmapWidth, c.bitmapHeight)
            copyCanvas.setBitmap(out)
            copyCanvas.drawBitmap(
                bitmap,
                (union.left - c.gridX).toFloat(),
                (union.top - c.gridY).toFloat(),
                copyPaint
            )
            copyCanvas.setBitmap(null)
            v.submitCapture(out, c)
        }
    }

    private fun obtainShared(w: Int, h: Int): Bitmap {
        val current = shared
        if (current != null && !current.isRecycled) {
            if (current.width == w && current.height == h) return current
            if (current.allocationByteCount >= w * h * 4) {
                try {
                    current.reconfigure(w, h, Bitmap.Config.ARGB_8888)
                    return current
                } catch (_: IllegalArgumentException) {
                }
            }
            current.recycle()
        }
        return createBitmap(w, h).also { shared = it }
    }

    private fun overlaps(a: PrismalFrameLayout.CaptureCrop, b: PrismalFrameLayout.CaptureCrop): Boolean =
        a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

    companion object {
        private const val MAX_UNION_OVERDRAW = 2L
        private val batches = WeakHashMap<ViewGroup, BackdropCaptureBatch>()

        fun enqueue(root: ViewGroup, view: PrismalFrameLayout) {
            val batch = batches.getOrPut(root) { BackdropCaptureBatch(root) }
            if (view in batch.pending) return
            batch.pending.add(view)
            if (batch.pending.size == 1) root.post(batch)
        }
    }
}
