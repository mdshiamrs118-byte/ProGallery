package com.pro.gallery

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

class ZoomImageView(c: Context) : ImageView(c) {
    var onTap: (() -> Unit)? = null
    private var s = 1f

    private val sd = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoom((s * d.scaleFactor)); return true }
    })
    private val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoom(if (s > 1.05f) 1f else 2.5f); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (s > 1f) { translationX -= dx; translationY -= dy; clamp() }; return true
        }
    })

    private fun zoom(v: Float) { s = v.coerceIn(1f, 5f); scaleX = s; scaleY = s; clamp() }
    private fun clamp() {
        val mx = width * (s - 1) / 2; val my = height * (s - 1) / 2
        translationX = translationX.coerceIn(-mx, mx); translationY = translationY.coerceIn(-my, my)
    }
    fun reset() { s = 1f; scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        sd.onTouchEvent(e); gd.onTouchEvent(e)
        parent?.requestDisallowInterceptTouchEvent(s > 1f || e.pointerCount > 1)
        return true
    }
}
