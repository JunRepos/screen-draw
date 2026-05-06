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
import android.widget.TextView
import android.widget.Toast

class DrawingOverlay(
    private val context: Context,
    private val onClose: () -> Unit
) {

    private val canvasView = DrawingCanvasView(context)

    private val colorButtons = mutableListOf<View>()
    private val widthButtons = mutableListOf<TextView>()
    private val toolButtons = mutableListOf<TextView>() // 펜·형광펜·지우개 토글
    private var eraserButton: TextView? = null
    private var ignoreFingerButton: TextView? = null

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
        // 초기 상태
        selectColor(0)
        selectWidth(1)
        selectTool(0) // 펜
        updateEraserLabel()
    }

    fun detach(wm: WindowManager) {
        if (attached) {
            wm.removeView(container)
            attached = false
        }
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

        bar.addView(textBtn("닫기", color = 0xFFFF8A80.toInt()) { onClose() })
        bar.addView(divider())

        // 색상 팔레트
        val palette = listOf(
            Color.BLACK,
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFFFDD835.toInt(),
            0xFF43A047.toInt() // 초록 추가 — 형광펜용으로 자주 씀
        )
        palette.forEachIndexed { i, c ->
            val swatch = colorSwatch(c) {
                canvasView.color = c
                // 색을 누르면 지우개에서 빠져나오고 직전 도구(펜/형광펜)로
                if (canvasView.tool == Tool.ERASER) {
                    canvasView.tool = Tool.PEN
                    selectTool(0)
                }
                selectColor(i)
            }
            colorButtons.add(swatch)
            bar.addView(swatch)
        }
        bar.addView(divider())

        // 도구 (펜·형광펜·지우개)
        toolButtons.add(textBtn("펜") { canvasView.tool = Tool.PEN; selectTool(0) })
        toolButtons.add(textBtn("형광펜") { canvasView.tool = Tool.HIGHLIGHTER; selectTool(1) })

        // 지우개 — 짧게 탭: 지우개 모드 진입. 길게 누름: 영역↔획 토글.
        val eraser = textBtn("지우개") {
            canvasView.tool = Tool.ERASER
            selectTool(2)
        }
        eraser.setOnLongClickListener {
            canvasView.eraserMode = if (canvasView.eraserMode == EraserMode.PIXEL) {
                EraserMode.STROKE
            } else {
                EraserMode.PIXEL
            }
            canvasView.tool = Tool.ERASER
            selectTool(2)
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

        // 굵기
        widthButtons.add(textBtn("얇게") { canvasView.strokeWidth = dp(2).toFloat(); selectWidth(0) })
        widthButtons.add(textBtn("중간") { canvasView.strokeWidth = dp(4).toFloat(); selectWidth(1) })
        widthButtons.add(textBtn("굵게") { canvasView.strokeWidth = dp(8).toFloat(); selectWidth(2) })
        widthButtons.forEach { bar.addView(it) }
        bar.addView(divider())

        // 손가락 무시 토글
        ignoreFingerButton = textBtn("손가락\n무시") {
            canvasView.ignoreFinger = !canvasView.ignoreFinger
            updateIgnoreFinger()
        }
        ignoreFingerButton?.apply {
            // 두 줄 표시 — 라인 높이 조정
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

    private fun selectWidth(index: Int) {
        widthButtons.forEachIndexed { i, v ->
            v.background = GradientDrawable().apply {
                setColor(if (i == index) 0x33FFFFFF else 0x00000000)
                cornerRadius = dp(8).toFloat()
            }
        }
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
