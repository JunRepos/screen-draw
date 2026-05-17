package com.jun.screendraw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Tool { PEN, HIGHLIGHTER, ERASER }
enum class EraserMode { PIXEL, STROKE }

/**
 * 필기 캔버스.
 *
 * 성능 설계:
 *  - 완성된 stroke는 **오프스크린 Bitmap** 에 누적해 저장.
 *  - 매 ACTION_MOVE 마다 그 segment 만 오프스크린에 incremental 하게 그림(전체 stroke 재그리기 X).
 *  - View 의 onDraw 는 오프스크린 비트맵 한 장 그리기 → stroke 수가 늘어나도 일정 비용.
 *  - 부분 invalidate(`invalidate(Rect)`) — 화면 전체 합성 트리거를 피함.
 *  - LAYER_TYPE_SOFTWARE 제거(default NONE) — GPU 가속 사용.
 *  - PorterDuff.CLEAR(영역 지우개)는 오프스크린 Canvas에서 적용되므로 GPU 호환성과 무관.
 *
 * 획 지우개는 `samples` 좌표 배열로 hit-test 후 strokes 에서 제거하고
 * 오프스크린을 한 번 재생성.
 */
class DrawingCanvasView(context: Context) : View(context) {

    private data class Stroke(
        val path: Path,
        val paint: Paint,
        val samples: FloatArray
    )

    private val strokes = mutableListOf<Stroke>()

    private var currentPaint: Paint? = null
    private val currentSamples = ArrayList<Float>(128)

    private enum class StrokeAction { IGNORE, ERASE_STROKE, DRAW }
    private var currentAction: StrokeAction = StrokeAction.IGNORE

    private var lastX = 0f
    private var lastY = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    var color: Int = Color.BLACK
    var tool: Tool = Tool.PEN
    var eraserMode: EraserMode = EraserMode.PIXEL
    var ignoreFinger: Boolean = false

    var penWidth: Float = 0f
    var highlighterWidth: Float = 0f

    private val strokeEraseRadiusPx: Float by lazy {
        18f * resources.displayMetrics.density
    }

    // ===== 오프스크린 비트맵 — 완성된 stroke 누적 =====
    private var offscreen: Bitmap? = null
    private var offscreenCanvas: Canvas? = null

