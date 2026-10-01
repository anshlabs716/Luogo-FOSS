package app.luogo.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luogo.app.ui.screens.HistoryScreen
import app.luogo.app.ui.screens.ItemsScreen
import app.luogo.app.ui.screens.MapScreen
import app.luogo.app.ui.screens.PeopleScreen
import app.luogo.app.ui.screens.PlacesScreen
import app.luogo.app.ui.screens.SettingsScreen
import app.luogo.app.ui.theme.LuogoTheme
import app.luogo.app.ui.viewmodel.LuogoViewModel
import app.luogo.app.ui.viewmodel.PrimaryTab

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = LuogoApplication.getRepository(this)
        handleIncomingIntent(intent, repository)

        setContent {
            LuogoTheme {
                val vm: LuogoViewModel = viewModel(factory = LuogoViewModel.Factory(repository))
                LuogoMainApp(
                    viewModel = vm,
                    isBatteryOptimizationExempt = { isIgnoringBatteryOptimizations() },
                    onRequestBatteryOptimizationExemption = { requestIgnoreBatteryOptimizations() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val repository = LuogoApplication.getRepository(this)
        handleIncomingIntent(intent, repository)
    }

    private fun handleIncomingIntent(intent: Intent?, repository: app.luogo.app.data.repository.LuogoRepository) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
                if (sharedText.startsWith("luogo-invite-key:")) {
                    repository.appendLog("Received shared group invite payload via Android Share Sheet")
                }
            }
            Intent.ACTION_VIEW -> {
                val data: Uri = intent.data ?: return
                repository.appendLog("Opened deep link: $data")
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}

@Composable
fun LuogoMainApp(
    viewModel: LuogoViewModel,
    isBatteryOptimizationExempt: () -> Boolean,
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val toastMsg by viewModel.statusToast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPermissionExplanationModal by remember { mutableStateOf(false) }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.repository.hardwareLocationManager.startLiveTracking()
        }
    }

    val requestLocationAndActivityPermissions = {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        multiPermissionLauncher.launch(perms)
    }

    val requestBlePermissions = {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        multiPermissionLauncher.launch(perms)
    }

    DisposableEffect(Unit) {
        viewModel.repository.hardwareLocationManager.startLiveTracking()
        viewModel.repository.relayClient.startLiveWebSocket()
        onDispose { }
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = selectedTab != PrimaryTab.MAP) {
        viewModel.selectTab(PrimaryTab.MAP)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        PrimaryTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon(),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        PrimaryTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon(),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedTab) {
                        PrimaryTab.MAP -> MapScreen(
                            viewModel = viewModel,
                            onRequestLocationPermission = requestLocationAndActivityPermissions
                        )
                        PrimaryTab.PEOPLE -> PeopleScreen(viewModel = viewModel)
                        PrimaryTab.ITEMS -> ItemsScreen(
                            viewModel = viewModel,
                            onRequestBlePermissions = requestBlePermissions
                        )
                        PrimaryTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                        PrimaryTab.PLACES -> PlacesScreen(viewModel = viewModel)
                        PrimaryTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            isBatteryExempt = isBatteryOptimizationExempt(),
                            onRequestBatteryExemption = onRequestBatteryOptimizationExemption,
                            onRequestPermissions = { showPermissionExplanationModal = true }
                        )
                    }
                }
            }
        }
    }

    if (showPermissionExplanationModal) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanationModal = false },
            title = { Text("Why Luogo-FOSS Requests Permissions") },
            text = {
                Column {
                    Text("• Precise & Background Location: Required to fuse GNSS + Wi-Fi + Inertial sensors for ~2-second moving location updates and saved-place geofencing.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Bluetooth / Nearby Devices: Required to detect and ring your personal items (e.g. Pixel Buds 3) and scan for unknown trackers.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Activity Recognition: Adapts tracking frequency between moving (~2s) and stationary states to save battery.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Notifications: Displays persistent live-sharing status, place arrival/departure alerts, and item detections.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanationModal = false
                        requestLocationAndActivityPermissions()
                        requestBlePermissions()
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanationModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun PrimaryTab.icon(): ImageVector = when (this) {
    PrimaryTab.MAP -> Icons.Default.Map
    PrimaryTab.PEOPLE -> Icons.Default.Groups
    PrimaryTab.ITEMS -> Icons.Default.Radar
    PrimaryTab.HISTORY -> Icons.Default.History
    PrimaryTab.PLACES -> Icons.Default.Place
    PrimaryTab.SETTINGS -> Icons.Default.Settings
}
