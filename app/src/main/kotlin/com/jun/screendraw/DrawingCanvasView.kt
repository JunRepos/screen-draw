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

enum class Tool { PEN, HIGHLIGHTER, ERASER }

class DrawingCanvasView(context: Context) : View(context) {

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private var currentStrokeAccepted: Boolean = true
    private var lastX = 0f
    private var lastY = 0f

    var color: Int = Color.BLACK
    var strokeWidth: Float = 8f
    var tool: Tool = Tool.PEN

    /** true 일 때 손가락 입력은 무시 (펜으로만 필기). 밑 앱 조작은 여전히 안 됨. */
    var ignoreFinger: Boolean = false

    init {
        // 지우개(CLEAR xfermode)가 자체 레이어에 작동하도록
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun makePaint(effectiveTool: Tool): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        when (effectiveTool) {
            Tool.PEN -> {
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = this@DrawingCanvasView.strokeWidth
                this.color = this@DrawingCanvasView.color
            }
            Tool.HIGHLIGHTER -> {
                strokeCap = Paint.Cap.SQUARE
                this.strokeWidth = this@DrawingCanvasView.strokeWidth * 3.5f
                this.color = this@DrawingCanvasView.color
                this.alpha = (255 * 0.35f).toInt()
            }
            Tool.ERASER -> {
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = this@DrawingCanvasView.strokeWidth * 4f
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
    }

    /** ACTION_DOWN 시점에 결정. S펜 사이드버튼 또는 뒷지우개면 임시 지우개. */
    private fun resolveEffectiveTool(event: MotionEvent): Tool {
        val toolType = event.getToolType(0)
        // S펜 뒷지우개 — 항상 지우개
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) return Tool.ERASER
        // S펜 사이드버튼이 눌린 동안 — 임시 지우개
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
        val sideButton = (event.buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        )) != 0
        if (isStylus && sideButton) return Tool.ERASER
        return tool
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
        // ACTION_DOWN 시점에 이 stroke를 받을지 거를지 결정 (stroke 내내 일관)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
            currentStrokeAccepted = !(ignoreFinger && isFinger)
        }
        if (!currentStrokeAccepted) return true

        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val effective = resolveEffectiveTool(event)
                val path = Path().apply { moveTo(x, y) }
                currentPath = path
                currentPaint = makePaint(effective)
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
