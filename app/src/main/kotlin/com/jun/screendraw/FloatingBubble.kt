package com.jun.screendraw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingBubble(
    private val context: Context,
    initialX: Int,
    initialY: Int,
    private val onPositionChanged: (Int, Int) -> Unit,
    private val onTap: () -> Unit
) {

    private val view: TextView = TextView(context).apply {
        text = "✎"
        setTextColor(Color.WHITE)
        textSize = 24f
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bubble_bg)
        val pad = dp(14)
        setPadding(pad, pad, pad, pad)
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = initialX
        y = initialY
    }

    private var attached = false

    @SuppressLint("ClickableViewAccessibility")
    fun attachTo(wm: WindowManager) {
        if (attached) return

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        val touchSlop = dp(10)

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        if (attached) wm.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onTap()
                    } else {
                        onPositionChanged(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(view, params)
        attached = true
    }

    fun detach(wm: WindowManager) {
        if (attached) {
            wm.removeView(view)
            attached = false
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
