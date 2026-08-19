package com.xrc.app.overlay

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.xrc.app.XRCApp
import com.xrc.app.R

/**
 * Foreground service for overlay rendering.
 * Keeps the overlay engine alive in the background.
 */
class OverlayService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val TAG = "OverlayService"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Restart via alarm
        restartService()
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, XRCApp.CHANNEL_OVERLAY_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Overlay Service")
            .setContentText("Rendering system overlays")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun restartService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
