package com.example.watchparty.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Section 4 & Step 8: HostForegroundService
 * Keeps the Host's embedded WebSocketServer and MediaExtractor chunking pipeline
 * alive when the screen turns off or app goes to background.
 */
class HostForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE) ?: "Party"
        val hostIp = intent?.getStringExtra(EXTRA_HOST_IP) ?: "Local WiFi"
        val notification = buildNotification(roomCode, hostIp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WatchParty::HostStreamingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // Max 2 hours
            }
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watch Party Host Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps local Wi-Fi Watch Party WebSocket streaming active"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(roomCode: String, hostIp: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Watch Party Room $roomCode Active")
            .setContentText("Streaming on ws://$hostIp:8080 • Keep Wi-Fi on")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "watch_party_host_channel"
        private const val NOTIFICATION_ID = 8080
        private const val EXTRA_ROOM_CODE = "extra_room_code"
        private const val EXTRA_HOST_IP = "extra_host_ip"

        fun start(context: Context, roomCode: String, hostIp: String) {
            val intent = Intent(context, HostForegroundService::class.java).apply {
                putExtra(EXTRA_ROOM_CODE, roomCode)
                putExtra(EXTRA_HOST_IP, hostIp)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, HostForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
