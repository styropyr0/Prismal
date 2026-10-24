package com.matrix.prismal.utils

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Soft drop shadow drawn behind a switch/slider thumb; mirrors the thumb's transform. */
internal class ThumbShadowView(context: Context, val pad: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 0, 0, 0)
        maskFilter = BlurMaskFilter(pad * 0.6f, BlurMaskFilter.Blur.NORMAL)
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        c.drawRoundRect(0f, pad * 0.25f, width.toFloat(), height + pad * 0.25f, r, r, paint)
    }

    fun follow(thumb: View) {
        translationX = thumb.translationX
        translationY = thumb.translationY
        pivotX = width / 2f
        pivotY = height / 2f
        scaleX = thumb.scaleX
        scaleY = thumb.scaleY
    }
}
