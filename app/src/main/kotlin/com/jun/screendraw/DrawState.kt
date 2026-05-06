package com.jun.screendraw

import android.graphics.Color

/** 필기 모드 진입 사이에 보존되는 도구 설정. 필기 내용(stroke)은 포함하지 않음. */
data class DrawState(
    val tool: Tool = Tool.PEN,
    val color: Int = Color.BLACK,
    val penWidthPx: Float = 0f,
    val highlighterWidthPx: Float = 0f,
    val eraserMode: EraserMode = EraserMode.PIXEL,
    val ignoreFinger: Boolean = false
)
