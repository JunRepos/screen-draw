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
enum class EraserMode { PIXEL, STROKE }

class DrawingCanvasView(context: Context) : View(context) {

    private data class Stroke(
        val path: Path,
        val paint: Paint,
        val samples: FloatArray // [x0, y0, x1, y1, ...]
    )

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private val currentSamples = ArrayList<Float>(64)

    private enum class StrokeAction { IGNORE, ERASE_STROKE, DRAW }
    private var currentAction: StrokeAction = StrokeAction.IGNORE

    private var lastX = 0f
    private var lastY = 0f

    var color: Int = Color.BLACK
    var strokeWidth: Float = 8f
    var tool: Tool = Tool.PEN
    var eraserMode: EraserMode = EraserMode.PIXEL

    /** true 일 때 손가락 입력은 무시 (펜으로만 필기). 밑 앱 조작은 여전히 안 됨. */
    var ignoreFinger: Boolean = false

    /** 획 지우개 hit-test 반경 — 펜 좌표와의 거리 임계값 */
    private val strokeEraseRadiusPx: Float by lazy {
        18f * resources.displayMetrics.density
    }

    init {
        // 영역 지우개(CLEAR xfermode)가 자체 레이어에 작동하도록
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
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) return Tool.ERASER
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
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
                if (ignoreFinger && isFinger) {
                    currentAction = StrokeAction.IGNORE
                    return true
                }
                val effective = resolveEffectiveTool(event)
                if (effective == Tool.ERASER && eraserMode == EraserMode.STROKE) {
                    currentAction = StrokeAction.ERASE_STROKE
                    eraseStrokesNear(x, y)
                    invalidate()
                } else {
                    currentAction = StrokeAction.DRAW
                    val path = Path().apply { moveTo(x, y) }
                    currentPath = path
                    currentPaint = makePaint(effective)
                    currentSamples.clear()
                    currentSamples.add(x)
                    currentSamples.add(y)
                    lastX = x
                    lastY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (currentAction) {
                    StrokeAction.IGNORE -> {}
                    StrokeAction.ERASE_STROKE -> {
                        eraseStrokesNear(x, y)
                        invalidate()
                    }
                    StrokeAction.DRAW -> {
                        val midX = (lastX + x) / 2f
                        val midY = (lastY + y) / 2f
                        currentPath?.quadTo(lastX, lastY, midX, midY)
                        currentSamples.add(x)
                        currentSamples.add(y)
                        lastX = x
                        lastY = y
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (currentAction) {
                    StrokeAction.IGNORE -> {}
                    StrokeAction.ERASE_STROKE -> {
                        eraseStrokesNear(x, y)
                        invalidate()
                    }
                    StrokeAction.DRAW -> {
                        currentPath?.lineTo(x, y)
                        currentSamples.add(x)
                        currentSamples.add(y)
                        val p = currentPath
                        val paint = currentPaint
                        if (p != null && paint != null) {
                            strokes.add(Stroke(p, paint, currentSamples.toFloatArray()))
                        }
                        currentPath = null
                        currentPaint = null
                        currentSamples.clear()
                        invalidate()
                    }
                }
                currentAction = StrokeAction.IGNORE
            }
        }
        return true
    }

    /** 펜 좌표 (x, y) 와 임계 거리 안에 점이 있는 stroke를 모두 제거. */
    private fun eraseStrokesNear(x: Float, y: Float) {
        val r2 = strokeEraseRadiusPx * strokeEraseRadiusPx
        val it = strokes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val pts = s.samples
            var hit = false
            var i = 0
            while (i + 1 < pts.size) {
                val dx = pts[i] - x
                val dy = pts[i + 1] - y
                if (dx * dx + dy * dy <= r2) {
                    hit = true
                    break
                }
                i += 2
            }
            if (hit) it.remove()
        }
    }

    fun clearAll() {
        strokes.clear()
        currentPath = null
        currentPaint = null
        currentSamples.clear()
        invalidate()
    }
}
