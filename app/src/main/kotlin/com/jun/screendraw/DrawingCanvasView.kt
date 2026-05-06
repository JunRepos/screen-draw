package com.jun.screendraw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View

class DrawingCanvasView(context: Context) : View(context) {

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private var lastX = 0f
    private var lastY = 0f

    var color: Int = Color.BLACK
    var strokeWidth: Float = 8f
    var eraserMode: Boolean = false

    init {
        // 지우개(CLEAR xfermode)가 자체 레이어에 작동하도록
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun makePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        if (eraserMode) {
            this.strokeWidth = this@DrawingCanvasView.strokeWidth * 4f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            this.strokeWidth = this@DrawingCanvasView.strokeWidth
            this.color = this@DrawingCanvasView.color
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in strokes) canvas.drawPath(s.path, s.paint)
        val p = currentPath
        val paint = currentPaint
        if (p != null && paint != null) canvas.drawPath(p, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val path = Path().apply { moveTo(x, y) }
                currentPath = path
                currentPaint = makePaint()
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                currentPath?.quadTo(lastX, lastY, midX, midY)
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.lineTo(x, y)
                val p = currentPath
                val paint = currentPaint
                if (p != null && paint != null) {
                    strokes.add(Stroke(p, paint))
                }
                currentPath = null
                currentPaint = null
                invalidate()
            }
        }
        return true
    }

    fun clearAll() {
        strokes.clear()
        currentPath = null
        currentPaint = null
        invalidate()
    }
}
