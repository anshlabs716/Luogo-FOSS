package app.luogo.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luogo.app.ui.screens.HistoryScreen
import app.luogo.app.ui.screens.ItemsScreen
import app.luogo.app.ui.screens.MapScreen
import app.luogo.app.ui.screens.PeopleScreen
import app.luogo.app.ui.screens.PlacesScreen
import app.luogo.app.ui.screens.SettingsScreen
import app.luogo.app.ui.theme.LuogoSpacing
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
                    repository.appendLog("Received a group invite through the Android share sheet")
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
    var showPermissionExplanation by remember { mutableStateOf(false) }

    // Permission requests are grouped by the feature that needs them, so the app never asks
    // for everything at once on first launch.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.repository.hardwareLocationManager.startLiveTracking()
        } else {
            viewModel.postToast("Location permission denied. The map needs it to show where you are.")
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.postToast("Bluetooth permission granted")
        } else {
            viewModel.postToast("Bluetooth permission denied. Item finding needs it.")
        }
    }

    val requestLocationPermissions = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        locationPermissionLauncher.launch(permissions.toTypedArray())
    }

    val requestBluetoothPermissions = {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        bluetoothPermissionLauncher.launch(permissions.toTypedArray())
    }

    // Only ask once the user has opened the map, which is the feature that needs location.
    LaunchedEffect(Unit) {
        viewModel.repository.hardwareLocationManager.startLiveTracking()
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= EXPANDED_WIDTH_BREAKPOINT

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!expanded) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        PrimaryTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(tab.icon(), contentDescription = null) },
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
                if (expanded) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        PrimaryTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(tab.icon(), contentDescription = null) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (selectedTab) {
                        PrimaryTab.MAP -> MapScreen(
                            viewModel = viewModel,
                            onRequestLocationPermission = requestLocationPermissions
                        )
                        PrimaryTab.PEOPLE -> PeopleScreen(viewModel = viewModel)
                        PrimaryTab.ITEMS -> ItemsScreen(
                            viewModel = viewModel,
                            onRequestBlePermissions = requestBluetoothPermissions
                        )
                        PrimaryTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                        PrimaryTab.PLACES -> PlacesScreen(viewModel = viewModel)
                        PrimaryTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            isBatteryExempt = isBatteryOptimizationExempt(),
                            onRequestBatteryExemption = onRequestBatteryOptimizationExemption,
                            onRequestPermissions = { showPermissionExplanation = true }
                        )
                    }
                }
            }
        }
    }

    if (showPermissionExplanation) {
        PermissionExplanationDialog(
            onDismiss = { showPermissionExplanation = false },
            onGrant = {
                showPermissionExplanation = false
                requestLocationPermissions()
                requestBluetoothPermissions()
            }
        )
    }
}

private val EXPANDED_WIDTH_BREAKPOINT = 600.dp

/**
 * Explains what each permission is for before the system dialog appears.
 *
 * Requesting a permission without a stated reason is how apps train people to tap "Don't
 * allow", so the reason comes first.
 */
@Composable
private fun PermissionExplanationDialog(
    onDismiss: () -> Unit,
    onGrant: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why Luogo-FOSS needs these permissions") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PermissionReason(
                    title = "Precise location",
                    body = "Fuses GNSS, Wi-Fi and motion sensors for live position while moving, " +
                        "and runs saved-place arrival and departure alerts."
                )
                PermissionReason(
                    title = "Background location",
                    body = "Lets sharing and geofencing keep working when the app is closed. " +
                        "Android shows a persistent notification while this is active."
                )
                PermissionReason(
                    title = "Notifications",
                    body = "Shows sharing status, arrival and departure alerts, and item detections."
                )
                PermissionReason(
                    title = "Nearby devices",
                    body = "Needed to detect your own items advertising a rotating identifier and to " +
                        "flag unknown trackers following you. No location is attached to another " +
                        "person's device."
                )
                PermissionReason(
                    title = "Physical activity",
                    body = "Tells the app whether you are walking, cycling or driving so tracking " +
                        "can back off when you are stationary."
                )
                Spacer(modifier = Modifier.height(LuogoSpacing.small))
                Text(
                    text = "Everything is stored on this device unless you turn sharing on. " +
                        "There are no ads and no analytics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

@Composable
private fun PermissionReason(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = LuogoSpacing.medium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
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