    /** invalidate(Rect) 재사용 객체 — 매 MOVE 마다 alloc 피함. */
    private val dirtyRect = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        offscreen = bmp
        offscreenCanvas = Canvas(bmp)
        redrawOffscreen()
    }

    private fun redrawOffscreen() {
        val bmp = offscreen ?: return
        val oc = offscreenCanvas ?: return
        bmp.eraseColor(Color.TRANSPARENT)
        for (s in strokes) oc.drawPath(s.path, s.paint)
    }

    private fun makePaint(effectiveTool: Tool): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        when (effectiveTool) {
            Tool.PEN -> {
                strokeCap = Paint.Cap.ROUND
                strokeWidth = penWidth
                color = this@DrawingCanvasView.color
            }
            Tool.HIGHLIGHTER -> {
                strokeCap = Paint.Cap.SQUARE
                strokeWidth = highlighterWidth
                color = this@DrawingCanvasView.color
                alpha = (255 * 0.35f).toInt()
            }
            Tool.ERASER -> {
                strokeCap = Paint.Cap.ROUND
                strokeWidth = penWidth * 4f
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
    }

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
        offscreen?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event, x, y)
            MotionEvent.ACTION_MOVE -> handleMove(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(x, y)
        }
        return true
    }

    private fun handleDown(event: MotionEvent, x: Float, y: Float) {
        val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        if (ignoreFinger && isFinger) {
            currentAction = StrokeAction.IGNORE
            return
        }
        val effective = resolveEffectiveTool(event)
        if (effective == Tool.ERASER && eraserMode == EraserMode.STROKE) {
            currentAction = StrokeAction.ERASE_STROKE
            if (eraseStrokesNear(x, y)) {
                redrawOffscreen()
                invalidate()
            }
            return
        }
        currentAction = StrokeAction.DRAW
        val paint = makePaint(effective)
        currentPaint = paint
        currentSamples.clear()
        currentSamples.add(x)
        currentSamples.add(y)
        // 시작 점 — 단일 탭(점 찍기) 지원
        offscreenCanvas?.drawCircle(x, y, paint.strokeWidth / 2f, paint)
        lastX = x; lastY = y
        lastMidX = x; lastMidY = y
        invalidateRect(x, y, x, y, paint.strokeWidth)
    }

    private fun handleMove(x: Float, y: Float) {
        when (currentAction) {
            StrokeAction.IGNORE -> { /* no-op */ }
            StrokeAction.ERASE_STROKE -> {
                if (eraseStrokesNear(x, y)) {
                    redrawOffscreen()
                    invalidate()
                }
            }
            StrokeAction.DRAW -> {
                val paint = currentPaint ?: return
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                // 작은 segment: prev mid → quad(lastX,lastY, midX,midY)
                val seg = Path().apply {
                    moveTo(lastMidX, lastMidY)
                    quadTo(lastX, lastY, midX, midY)
                }
                offscreenCanvas?.drawPath(seg, paint)
                currentSamples.add(x); currentSamples.add(y)
                // 영역만 invalidate
                invalidateRect(lastMidX, lastMidY, midX, midY, paint.strokeWidth)
                lastMidX = midX; lastMidY = midY
                lastX = x; lastY = y
            }
        }
    }

    private fun handleUp(x: Float, y: Float) {
        when (currentAction) {
            StrokeAction.IGNORE, StrokeAction.ERASE_STROKE -> { /* no-op */ }
            StrokeAction.DRAW -> {
                val paint = currentPaint
                if (paint != null) {
                    // 마지막 segment — lineTo 로 마무리
                    val seg = Path().apply {
                        moveTo(lastMidX, lastMidY)
                        lineTo(x, y)
                    }
                    offscreenCanvas?.drawPath(seg, paint)
                    currentSamples.add(x); currentSamples.add(y)
                    // 획 지우개 hit-test 및 redraw 용 path 저장
                    val fullPath = buildPathFromSamples(currentSamples)
                    strokes.add(Stroke(fullPath, paint, currentSamples.toFloatArray()))
                    invalidateRect(lastMidX, lastMidY, x, y, paint.strokeWidth)
                }
            }
        }
        currentPaint = null
        currentSamples.clear()
        currentAction = StrokeAction.IGNORE
    }

    /** samples([x0,y0,x1,y1,...])로부터 부드러운(quad) path 복원. redraw 용도. */
    private fun buildPathFromSamples(s: List<Float>): Path {
        val p = Path()
        if (s.size < 2) return p
        var prevX = s[0]
        var prevY = s[1]
        p.moveTo(prevX, prevY)
        if (s.size < 4) return p
        var i = 2
        while (i + 3 < s.size) {
            val curX = s[i]
            val curY = s[i + 1]
            val midX = (prevX + curX) / 2f
            val midY = (prevY + curY) / 2f
            p.quadTo(prevX, prevY, midX, midY)
            prevX = curX; prevY = curY
            i += 2
        }
        // 마지막 점까지 직선으로
        p.lineTo(s[s.size - 2], s[s.size - 1])
        return p
    }

    /** (x,y) 근처에 있는 stroke 들을 strokes 에서 제거. 제거된 게 있으면 true. */
    private fun eraseStrokesNear(x: Float, y: Float): Boolean {
        val r2 = strokeEraseRadiusPx * strokeEraseRadiusPx
        val it = strokes.iterator()
        var removed = false
        while (it.hasNext()) {
            val s = it.next()
            val pts = s.samples
            var hit = false
            var i = 0
            while (i + 1 < pts.size) {
                val dx = pts[i] - x
                val dy = pts[i + 1] - y
                if (dx * dx + dy * dy <= r2) { hit = true; break }
                i += 2
            }
            if (hit) {
                it.remove()
                removed = true
            }
        }
        return removed
    }

    private fun invalidateRect(x1: Float, y1: Float, x2: Float, y2: Float, brush: Float) {
        val pad = brush * 1.5f + 4f
        val l = (min(x1, x2) - pad).toInt()
        val t = (min(y1, y2) - pad).toInt()
        val r = ceil(max(x1, x2) + pad).toInt()
        val b = ceil(max(y1, y2) + pad).toInt()
        dirtyRect.set(l, t, r, b)
        invalidate(dirtyRect)
    }

    fun clearAll() {
        strokes.clear()
        currentPaint = null
        currentSamples.clear()
        offscreen?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }
}
