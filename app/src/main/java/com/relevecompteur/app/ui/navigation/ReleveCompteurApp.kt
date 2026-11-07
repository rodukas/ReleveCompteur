package com.relevecompteur.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relevecompteur.app.ui.AuthUiEvent
import com.relevecompteur.app.ui.MainViewModel
import com.relevecompteur.app.ui.capture.CaptureScreen
import com.relevecompteur.app.ui.costs.CostSimulatorScreen
import com.relevecompteur.app.ui.settings.SettingsModalSheet
import com.relevecompteur.app.ui.trends.TrendsScreen

enum class PrimaryScreenTab(
    val title: String,
    val icon: ImageVector
) {
    CAPTURE("Capturer", Icons.Default.CameraAlt),
    TRENDS("Tendances", Icons.Default.ShowChart),
    COSTS("Coûts & Tarifs", Icons.Default.Euro)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleveCompteurApp(viewModel: MainViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val allReadings by viewModel.allReadingsState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val captureModalState by viewModel.captureModalState.collectAsState()
    val trendsState by viewModel.trendsUiState.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val costReport by viewModel.costProjectionState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val isSyncingSheet by viewModel.isSyncingSheet.collectAsState()
    val permissionStatus by viewModel.sheetPermissionStatus.collectAsState()

    var currentTab by remember { mutableStateOf(PrimaryScreenTab.CAPTURE) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Fallback consent launcher when SettingsModalSheet is closed (e.g., during capture save)
    val appLevelConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentGranted()
        }
    }

    LaunchedEffect(showSettingsSheet) {
        if (!showSettingsSheet) {
            viewModel.authUiEvent.collect { event ->
                when (event) {
                    is AuthUiEvent.LaunchConsent -> appLevelConsentLauncher.launch(event.intent)
                }
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Relevé Compteurs",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Sync status & pending queue badge
                    IconButton(onClick = { showSettingsSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (pendingSyncCount > 0) {
                                    Badge { Text(pendingSyncCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (settings.googleAccountEmail.isNotBlank() && settings.spreadsheetId.isNotBlank()) {
                                    Icons.Default.CloudDone
                                } else {
                                    Icons.Default.CloudQueue
                                },
                                contentDescription = "État synchronisation Google Sheet"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuration Google Sheets et Offsets"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                PrimaryScreenTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = (currentTab == tab),
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                PrimaryScreenTab.CAPTURE -> {
                    CaptureScreen(
                        modalState = captureModalState,
                        meterOffsets = settings.meterOffsets,
                        recentReadings = allReadings,
                        onBitmapCaptured = viewModel::onBitmapCaptured,
                        onTestReferenceAsset = viewModel::analyzeBundledReferenceImage,
                        onOpenManualModal = viewModel::openManualEntryModal,
                        onUpdateModalMeterType = viewModel::updateModalMeterType,
                        onUpdateModalRawValue = viewModel::updateModalRawValue,
                        onUpdateModalComment = viewModel::updateModalComment,
                        onCancelModal = viewModel::dismissCaptureModal,
                        onRetakeCapture = viewModel::dismissCaptureModal,
                        onConfirmAndSave = viewModel::confirmAndSaveCapture
                    )
                }

                PrimaryScreenTab.TRENDS -> {
                    TrendsScreen(
                        state = trendsState,
                        onSelectMeter = viewModel::selectTrendMeter,
                        onSelectMetricMode = viewModel::selectChartMetricMode
                    )
                }

                PrimaryScreenTab.COSTS -> {
                    CostSimulatorScreen(
                        selectedTimeframe = selectedTimeframe,
                        tariffConfig = settings.tariffConfig,
                        projectionReport = costReport,
                        onSelectTimeframe = viewModel::selectProjectionTimeframe,
                        onUpdateTariffConfig = viewModel::updateTariffConfig
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsModalSheet(
            settings = settings,
            googleAuthManager = viewModel.googleAuthManager,
            authUiEvent = viewModel.authUiEvent,
            isSyncing = isSyncingSheet,
            permissionStatusMessage = permissionStatus,
            onGoogleSignedIn = viewModel::onGoogleAccountSignedIn,
            onGoogleSignOut = viewModel::signOutGoogleAccount,
            onConsentGranted = viewModel::onConsentGranted,
            onSaveSpreadsheetConfig = viewModel::saveSpreadsheetConfig,
            onVerifyPermissions = viewModel::verifyGoogleSheetPermissions,
            onSyncSheetNow = viewModel::syncWithGoogleSheetNow,
            onUpdateMeterOffset = viewModel::updateMeterOffset,
            onResetOffsetsFromCsv = viewModel::resetOffsetsFromSampleCsvAnalysis,
            onSelectClassifierMode = viewModel::updateClassifierMode,
            onDismiss = { showSettingsSheet = false }
        )
    }
}
