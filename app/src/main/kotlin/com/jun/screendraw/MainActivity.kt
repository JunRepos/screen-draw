package com.jun.screendraw

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.intro)
            textSize = 16f
            setPadding(0, 0, 0, dp(32))
        })

        root.addView(Button(this).apply {
            text = getString(R.string.start_overlay)
            setOnClickListener { ensurePermissionAndStart() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        })

        root.addView(Button(this).apply {
            text = getString(R.string.stop_overlay)
            setOnClickListener { stopOverlay() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        return root
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
        // 사용자가 다시 앱을 열 필요 없도록 종료 → 홈으로 빠지면서 버블만 남음
        moveTaskToBack(true)
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
        resources.displayMetrics
    ).toInt()
}
