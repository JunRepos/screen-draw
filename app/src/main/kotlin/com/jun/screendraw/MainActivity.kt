package com.jun.screendraw

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG_COLOR)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }

        // ===== Header =====
        root.addView(TextView(this).apply {
            text = "화면필기"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(BRAND)
        })
        root.addView(TextView(this).apply {
            text = "어떤 앱 위에서도 S펜으로 바로 필기"
            textSize = 15f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(6), 0, dp(28))
        })

        // ===== Feature cards =====
        val features = listOf(
            Feature("🖊️", "플로팅 펜 버튼",
                "어디서든 떠있는 펜 버블을 탭하면 즉시 필기 모드로 진입. 드래그로 위치 이동."),
            Feature("🔀", "필기 ↔ 조작 자유 전환",
                "[조작] 버튼을 누르면 필기가 사라지면서 화면 조작 가능. [필기] 누르면 다시 그릴 수 있음. 닫지 않아도 됨."),
            Feature("🖍️", "S펜 사이드버튼 = 임시 지우개",
                "S펜 옆 버튼을 누른 채로 그으면 그 획만 지우개로 동작. 떼면 다시 펜. 뒷지우개도 동일."),
            Feature("✂️", "영역 / 획 지우개",
                "[▾ 지우개] 드롭다운에서 모드 선택. 영역은 픽셀 단위, 획은 스트로크 통째로 삭제."),
            Feature("🟡", "형광펜 + 굵기 슬라이드바",
                "5색, 형광펜은 알파 35%. 펜과 형광펜의 굵기는 독립적으로 슬라이더 조정."),
            Feature("🖐️", "손가락 무시 (palm rejection)",
                "토글 켜면 펜으로만 필기됨. 손바닥이 화면에 닿아도 안 그려짐."),
            Feature("💾", "도구 상태 영구 저장",
                "닫고 다시 열어도 마지막에 쓰던 도구·색·굵기·지우개 모드가 그대로 복원됨.")
        )
        features.forEach { root.addView(makeFeatureCard(it)) }

        // ===== 권한 안내 =====
        root.addView(TextView(this).apply {
            text = "💡  처음 실행 시 \"다른 앱 위에 표시\" 권한이 필요해요."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(dp(4), dp(20), dp(4), dp(20))
        })

        // ===== 액션 버튼 =====
        root.addView(Button(this).apply {
            text = "플로팅 버튼 켜기"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(BRAND)
                cornerRadius = dp(28).toFloat()
            }
            stateListAnimator = null
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
            setOnClickListener { ensurePermissionAndStart() }
        })

        root.addView(TextView(this).apply {
            text = "플로팅 버튼 끄기"
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(16), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { stopOverlay() }
        })

        scroll.addView(root)
        return scroll
    }

    private data class Feature(val emoji: String, val title: String, val desc: String)

    private fun makeFeatureCard(f: Feature): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                cornerRadius = dp(14).toFloat()
            }
            elevation = dp(1).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        // 아이콘 — 원형 배경 + 이모지
        val iconBg = TextView(this).apply {
            text = f.emoji
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ICON_BG)
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(14)
            }
        }
        card.addView(iconBg)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        textCol.addView(TextView(this).apply {
            text = f.title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        })
        textCol.addView(TextView(this).apply {
            text = f.desc
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(3), 0, 0)
        })
        card.addView(textCol)

        return card
    }

    private fun ensurePermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_message)
                .setPositiveButton(R.string.perm_open) { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        startOverlay()
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        moveTaskToBack(true)
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
        resources.displayMetrics
    ).toInt()

    companion object {
        // 라이트 톤 팔레트 — Notion·Linear 류의 카드 패턴
        private const val BG_COLOR = 0xFFFAFAFA.toInt()
        private const val CARD_BG = 0xFFFFFFFF.toInt()
        private const val ICON_BG = 0xFFEEF3FA.toInt()
        private const val BRAND = 0xFF1976D2.toInt()
        private const val TEXT_PRIMARY = 0xFF1A1A1A.toInt()
        private const val TEXT_SECONDARY = 0xFF6B6B6B.toInt()
    }
}
