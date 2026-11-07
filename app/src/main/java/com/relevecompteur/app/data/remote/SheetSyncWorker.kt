package com.relevecompteur.app.data.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.relevecompteur.app.data.local.AppDatabase
import com.relevecompteur.app.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background CoroutineWorker that synchronizes any locally cached unsynced meter captures
 * to the configured Google Sheet once network connectivity and credentials are available.
 */
class SheetSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.meterReadingDao()
        val prefs = UserPreferencesRepository(applicationContext).settingsFlow.first()

        if (prefs.googleAccountEmail.isBlank() || prefs.spreadsheetId.isBlank()) {
            return Result.success()
        }

        val pendingEntities = dao.getPendingSyncReadings()
        if (pendingEntities.isEmpty()) {
            return Result.success()
        }

        val sheetsWrapper = GoogleSheetsServiceWrapper(applicationContext)
        var allSucceeded = true

        for (entity in pendingEntities) {
            val domainReading = entity.toDomainOrNull() ?: continue
            val appendResult = sheetsWrapper.appendMeterReading(
                accountEmail = prefs.googleAccountEmail,
                spreadsheetId = prefs.spreadsheetId,
                sheetTabName = prefs.sheetTabName,
                reading = domainReading
            )
            if (appendResult.isSuccess) {
                dao.markAsSynced(entity.id)
            } else {
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "google_sheets_meter_sync"

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SheetSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

