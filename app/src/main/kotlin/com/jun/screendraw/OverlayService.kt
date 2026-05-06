package com.jun.screendraw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: FloatingBubble? = null
    private var drawingOverlay: DrawingOverlay? = null

    private var bubbleX = 50
    private var bubbleY = 300

    private val prefs by lazy {
        getSharedPreferences("screen_draw", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        showBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        bubble = FloatingBubble(
            context = this,
            initialX = bubbleX,
            initialY = bubbleY,
            onPositionChanged = { x, y -> bubbleX = x; bubbleY = y },
            onTap = { enterDrawingMode() }
        ).also { it.attachTo(windowManager) }
    }

    private fun hideBubble() {
        bubble?.detach(windowManager)
        bubble = null
    }

    private fun enterDrawingMode() {
        if (drawingOverlay != null) return
        hideBubble()
        val state = loadState()
        drawingOverlay = DrawingOverlay(this, state) { finalState ->
            saveState(finalState)
            exitDrawingMode()
        }.also { it.attachTo(windowManager) }
    }

    private fun exitDrawingMode() {
        drawingOverlay?.detach(windowManager)
        drawingOverlay = null
        showBubble()
    }

    private fun loadState(): DrawState {
        val density = resources.displayMetrics.density
        val defaultPenWidthPx = 4f * density
        val defaultHlWidthPx = 20f * density
        return DrawState(
            tool = runCatching {
                Tool.valueOf(prefs.getString(KEY_TOOL, null) ?: Tool.PEN.name)
            }.getOrDefault(Tool.PEN),
            color = prefs.getInt(KEY_COLOR, Color.BLACK),
            penWidthPx = prefs.getFloat(KEY_PEN_WIDTH, defaultPenWidthPx),
            highlighterWidthPx = prefs.getFloat(KEY_HL_WIDTH, defaultHlWidthPx),
            eraserMode = runCatching {
                EraserMode.valueOf(prefs.getString(KEY_ERASER_MODE, null) ?: EraserMode.PIXEL.name)
            }.getOrDefault(EraserMode.PIXEL),
            ignoreFinger = prefs.getBoolean(KEY_IGNORE_FINGER, false)
        )
    }

    private fun saveState(state: DrawState) {
        prefs.edit()
            .putString(KEY_TOOL, state.tool.name)
            .putInt(KEY_COLOR, state.color)
            .putFloat(KEY_PEN_WIDTH, state.penWidthPx)
            .putFloat(KEY_HL_WIDTH, state.highlighterWidthPx)
            .putString(KEY_ERASER_MODE, state.eraserMode.name)
            .putBoolean(KEY_IGNORE_FINGER, state.ignoreFinger)
            .apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        drawingOverlay?.detach(windowManager)
        drawingOverlay = null
        bubble?.detach(windowManager)
        bubble = null
    }

    private fun buildNotification(): Notification {
        val channelId = "screen_draw_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val KEY_TOOL = "tool"
        private const val KEY_COLOR = "color"
        private const val KEY_PEN_WIDTH = "penWidth"
        private const val KEY_HL_WIDTH = "hlWidth"
        private const val KEY_ERASER_MODE = "eraserMode"
        private const val KEY_IGNORE_FINGER = "ignoreFinger"
    }
}
