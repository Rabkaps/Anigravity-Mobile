package com.hambalapps.agymobile.data.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hambalapps.agymobile.MainActivity
import com.hambalapps.agymobile.R

/**
 * Lightweight foreground service active while an agent turn or background CLI task is running,
 * preventing Android/One UI from restricting network access when the user switches to the browser.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Working in background…"
        ensureChannel()
        val notification = buildNotification(detail)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (intent?.action == ACTION_STOP) {
            isActive = false
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelfResult(startId)
        } else {
            isActive = true
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        isActive = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        isActive = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Active Tasks",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Antigravity network and background CLI tasks alive"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(detail: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Antigravity")
            .setContentText(detail)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "antigravity_active_task"
        private const val NOTIFICATION_ID = 4789
        private const val EXTRA_DETAIL = "detail"
        private const val ACTION_STOP = "com.hambalapps.agymobile.ACTION_STOP"

        @Volatile
        private var isActive = false

        fun start(context: Context, detail: String = "Working in background…") {
            isActive = true
            runCatching {
                val intent = Intent(context, AgentForegroundService::class.java)
                    .putExtra(EXTRA_DETAIL, detail)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            if (!isActive) return
            isActive = false
            runCatching {
                val intent = Intent(context, AgentForegroundService::class.java)
                    .setAction(ACTION_STOP)
                context.startService(intent)
            }
        }
    }
}
