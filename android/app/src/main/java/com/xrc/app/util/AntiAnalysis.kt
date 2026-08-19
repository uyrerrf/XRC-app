package com.xrc.app.util

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Anti-analysis, anti-emulator, and anti-debugging checks.
 * Detects sandbox environments and evades dynamic analysis.
 */
object AntiAnalysis {

    const val TAG = "AntiAnalysis"

    fun isRunningInEmulator(): Boolean {
        val emulatorSigns = listOf(
            Build.FINGERPRINT.contains("generic", ignoreCase = true),
            Build.FINGERPRINT.contains("emulator", ignoreCase = true),
            Build.MODEL.contains("sdk", ignoreCase = true),
            Build.MODEL.contains("emulator", ignoreCase = true),
            Build.MODEL.contains("google_sdk", ignoreCase = true),
            Build.MANUFACTURER.contains("google", ignoreCase = true) &&
                Build.MODEL.contains("sdk", ignoreCase = true),
            Build.BRAND.contains("generic", ignoreCase = true),
            Build.DEVICE.contains("generic", ignoreCase = true),
            Build.HARDWARE.contains("goldfish", ignoreCase = true),
            Build.HARDWARE.contains("ranchu", ignoreCase = true),
            Build.HARDWARE.contains("cutf", ignoreCase = true),
            Build.HARDWARE.contains("unknown", ignoreCase = true),
            Build.PRODUCT.contains("sdk", ignoreCase = true),
            Build.PRODUCT.contains("emulator", ignoreCase = true),
            Build.PRODUCT.contains("google_sdk", ignoreCase = true),
            Build.BOARD.lowercase().contains("unknown"),
            Build.BOOTLOADER.lowercase().contains("unknown"),
            Build.SERIAL == "unknown" || Build.SERIAL == "null"
        )

        // Check for QEMU
        val qemuSigns = listOf(
            File("/system/lib/libc.so").exists() && File("/system/lib/libc.so").length() < 100000,
            File("/system/bin/qemu-props").exists(),
            File("/dev/socket/qemud").exists(),
            File("/dev/qemu_pipe").exists()
        )

        // Check for emulator props
        val propsSigns = try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop ro.kernel.qemu 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() == "1"
        } catch (e: Exception) { false }

        return emulatorSigns.any { it } || qemuSigns.any { it } || propsSigns
    }

    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    fun isDeviceRooted(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/system/xbin/daemonsu",
            "/system/etc/init.d/99SuperSUDaemon",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/tmp/su"
        )
        return rootPaths.any { File(it).exists() }
    }

    fun isTraceRunning(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/self/status | grep -i tracer"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            line != null && !line.contains("0\t")
        } catch (e: Exception) { false }
    }

    fun shouldEvade(): Boolean {
        val reasons = mutableListOf<String>()
        if (isRunningInEmulator()) reasons.add("emulator")
        if (isDebuggerAttached()) reasons.add("debugger")
        if (isTraceRunning()) reasons.add("trace")
        if (reasons.isNotEmpty()) {
            Log.w(TAG, "Evasion triggered: ${reasons.joinToString(", ")}")
            return true
        }
        return false
    }

    fun getSafeDeviceId(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.VERSION.RELEASE}_${Build.VERSION.SDK_INT}"
    }
}
