package com.matrix.prismal.utils

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Internal animator class for [com.matrix.prismal.PrismalFrameLayout] or inheritances
 * to implement realistic spring physics animation.
 *
 * @author Saurav Sajeev
 */
internal class SpringAnimator(
    dampingRatio: Float,
    stiffness: Float,
    private val threshold: Float = 0.001f
) {
    private val k = stiffness
    private val d = 2f * dampingRatio * sqrt(stiffness)

    var value = 0f
        private set
    var velocity = 0f
        private set
    var target = 0f

    private var running = false
    private var lastNanos = 0L

    var onUpdate: ((Float) -> Unit)? = null
    var onSettled: (() -> Unit)? = null

    private val callback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        if (lastNanos == 0L) {
            lastNanos = nanos
            scheduleNext()
            return@FrameCallback
        }
        val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.048f)
        lastNanos = nanos

        val force = -k * (value - target) - d * velocity
        velocity += force * dt
        value += velocity * dt

        onUpdate?.invoke(value)

        if (abs(value - target) < threshold && abs(velocity) < threshold * 10f) {
            value = target
            velocity = 0f
            running = false
            lastNanos = 0L
            onUpdate?.invoke(value)
            onSettled?.invoke()
        } else {
            scheduleNext()
        }
    }

    private fun scheduleNext() = Choreographer.getInstance().postFrameCallback(callback)

    fun animateTo(newTarget: Float) {
        target = newTarget
        if (!running) {
            running = true
            lastNanos = 0L
            scheduleNext()
        }
    }

    fun snapTo(v: Float) {
        cancel()
        value = v
        target = v
        velocity = 0f
        onUpdate?.invoke(v)
    }

    fun cancel() {
        Choreographer.getInstance().removeFrameCallback(callback)
        running = false
        lastNanos = 0L
    }
}