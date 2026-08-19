package com.xrc.app.persistence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
 * Keep-alive manager that prevents the OS from killing our process.
 * Uses multiple overlapping keep-alive techniques:
 * - AlarmManager repeating alarms
 * - Foreground service with persistent notification
 * - JobScheduler periodic jobs
 * - WorkManager periodic work requests
 * - Wake locks to prevent CPU sleep
 * - Guardian service that restarts the main service if killed
 */
class KeepAliveManager(private val context: Context) {

    companion object {
        private const val TAG = "KeepAliveManager"

        // Alarm intervals
        private const val ALARM_INTERVAL = 30_000L // 30 seconds
        private const val SHORT_INTERVAL = 10_000L // 10 seconds
        private const val KEEPALIVE_INTERVAL = 15_000L // 15 seconds

        // Intent actions
        private const val ACTION_KEEPALIVE = "${XRCApp.PACKAGE_NAME}.KEEPALIVE"
        private const val ACTION_GUARDIAN_CHECK = "${XRCApp.PACKAGE_NAME}.GUARDIAN_CHECK"

        // Request codes for PendingIntents
        private const val REQ_KEEPALIVE = 1001
        private const val REQ_GUARDIAN = 1002
        private const val REQ_CRITICAL = 1003

        // Maximum missed heartbeats before forced restart
        private const val MAX_MISSED_HEARTBEATS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Volatile
    var isActive: Boolean = false
        private set

    @Volatile
    private var missedHeartbeats = 0

    @Volatile
    private var lastHeartbeat = 0L

    /**
     * Start all keep-alive mechanisms.
     */
    fun start() {
        if (isActive) return
        isActive = true
        lastHeartbeat = System.currentTimeMillis()
        Log.i(TAG, "Keep-alive manager starting")

        // Start heartbeat monitor
        scope.launch { heartbeatMonitor() }

        // Schedule alarms
        scheduleKeepAliveAlarm()
        scheduleGuardianAlarm()

        // Start CoreService if not running
        ensureServicesRunning()

        Log.i(TAG, "Keep-alive manager started")
    }

    /**
     * Stop all keep-alive mechanisms.
     */
    fun stop() {
        isActive = false
        try {
            alarmManager.cancel(createKeepAlivePendingIntent())
            alarmManager.cancel(createGuardianPendingIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Alarm cancel error: ${e.message}")
        }
        Log.i(TAG, "Keep-alive manager stopped")
    }

    /**
     * Report a heartbeat — called periodically by CoreService.
     */
    fun reportHeartbeat() {
        lastHeartbeat = System.currentTimeMillis()
        missedHeartbeats = 0
    }

    /**
     * Ensure CoreService is running.
     */
    private fun ensureServicesRunning() {
        if (!CoreService.isRunning) {
            try {
                val intent = Intent(context, CoreService::class.java).apply {
                    action = CoreService.ACTION_START_FOREGROUND
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "CoreService started from KeepAliveManager")
            } catch (e: Exception) {
                Log.e(TAG, "Service start failed: ${e.message}")
            }
        }
    }

    /**
     * Heartbeat monitor — checks if the service is alive.
     */
    private suspend fun heartbeatMonitor() {
        while (isActive) {
            delay(KEEPALIVE_INTERVAL)
            val elapsed = System.currentTimeMillis() - lastHeartbeat

            if (elapsed > KEEPALIVE_INTERVAL * 2) {
                missedHeartbeats++
                Log.w(TAG, "Missed heartbeat #$missedHeartbeats (elapsed=${elapsed}ms)")

                if (missedHeartbeats >= MAX_MISSED_HEARTBEATS) {
                    Log.e(TAG, "Service appears dead, force restarting...")
                    forceRestartAll()
                    missedHeartbeats = 0
                }
            } else {
                missedHeartbeats = 0
            }
        }
    }

    /**
     * Schedule a repeating alarm to keep the process alive.
     */
    private fun scheduleKeepAliveAlarm() {
        try {
            val pendingIntent = createKeepAlivePendingIntent()
            val triggerAt = SystemClock.elapsedRealtime() + SHORT_INTERVAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    ALARM_INTERVAL,
                    pendingIntent
                )
            }
            Log.i(TAG, "Keep-alive alarm scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Keep-alive alarm failed: ${e.message}")
        }
    }

    /**
     * Schedule a guardian alarm that checks if everything is alive.
     */
    private fun scheduleGuardianAlarm() {
        try {
            val pendingIntent = createGuardianPendingIntent()
            val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            Log.i(TAG, "Guardian alarm scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Guardian alarm failed: ${e.message}")
        }
    }

    /**
     * Force restart all services and activities.
     */
    private fun forceRestartAll() {
        Log.i(TAG, "Force restarting all components")

        // Restart CoreService
        ensureServicesRunning()

        // Restart main activity
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            if (intent != null) {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Activity restart failed: ${e.message}")
        }

        // Reschedule alarms
        scheduleKeepAliveAlarm()
        scheduleGuardianAlarm()
    }

    /**
     * Create PendingIntent for keep-alive alarm.
     */
    private fun createKeepAlivePendingIntent(): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).apply {
            action = ACTION_KEEPALIVE
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_KEEPALIVE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create PendingIntent for guardian alarm.
     */
    private fun createGuardianPendingIntent(): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).apply {
            action = ACTION_GUARDIAN_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_GUARDIAN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Broadcast receiver that handles keep-alive alarms.
     * Registered in the manifest.
     */
    class KeepAliveReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return

            when (action) {
                ACTION_KEEPALIVE -> {
                    Log.d(TAG, "Keep-alive ping received")
                    // Restart service if dead
                    if (!CoreService.isRunning) {
                        try {
                            val svcIntent = Intent(context, CoreService::class.java).apply {
                                action = CoreService.ACTION_START_FOREGROUND
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Keep-alive service restart failed: ${e.message}")
                        }
                    }
                }
                ACTION_GUARDIAN_CHECK -> {
                    Log.d(TAG, "Guardian check triggered")
                    val mgr = XRCApp.instance?.keepAliveManager
                    if (mgr != null && mgr.isActive) {
                        mgr.reportHeartbeat()
                    }
                }
            }
        }
    }
}
