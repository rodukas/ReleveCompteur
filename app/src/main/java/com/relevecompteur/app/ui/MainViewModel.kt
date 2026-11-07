package com.relevecompteur.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relevecompteur.app.data.local.AppDatabase
import com.relevecompteur.app.data.local.AppSettingsState
import com.relevecompteur.app.data.local.UserPreferencesRepository
import com.relevecompteur.app.data.remote.GoogleAuthManager
import com.relevecompteur.app.data.remote.GoogleSheetsServiceWrapper
import com.relevecompteur.app.data.remote.extractConsentIntentOrNull
import com.relevecompteur.app.data.remote.formatErrorMessage
import com.relevecompteur.app.data.repository.MeterRepository
import com.relevecompteur.app.data.vision.MlKitOcrProcessor
import com.relevecompteur.app.data.vision.MlKitVisualMeterClassifier
import com.relevecompteur.app.data.vision.MultimodalPromptMeterClassifier
import com.relevecompteur.app.domain.model.ChartMetricMode
import com.relevecompteur.app.domain.model.LeakAlert
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.ProjectionTimeframe
import com.relevecompteur.app.domain.model.TariffConfig
import com.relevecompteur.app.domain.model.TotalCostProjectionReport
import com.relevecompteur.app.domain.model.YearlyTrendSeries
import com.relevecompteur.app.domain.usecase.CostProjectionEngine
import com.relevecompteur.app.domain.usecase.LeakDetectionEngine
import com.relevecompteur.app.domain.vision.ClassifierEngineMode
import com.relevecompteur.app.domain.vision.MeterRecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToLong

sealed interface AuthUiEvent {
    data class LaunchConsent(val intent: Intent) : AuthUiEvent
}

data class CaptureModalUiState(
    val isVisible: Boolean = false,
    val isAnalyzing: Boolean = false,
    val recognitionResult: MeterRecognitionResult? = null,
    val selectedMeterType: MeterType = MeterType.EDF,
    val rawValueInput: String = "",
    val commentInput: String = "",
    val captureDate: LocalDate = LocalDate.now()
) {
    val parsedRawValue: Double
        get() = rawValueInput.replace(",", ".").trim().toDoubleOrNull() ?: 0.0

    fun computeNormalizedValue(offsets: Map<MeterType, Double>): Double {
        val offset = offsets[selectedMeterType] ?: selectedMeterType.defaultOffset
        return parsedRawValue + offset
    }
}

