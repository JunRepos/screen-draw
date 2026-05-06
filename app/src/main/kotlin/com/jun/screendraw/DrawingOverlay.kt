package com.jun.screendraw

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class DrawingOverlay(
    private val context: Context,
    private val initialState: DrawState,
    private val onClose: (DrawState) -> Unit
) {

    private val canvasView = DrawingCanvasView(context).apply {
        color = initialState.color
        penWidth = initialState.penWidthPx
        highlighterWidth = initialState.highlighterWidthPx
        tool = initialState.tool
        eraserMode = initialState.eraserMode
        ignoreFinger = initialState.ignoreFinger
    }

    private val palette = listOf(
        Color.BLACK,
        0xFFE53935.toInt(),
        0xFF1E88E5.toInt(),
        0xFFFDD835.toInt(),
        0xFF43A047.toInt()
    )

    private val colorButtons = mutableListOf<View>()
    private val toolButtons = mutableListOf<TextView>()
    private var eraserButton: TextView? = null
    private var ignoreFingerButton: TextView? = null
    private lateinit var widthSeekBar: SeekBar

    private val container: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(0x33000000)
        addView(
            canvasView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        addView(
            buildToolbar(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            }
        )
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private var attached = false

    fun attachTo(wm: WindowManager) {
        if (attached) return
        wm.addView(container, params)
        attached = true
        applyInitialUiState()
    }

    fun detach(wm: WindowManager) {
        if (attached) {
            wm.removeView(container)
            attached = false
        }
    }

    private fun applyInitialUiState() {
        val colorIdx = palette.indexOf(initialState.color).let { if (it < 0) 0 else it }
        selectColor(colorIdx)
        val toolIdx = when (initialState.tool) {
            Tool.PEN -> 0
            Tool.HIGHLIGHTER -> 1
            Tool.ERASER -> 2
        }
        selectTool(toolIdx)
        updateEraserLabel()
        updateIgnoreFinger()
        syncWidthSeekBar()
    }

    private fun buildToolbar(): View {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0xCC1A1A1A.toInt())
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        bar.addView(textBtn("닫기", color = 0xFFFF8A80.toInt()) { closeAndReportState() })
        bar.addView(divider())

        // 색상 팔레트
        palette.forEachIndexed { i, c ->
            val swatch = colorSwatch(c) {
                canvasView.color = c
                if (canvasView.tool == Tool.ERASER) {
                    canvasView.tool = Tool.PEN
                    selectTool(0)
                    syncWidthSeekBar()
                }
                selectColor(i)
            }
            colorButtons.add(swatch)
            bar.addView(swatch)
        }
        bar.addView(divider())

        // 도구 (펜·형광펜·지우개)
        toolButtons.add(textBtn("펜") {
            canvasView.tool = Tool.PEN
            selectTool(0)
            syncWidthSeekBar()
        })
        toolButtons.add(textBtn("형광펜") {
            canvasView.tool = Tool.HIGHLIGHTER
            selectTool(1)
            syncWidthSeekBar()
        })

        // 지우개 — 짧게: 진입 / 길게: 영역↔획 토글
        val eraser = textBtn("지우개") {
            canvasView.tool = Tool.ERASER
            selectTool(2)
            syncWidthSeekBar()
        }
        eraser.setOnLongClickListener {
            canvasView.eraserMode = if (canvasView.eraserMode == EraserMode.PIXEL) {
                EraserMode.STROKE
            } else {
                EraserMode.PIXEL
            }
            canvasView.tool = Tool.ERASER
            selectTool(2)
            syncWidthSeekBar()
            updateEraserLabel()
            Toast.makeText(
                context,
                if (canvasView.eraserMode == EraserMode.STROKE) "획 지우개" else "영역 지우개",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        eraserButton = eraser
        toolButtons.add(eraser)
        toolButtons.forEach { bar.addView(it) }
        bar.addView(divider())

        // 굵기 슬라이드바 (펜·형광펜 각각 독립 굵기)
        widthSeekBar = SeekBar(context).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                dp(140), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val (loDp, hiDp) = currentDpRange()
                    val targetDp = loDp + (hiDp - loDp) * (progress / 100f)
                    val px = targetDp * context.resources.displayMetrics.density
                    when (canvasView.tool) {
                        Tool.HIGHLIGHTER -> canvasView.highlighterWidth = px
                        else -> canvasView.penWidth = px // PEN, ERASER
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        bar.addView(widthSeekBar)
        bar.addView(divider())

        // 손가락 무시 토글
        ignoreFingerButton = textBtn("손가락\n무시") {
            canvasView.ignoreFinger = !canvasView.ignoreFinger
            updateIgnoreFinger()
        }
        ignoreFingerButton?.apply {
            setLineSpacing(0f, 0.9f)
            textSize = 11f
        }
        bar.addView(ignoreFingerButton)

        bar.addView(textBtn("전체\n지우기", color = 0xFFFFAB91.toInt()) {
            canvasView.clearAll()
        }.apply {
            setLineSpacing(0f, 0.9f)
            textSize = 11f
        })

        return bar
    }

    private fun currentDpRange(): Pair<Float, Float> = when (canvasView.tool) {
        Tool.HIGHLIGHTER -> 8f to 40f
        else -> 1f to 16f // PEN, ERASER 모두 펜 굵기 매핑
    }

    private fun syncWidthSeekBar() {
        if (!::widthSeekBar.isInitialized) return
        val (loDp, hiDp) = currentDpRange()
        val curPx = when (canvasView.tool) {
            Tool.HIGHLIGHTER -> canvasView.highlighterWidth
            else -> canvasView.penWidth
        }
        val curDp = curPx / context.resources.displayMetrics.density
        val progress = ((curDp - loDp) / (hiDp - loDp) * 100f).toInt().coerceIn(0, 100)
        widthSeekBar.progress = progress
    }

    private fun closeAndReportState() {
        onClose(
            DrawState(
                tool = canvasView.tool,
                color = canvasView.color,
                penWidthPx = canvasView.penWidth,
                highlighterWidthPx = canvasView.highlighterWidth,
                eraserMode = canvasView.eraserMode,
                ignoreFinger = canvasView.ignoreFinger
            )
        )
    }

    private fun textBtn(
        label: String,
        color: Int = Color.WHITE,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        setTextColor(color)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            setColor(0x00000000)
            cornerRadius = dp(8).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(4) }
        setOnClickListener { onClick() }
    }

    private fun colorSwatch(color: Int, onClick: () -> Unit): View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        val size = dp(32)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = dp(8)
            gravity = Gravity.CENTER_VERTICAL
        }
        setOnClickListener { onClick() }
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(0x33FFFFFF)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(28)).apply {
            marginStart = dp(4)
            marginEnd = dp(8)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun selectColor(index: Int) {
        colorButtons.forEachIndexed { i, v -> v.alpha = if (i == index) 1.0f else 0.4f }
    }

    private fun selectTool(index: Int) {
        toolButtons.forEachIndexed { i, v ->
            v.background = GradientDrawable().apply {
                setColor(
                    when {
                        i != index -> 0x00000000
                        index == 2 -> 0x55FF5252 // 지우개는 빨강 톤
                        else -> 0x33FFFFFF
                    }
                )
                cornerRadius = dp(8).toFloat()
            }
        }
    }

    private fun updateEraserLabel() {
        eraserButton?.text = if (canvasView.eraserMode == EraserMode.STROKE) "획지우개" else "지우개"
    }

    private fun updateIgnoreFinger() {
        ignoreFingerButton?.background = GradientDrawable().apply {
            setColor(if (canvasView.ignoreFinger) 0x5564B5F6 else 0x00000000)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
