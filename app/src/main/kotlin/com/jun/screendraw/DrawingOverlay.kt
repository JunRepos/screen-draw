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

class DrawingOverlay(
    private val context: Context,
    private val onClose: () -> Unit
) {

    private val canvasView = DrawingCanvasView(context)
    private val colorButtons = mutableListOf<View>()
    private val widthButtons = mutableListOf<TextView>()
    private var eraserButton: TextView? = null

    private val container: FrameLayout = FrameLayout(context).apply {
        // 필기 모드라는 시각적 표시 — 살짝 어둡게
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
        // 초기 선택 상태
        selectColor(0)
        selectWidth(1)
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

        val palette = listOf(
            Color.BLACK,
            0xFFE53935.toInt(), // 빨강
            0xFF1E88E5.toInt(), // 파랑
            0xFFFDD835.toInt()  // 노랑
        )
        palette.forEachIndexed { i, c ->
            val swatch = colorSwatch(c) {
                canvasView.color = c
                canvasView.eraserMode = false
                selectColor(i)
            }
            colorButtons.add(swatch)
            bar.addView(swatch)
        }
        bar.addView(divider())

        widthButtons.add(textBtn("얇게") { canvasView.strokeWidth = dp(2).toFloat(); selectWidth(0) })
        widthButtons.add(textBtn("중간") { canvasView.strokeWidth = dp(4).toFloat(); selectWidth(1) })
        widthButtons.add(textBtn("굵게") { canvasView.strokeWidth = dp(8).toFloat(); selectWidth(2) })
        widthButtons.forEach { bar.addView(it) }
        bar.addView(divider())

        eraserButton = textBtn("지우개") {
            canvasView.eraserMode = !canvasView.eraserMode
            updateEraserSelection()
            if (canvasView.eraserMode) {
                colorButtons.forEach { it.alpha = 0.4f }
            } else {
                selectColor(colorButtons.indexOfFirst { it.alpha > 0.9f }.coerceAtLeast(0))
            }
        }
        bar.addView(eraserButton)

        bar.addView(textBtn("전체지우기", color = 0xFFFFAB91.toInt()) { canvasView.clearAll() })

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
        setPadding(dp(12), dp(8), dp(12), dp(8))
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
        canvasView.eraserMode = false
        updateEraserSelection()
    }

    private fun selectWidth(index: Int) {
        widthButtons.forEachIndexed { i, v ->
            v.background = GradientDrawable().apply {
                setColor(if (i == index) 0x33FFFFFF else 0x00000000)
                cornerRadius = dp(8).toFloat()
            }
        }
    }

    private fun updateEraserSelection() {
        eraserButton?.background = GradientDrawable().apply {
            setColor(if (canvasView.eraserMode) 0x55FF5252 else 0x00000000)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
