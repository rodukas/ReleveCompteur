package com.relevecompteur.app.data.repository

import android.content.Context
import android.content.Intent
import com.relevecompteur.app.data.local.MeterReadingDao
import com.relevecompteur.app.data.local.MeterReadingEntity
import com.relevecompteur.app.data.local.SampleCsvParser
import com.relevecompteur.app.data.local.UserPreferencesRepository
import com.relevecompteur.app.data.remote.GoogleSheetsServiceWrapper
import com.relevecompteur.app.data.remote.SheetPermissionCheckResult
import com.relevecompteur.app.data.remote.SheetSyncWorker
import com.relevecompteur.app.data.remote.extractConsentIntentOrNull
import com.relevecompteur.app.data.remote.formatErrorMessage
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.usecase.OffsetAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class CaptureSaveOutcome(
    val savedLocally: Boolean,
    val syncedToGoogleSheet: Boolean,
    val userMessage: String,
    val consentIntent: Intent? = null
)

class MeterRepository(
    private val context: Context,
    private val dao: MeterReadingDao,
    private val prefsRepository: UserPreferencesRepository,
    private val sheetsService: GoogleSheetsServiceWrapper
) {

    val allReadingsFlow: Flow<List<MeterReading>> = dao.observeAllReadings().map { list ->
        list.mapNotNull { it.toDomainOrNull() }
    }

    val pendingSyncCountFlow: Flow<Int> = dao.observePendingSyncCount()

    /**
     * Seeds the local database and initial meter offsets from the bundled GoogleSheetSample.csv
     * on first app launch, or backfills the newly added GAZ meter if missing on an existing install.
     */
    suspend fun ensureSeededFromSampleCsv() = withContext(Dispatchers.IO) {
        val existingCount = dao.countReadings()
        val existingGazCount = dao.countReadingsForMeter(MeterType.GAZ.keyId)

        if (existingCount > 0 && existingGazCount > 0) return@withContext

        val rawRows = SampleCsvParser.loadSampleCsvRows(context)
        if (rawRows.isEmpty()) return@withContext

        val discoveredOffsets = OffsetAnalyzer.extractInitialOffsets(rawRows)
        val normalizedReadings = OffsetAnalyzer.buildNormalizedReadings(rawRows, discoveredOffsets)

        if (existingCount == 0) {
            prefsRepository.updateAllMeterOffsets(discoveredOffsets)
            val entities = normalizedReadings.map { MeterReadingEntity.fromDomain(it.copy(id = 0L)) }
            dao.insertAllReadings(entities)
        } else if (existingGazCount == 0) {
            // Backfill Gaz readings and its +33573 m³ replacement offset for existing installations
            val gazOffset = discoveredOffsets[MeterType.GAZ] ?: MeterType.GAZ.defaultOffset
            prefsRepository.updateMeterOffset(MeterType.GAZ, gazOffset)
            val gazEntities = normalizedReadings
                .filter { it.meterType == MeterType.GAZ }
                .map { MeterReadingEntity.fromDomain(it.copy(id = 0L)) }
            dao.insertAllReadings(gazEntities)
        }
    }

    /**
     * Re-analyzes GoogleSheetSample.csv (or current sheet rows) to reset initial offsets.
     */
    suspend fun reAnalyzeOffsetsFromSampleCsv(): Map<MeterType, Double> = withContext(Dispatchers.IO) {
        val rawRows = SampleCsvParser.loadSampleCsvRows(context)
        val offsets = OffsetAnalyzer.extractInitialOffsets(rawRows)
        prefsRepository.updateAllMeterOffsets(offsets)
        offsets
    }

    /**
     * Returns the most recent raw value per meter for OCR plausibility scoring.
     */
    suspend fun getLatestRawValuesByMeter(): Map<MeterType, Double> = withContext(Dispatchers.IO) {
        val all = dao.getAllReadingsOnce().mapNotNull { it.toDomainOrNull() }
        all.groupBy { it.meterType }
            .mapValues { (_, list) -> list.maxBy { it.date }.rawValue }
    }

    /**
     * Records a confirmed meter capture:
     * 1. Inserts locally into Room cache immediately.
     * 2. Attempts immediate Google Sheets append if signed in and Spreadsheet ID is configured;
     *    otherwise enqueues WorkManager background sync.
     */
    suspend fun recordConfirmedReading(
        meterType: MeterType,
        rawValue: Double,
        normalizedValue: Double,
        comment: String,
        captureDate: LocalDate = LocalDate.now()
    ): CaptureSaveOutcome = withContext(Dispatchers.IO) {
        val dateStr = MeterReading.formatForSheet(captureDate)
        val newReading = MeterReading(
            id = 0L,
            dateString = dateStr,
            date = captureDate,
            meterType = meterType,
            rawValue = rawValue,
            normalizedValue = normalizedValue,
            comment = comment.trim(),
            isSyncedToSheet = false
        )

        val insertedId = dao.insertReading(MeterReadingEntity.fromDomain(newReading))
        val savedReading = newReading.copy(id = insertedId)

        val settings = prefsRepository.settingsFlow.first()
        if (settings.googleAccountEmail.isBlank() || settings.spreadsheetId.isBlank()) {
            SheetSyncWorker.enqueueImmediateSync(context)
            return@withContext CaptureSaveOutcome(
                savedLocally = true,
                syncedToGoogleSheet = false,
                userMessage = "Relevé ${meterType.displayLabel} (${rawValue.toLong()} ${meterType.unit} → " +
                    "norm. ${normalizedValue.toLong()}) enregistré localement (en attente de synchro Google Sheet)."
            )
        }

        val appendResult = sheetsService.appendMeterReading(
            accountEmail = settings.googleAccountEmail,
            spreadsheetId = settings.spreadsheetId,
            sheetTabName = settings.sheetTabName,
            reading = savedReading
        )

        if (appendResult.isSuccess) {
            dao.markAsSynced(insertedId)
            CaptureSaveOutcome(
                savedLocally = true,
                syncedToGoogleSheet = true,
                userMessage = "Relevé ${meterType.displayLabel} synchronisé sur Google Sheets " +
                    "[${dateStr}, ${meterType.displayLabel}, ${rawValue.toLong()}, ${normalizedValue.toLong()}]."
            )
        } else {
            SheetSyncWorker.enqueueImmediateSync(context)
            val err = appendResult.exceptionOrNull()
            val consentIntent = err?.extractConsentIntentOrNull()
            val errMsg = err?.formatErrorMessage() ?: "erreur réseau"
            CaptureSaveOutcome(
                savedLocally = true,
                syncedToGoogleSheet = false,
                userMessage = "Enregistré en cache local. Synchro différée programmée ($errMsg).",
                consentIntent = consentIntent
            )
        }
    }

    /**
     * Verifies Read/Write permissions on the configured Google Sheet.
     */
    suspend fun verifySheetPermissions(): Result<SheetPermissionCheckResult> {
        val settings = prefsRepository.settingsFlow.first()
        return sheetsService.verifyReadWritePermissions(
            accountEmail = settings.googleAccountEmail,
            spreadsheetId = settings.spreadsheetId
        )
    }

    /**
     * Pulls all historical rows from the live Google Sheet, updates offsets, and refreshes local cache.
     */
    suspend fun pullFromGoogleSheet(): Result<Int> = withContext(Dispatchers.IO) {
        val settings = prefsRepository.settingsFlow.first()
        if (settings.googleAccountEmail.isBlank() || settings.spreadsheetId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Compte Google ou ID Google Sheet non configuré.")
            )
        }

        // First push any pending local readings
        val pending = dao.getPendingSyncReadings()
        for (entity in pending) {
            val domain = entity.toDomainOrNull() ?: continue
            val res = sheetsService.appendMeterReading(
                accountEmail = settings.googleAccountEmail,
                spreadsheetId = settings.spreadsheetId,
                sheetTabName = settings.sheetTabName,
                reading = domain
            )
            if (res.isSuccess) {
                dao.markAsSynced(entity.id)
            } else {
                val ex = res.exceptionOrNull()
                if (ex?.extractConsentIntentOrNull() != null) {
                    return@withContext Result.failure(ex)
                }
            }
        }

        val rowsResult = sheetsService.fetchSheetRows(
            accountEmail = settings.googleAccountEmail,
            spreadsheetId = settings.spreadsheetId,
            sheetTabName = settings.sheetTabName
        )

        rowsResult.map { rawSheetValues ->
            val parsedRows = SampleCsvParser.parseGoogleSheetValues(rawSheetValues)
            if (parsedRows.isNotEmpty()) {
                val discoveredOffsets = OffsetAnalyzer.extractInitialOffsets(parsedRows)
                val mergedOffsets = settings.meterOffsets.toMutableMap()
                discoveredOffsets.forEach { (meter, offset) ->
                    if (offset > 0.0) mergedOffsets[meter] = offset
                }
                prefsRepository.updateAllMeterOffsets(mergedOffsets)

                val normalizedReadings = OffsetAnalyzer.buildNormalizedReadings(parsedRows, mergedOffsets)
                dao.deleteAllSyncedReadings()
                dao.insertAllReadings(
                    normalizedReadings.map { MeterReadingEntity.fromDomain(it.copy(id = 0L)) }
                )
                normalizedReadings.size
            } else {
                0
            }
        }
    }
}
