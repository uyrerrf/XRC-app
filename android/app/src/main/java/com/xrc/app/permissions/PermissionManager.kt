package com.xrc.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.xrc.app.XRCApp
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates permission requests, checks, and status reporting.
 * Has multiple backup methods for granting each permission.
 */
object PermissionManager {

    const val TAG = "PermissionManager"

    // Tier 1: Normal permissions (auto-granted at install time)
    private val normalPermissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.VIBRATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.SCHEDULE_EXACT_ALARM
    )

    // Tier 2: Normal dangerous permissions (runtime requested)
    private val dangerousPermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.WRITE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    // Tier 3: Special permissions (settings intent required)
    private val specialPermissions = listOf(
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "REQUEST_INSTALL_PACKAGES",
        "MANAGE_EXTERNAL_STORAGE",
        "NOTIFICATION_LISTENER",
        "BATTERY_OPTIMIZATIONS",
        "ACCESSIBILITY_SERVICE",
        "DEVICE_ADMIN"
    )

    // Tier 4: Hidden OEM auto-start permissions
    private val oemAutoStartIntents = mapOf(
        "xiaomi" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "samsung" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "oppo" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "vivo" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "oneplus" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "huawei" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )

    private val permissionStatus = ConcurrentHashMap<String, Boolean>()

    fun checkAllPermissions(context: Context): JSONObject {
        val status = JSONObject()

        // Check dangerous permissions
        for (perm in dangerousPermissions) {
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            status.put(perm, granted)
            permissionStatus[perm] = granted
        }

        // Check special permissions
        status.put("SYSTEM_ALERT_WINDOW", hasOverlayPermission(context))
        status.put("WRITE_SETTINGS", hasWriteSettingsPermission(context))
        status.put("REQUEST_INSTALL_PACKAGES", hasInstallPackagesPermission(context))
        status.put("MANAGE_EXTERNAL_STORAGE", hasManageStoragePermission(context))
        status.put("BATTERY_OPTIMIZATIONS", hasBatteryOptimizationPermission(context))
        status.put("NOTIFICATION_LISTENER", hasNotificationListenerPermission(context))

        return status
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true
    }

    fun hasInstallPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun hasManageStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBatteryOptimizationPermission(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return listeners?.contains(context.packageName) == true
    }

    fun openPermissionSettings(context: Context, permission: String) {
        val intent = when (permission) {
            "SYSTEM_ALERT_WINDOW" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            "WRITE_SETTINGS" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            "REQUEST_INSTALL_PACKAGES" -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            "MANAGE_EXTERNAL_STORAGE" -> Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            "BATTERY_OPTIMIZATIONS" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            "ACCESSIBILITY_SERVICE" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "NOTIFICATION_LISTENER" -> Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isPermissionGranted(permission: String): Boolean {
        return permissionStatus[permission] ?: false
    }
}

// Need to import Environment for MANAGE_EXTERNAL_STORAGE check
import android.os.Environment
