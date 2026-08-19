package com.xrc.app.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.xrc.app.XRCApp
import com.xrc.app.R

class CoreService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val TAG = "CoreService"
        var isRunning = false
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        // Restart the service via alarm manager for persistence
        reschedule()
    }

    private fun createNotification(): Notification {
        val channelId = XRCApp.CHANNEL_CORE_ID
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.core_notification_title))
            .setContentText(getString(R.string.core_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "XRC:CoreWakeLock"
            ).apply {
                acquire(30 * 60 * 1000L) // 30 minutes max
            }
        } catch (e: Exception) {
            // Wake lock may be restricted
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) { }
    }

    private fun reschedule() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, CoreService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5000,
            pendingIntent
        )
    }

    class RestartReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val serviceIntent = Intent(context, CoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