data class TrendsUiState(
    val selectedMeter: MeterType = MeterType.EAU_GEN,
    val metricMode: ChartMetricMode = ChartMetricMode.NORMALIZED_CUMULATIVE,
    val yearlySeries: List<YearlyTrendSeries> = emptyList(),
    val selectedMeterLeakAlert: LeakAlert? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    val prefsRepository = UserPreferencesRepository(appContext)
    val googleAuthManager = GoogleAuthManager(appContext)
    private val sheetsService = GoogleSheetsServiceWrapper(appContext)

    val repository = MeterRepository(
        context = appContext,
        dao = database.meterReadingDao(),
        prefsRepository = prefsRepository,
        sheetsService = sheetsService
    )

    private val ocrProcessor = MlKitOcrProcessor()

    @Volatile
    private var cachedLatestRawByMeter: Map<MeterType, Double> = emptyMap()

    private val mlKitVisualClassifier = MlKitVisualMeterClassifier(
        context = appContext,
        historicalLastRawValues = { cachedLatestRawByMeter }
    )
    private val multimodalClassifier = MultimodalPromptMeterClassifier(mlKitVisualClassifier)

    val settingsState: StateFlow<AppSettingsState> = prefsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettingsState()
        )

    val allReadingsState: StateFlow<List<MeterReading>> = repository.allReadingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // One-time OAuth consent events (UserRecoverableAuthException)
    private val _authUiEvent = MutableSharedFlow<AuthUiEvent>(extraBufferCapacity = 1)
    val authUiEvent: SharedFlow<AuthUiEvent> = _authUiEvent.asSharedFlow()

    private var pendingRetryAfterConsent: (() -> Unit)? = null

    // Activity 1: Capture & OCR State
    private val _captureModalState = MutableStateFlow(CaptureModalUiState())
    val captureModalState: StateFlow<CaptureModalUiState> = _captureModalState.asStateFlow()

    // Activity 2: Trends & Leak Detection State
    private val _selectedTrendMeter = MutableStateFlow(MeterType.EAU_GEN)
    private val _selectedChartMetric = MutableStateFlow(ChartMetricMode.NORMALIZED_CUMULATIVE)

    val trendsUiState: StateFlow<TrendsUiState> = combine(
        allReadingsState,
        _selectedTrendMeter,
        _selectedChartMetric
    ) { readings, meter, metricMode ->
        cachedLatestRawByMeter = readings
            .groupBy { it.meterType }
            .mapValues { (_, list) -> list.maxBy { it.date }.rawValue }

        val series = LeakDetectionEngine.buildYearlySeries(readings, meter, metricMode)
        val currentAlert = if (meter.isWaterMeter) {
            LeakDetectionEngine.analyzeWaterLeakHeuristics(readings, meter)
        } else null

        TrendsUiState(
            selectedMeter = meter,
            metricMode = metricMode,
            yearlySeries = series,
            selectedMeterLeakAlert = currentAlert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrendsUiState()
    )

    // Activity 3: Cost Projection & Tariff Simulator State
    private val _selectedTimeframe = MutableStateFlow(ProjectionTimeframe.LAST_YEAR)
    val selectedTimeframe: StateFlow<ProjectionTimeframe> = _selectedTimeframe.asStateFlow()

    val costProjectionState: StateFlow<TotalCostProjectionReport> = combine(
        allReadingsState,
        settingsState,
        _selectedTimeframe
    ) { readings, settings, timeframe ->
        CostProjectionEngine.calculateProjections(
            allReadings = readings,
            tariffConfig = settings.tariffConfig,
            timeframe = timeframe
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CostProjectionEngine.calculateProjections(
            allReadings = emptyList(),
            tariffConfig = TariffConfig(),
            timeframe = ProjectionTimeframe.LAST_YEAR
        )
    )

    // Global UI Notifications & Sync status
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSyncingSheet = MutableStateFlow(false)
    val isSyncingSheet: StateFlow<Boolean> = _isSyncingSheet.asStateFlow()

    private val _sheetPermissionStatus = MutableStateFlow<String?>(null)
    val sheetPermissionStatus: StateFlow<String?> = _sheetPermissionStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeededFromSampleCsv()
            cachedLatestRawByMeter = repository.getLatestRawValuesByMeter()
            // Restore signed-in Google account if already authenticated
            googleAuthManager.getLastSignedInAccount()?.email?.let { email ->
                if (email.isNotBlank()) {
                    prefsRepository.updateGoogleAccountEmail(email)
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    // =========================================================================
    // Activity 1: Capture & Optical Recognition Actions
    // =========================================================================

    fun onBitmapCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _captureModalState.update { it.copy(isAnalyzing = true) }
            val result = runOpticalPipeline(bitmap)
            _captureModalState.update {
                CaptureModalUiState(
                    isVisible = true,
                    isAnalyzing = false,
                    recognitionResult = result,
                    selectedMeterType = result.detectedMeterType,
                    rawValueInput = result.extractedRawValue.roundToLong().toString(),
                    commentInput = "",
                    captureDate = LocalDate.now()
                )
            }
        }
    }

    /**
     * Loads one of the bundled reference meter images from `assets/meter_references/`
     * and runs the live ML Kit OCR + Visual Classification pipeline on it.
     */
    fun analyzeBundledReferenceImage(meterType: MeterType) {
        viewModelScope.launch {
            _captureModalState.update { it.copy(isAnalyzing = true) }
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.assets.open(meterType.referenceAssetPath).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bmp == null) {
                _captureModalState.update { it.copy(isAnalyzing = false) }
                showMessage("Impossible de charger l'image de référence ${meterType.referenceAssetPath}")
                return@launch
            }
            val result = runOpticalPipeline(bmp)
            _captureModalState.update {
                CaptureModalUiState(
                    isVisible = true,
                    isAnalyzing = false,
                    recognitionResult = result,
                    selectedMeterType = result.detectedMeterType,
                    rawValueInput = result.extractedRawValue.roundToLong().toString(),
                    commentInput = "",
                    captureDate = LocalDate.now()
                )
            }
        }
    }

    private suspend fun runOpticalPipeline(sourceBitmap: Bitmap): MeterRecognitionResult {
        val ocrResult = ocrProcessor.recognizeBestOrientation(sourceBitmap)
        val activeClassifier = when (settingsState.value.classifierMode) {
            ClassifierEngineMode.MLKIT_VISUAL_TEMPLATE -> mlKitVisualClassifier
            ClassifierEngineMode.GEMINI_NANO_MULTIMODAL -> multimodalClassifier
        }
        return activeClassifier.classifyAndExtract(
            frameBitmap = ocrResult.orientedBitmap,
            ocrElements = ocrResult.elements
        )
    }

    fun openManualEntryModal(defaultMeter: MeterType = MeterType.EDF) {
        val lastRaw = cachedLatestRawByMeter[defaultMeter]?.roundToLong()?.toString().orEmpty()
        _captureModalState.value = CaptureModalUiState(
            isVisible = true,
            isAnalyzing = false,
            recognitionResult = null,
            selectedMeterType = defaultMeter,
            rawValueInput = lastRaw,
            commentInput = "",
            captureDate = LocalDate.now()
        )
    }

    fun updateModalMeterType(meterType: MeterType) {
        _captureModalState.update { it.copy(selectedMeterType = meterType) }
    }

    fun updateModalRawValue(rawText: String) {
        _captureModalState.update { it.copy(rawValueInput = rawText) }
    }

    fun updateModalComment(comment: String) {
        _captureModalState.update { it.copy(commentInput = comment) }
    }

    fun dismissCaptureModal() {
        _captureModalState.update { it.copy(isVisible = false, isAnalyzing = false) }
    }

    fun confirmAndSaveCapture() {
        val state = _captureModalState.value
        val rawVal = state.parsedRawValue
        val offsets = settingsState.value.meterOffsets
        val normVal = state.computeNormalizedValue(offsets)

        _captureModalState.update { it.copy(isVisible = false) }

        viewModelScope.launch {
            val outcome = repository.recordConfirmedReading(
                meterType = state.selectedMeterType,
                rawValue = rawVal,
                normalizedValue = normVal,
                comment = state.commentInput,
                captureDate = state.captureDate
            )
            outcome.consentIntent?.let { intent ->
                pendingRetryAfterConsent = { syncWithGoogleSheetNow() }
                _authUiEvent.emit(AuthUiEvent.LaunchConsent(intent))
            }
            showMessage(outcome.userMessage)
        }
    }

    // =========================================================================
    // Activity 2: Multi-Year Trends & Leak Detection Actions
    // =========================================================================

    fun selectTrendMeter(meterType: MeterType) {
        _selectedTrendMeter.value = meterType
    }

    fun selectChartMetricMode(mode: ChartMetricMode) {
        _selectedChartMetric.value = mode
    }

    // =========================================================================
    // Activity 3: Cost Projection & Tariff Simulator Actions
    // =========================================================================

    fun selectProjectionTimeframe(timeframe: ProjectionTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun updateTariffConfig(newConfig: TariffConfig) {
        viewModelScope.launch {
            prefsRepository.updateTariffConfig(newConfig)
        }
    }

    // =========================================================================
    // Settings, Offsets & Google Sheets OAuth Actions
    // =========================================================================

    fun onGoogleAccountSignedIn(email: String) {
        viewModelScope.launch {
            prefsRepository.updateGoogleAccountEmail(email)
            showMessage("Connecté avec le compte Google : $email")
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            prefsRepository.updateGoogleAccountEmail("")
            _sheetPermissionStatus.value = null
            showMessage("Compte Google déconnecté.")
        }
    }

    fun saveSpreadsheetConfig(rawSpreadsheetIdOrUrl: String, tabName: String) {
        viewModelScope.launch {
            prefsRepository.updateSpreadsheetId(rawSpreadsheetIdOrUrl, tabName)
            showMessage("Identifiant Google Sheet enregistré.")
        }
    }

    /**
     * Called when the user grants OAuth remote consent from the recovery dialog.
     * Retries the sheet R/W verification (or the pending sheet operation).
     */
    fun onConsentGranted() {
        val retryAction = pendingRetryAfterConsent
        pendingRetryAfterConsent = null
        if (retryAction != null) {
            retryAction.invoke()
        } else {
            verifyGoogleSheetPermissions()
        }
    }

    fun verifyGoogleSheetPermissions() {
        viewModelScope.launch {
            _isSyncingSheet.value = true
            val result = repository.verifySheetPermissions()
            _isSyncingSheet.value = false
            result.onSuccess { check ->
                _sheetPermissionStatus.value = check.statusMessage
                showMessage(check.statusMessage)
            }.onFailure { err ->
                val consentIntent = err.extractConsentIntentOrNull()
                if (consentIntent != null) {
                    pendingRetryAfterConsent = { verifyGoogleSheetPermissions() }
                    _sheetPermissionStatus.value =
                        "Autorisation Google requise : veuillez accepter l'accès dans la fenêtre Google…"
                    _authUiEvent.emit(AuthUiEvent.LaunchConsent(consentIntent))
                } else {
                    val formattedErr = err.formatErrorMessage()
                    val msg = "Échec de vérification des permissions : $formattedErr"
                    _sheetPermissionStatus.value = msg
                    showMessage(msg)
                }
            }
        }
    }

    fun syncWithGoogleSheetNow() {
        viewModelScope.launch {
            _isSyncingSheet.value = true
            val result = repository.pullFromGoogleSheet()
            _isSyncingSheet.value = false
            result.onSuccess { count ->
                showMessage("Synchronisation réussie : $count relevés chargés depuis Google Sheets.")
            }.onFailure { err ->
                val consentIntent = err.extractConsentIntentOrNull()
                if (consentIntent != null) {
                    pendingRetryAfterConsent = { syncWithGoogleSheetNow() }
                    _sheetPermissionStatus.value =
                        "Autorisation Google requise : veuillez accepter l'accès dans la fenêtre Google…"
                    _authUiEvent.emit(AuthUiEvent.LaunchConsent(consentIntent))
                } else {
                    val formattedErr = err.formatErrorMessage()
                    showMessage("Erreur de synchronisation Google Sheets : $formattedErr")
                }
            }
        }
    }

    fun updateMeterOffset(meterType: MeterType, newOffset: Double) {
        viewModelScope.launch {
            prefsRepository.updateMeterOffset(meterType, newOffset)
        }
    }

    fun resetOffsetsFromSampleCsvAnalysis() {
        viewModelScope.launch {
            val offsets = repository.reAnalyzeOffsetsFromSampleCsv()
            val eauGenOffset = offsets[MeterType.EAU_GEN]?.toLong() ?: 4377L
            showMessage("Offsets recalculés depuis GoogleSheetSample.csv (Eau generale = +$eauGenOffset m³).")
        }
    }

    fun updateClassifierMode(mode: ClassifierEngineMode) {
        viewModelScope.launch {
            prefsRepository.updateClassifierMode(mode)
        }
    }
}
