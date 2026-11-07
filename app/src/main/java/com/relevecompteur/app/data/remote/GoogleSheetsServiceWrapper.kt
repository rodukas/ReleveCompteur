package com.relevecompteur.app.data.remote

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import com.relevecompteur.app.domain.model.MeterReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SheetPermissionCheckResult(
    val canRead: Boolean,
    val canWrite: Boolean,
    val spreadsheetTitle: String,
    val firstSheetTitle: String,
    val rowCount: Int,
    val statusMessage: String
)

/**
 * Exception carrying the recovery [Intent] when Google Play Services requires interactive
 * user consent (status: NEED_REMOTE_CONSENT).
 */
class RemoteConsentRequiredException(
    val consentIntent: Intent,
    cause: Throwable
) : Exception("${cause.javaClass.simpleName}: ${cause.localizedMessage ?: cause.message}", cause)

/**
 * Formats any exception so that errors never display as raw "null".
 */
fun Throwable.formatErrorMessage(): String =
    "${this.javaClass.simpleName}: ${this.localizedMessage ?: this.message}"

/**
 * Extracts a [UserRecoverableAuthException] recovery [Intent] from this [Throwable] or its cause chain.
 */
fun Throwable.extractConsentIntentOrNull(): Intent? {
    return when (this) {
        is RemoteConsentRequiredException -> this.consentIntent
        is UserRecoverableAuthException -> this.intent
        is UserRecoverableAuthIOException -> this.intent ?: (this.cause as? UserRecoverableAuthException)?.intent
        else -> (this.cause as? UserRecoverableAuthException)?.intent
            ?: (this.cause as? UserRecoverableAuthIOException)?.intent
    }
}

/**
 * Wrapper around Google Sheets API v4 (com.google.apis:google-api-services-sheets).
 * Verifies read/write permissions and reads/appends 5-column meter rows:
 * [Date, Meter Type, Raw Value, Normalized Value, Comment]
 */
class GoogleSheetsServiceWrapper(private val context: Context) {

    private val oauthScopesString: String =
        "oauth2:${GoogleAuthManager.SCOPE_SPREADSHEETS} ${GoogleAuthManager.SCOPE_DRIVE_FILE}"

    private fun buildSheetsService(accountEmail: String): Sheets {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(
                GoogleAuthManager.SCOPE_SPREADSHEETS,
                GoogleAuthManager.SCOPE_DRIVE_FILE
            )
        ).apply {
            selectedAccount = Account(accountEmail, "com.google")
        }

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ReleveCompteur")
            .build()
    }

    /**
     * Explicitly acquires an OAuth2 token via [GoogleAuthUtil.getToken] so that
     * [UserRecoverableAuthException] (status: NEED_REMOTE_CONSENT) is caught immediately
     * and its recovery [Intent] can be launched by the UI.
     */
    private fun ensureOAuthTokenOrThrowConsent(accountEmail: String) {
        val account = Account(accountEmail, "com.google")
        try {
            GoogleAuthUtil.getToken(context, account, oauthScopesString)
        } catch (e: UserRecoverableAuthException) {
            val recoveryIntent = e.intent
            if (recoveryIntent != null) {
                throw RemoteConsentRequiredException(recoveryIntent, e)
            }
            throw e
        }
    }

    private inline fun <T> executeWithConsentHandling(
        accountEmail: String,
        block: () -> T
    ): Result<T> {
        return try {
            ensureOAuthTokenOrThrowConsent(accountEmail)
            Result.success(block())
        } catch (e: RemoteConsentRequiredException) {
            Result.failure(e)
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                Result.failure(RemoteConsentRequiredException(intent, e))
            } else {
                Result.failure(e)
            }
        } catch (e: UserRecoverableAuthIOException) {
            val intent = e.extractConsentIntentOrNull()
            if (intent != null) {
                Result.failure(RemoteConsentRequiredException(intent, e.cause ?: e))
            } else {
                Result.failure(e)
            }
        } catch (e: Throwable) {
            val intent = e.extractConsentIntentOrNull()
            if (intent != null) {
                Result.failure(RemoteConsentRequiredException(intent, e))
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Verifies both READ and WRITE access on the target Spreadsheet prior to running write jobs.
     */
    suspend fun verifyReadWritePermissions(
        accountEmail: String,
        spreadsheetId: String
    ): Result<SheetPermissionCheckResult> = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Veuillez d'abord vous connecter à un compte Google.")
            )
        }
        if (spreadsheetId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Veuillez renseigner l'identifiant du Google Sheet.")
            )
        }

        executeWithConsentHandling(accountEmail) {
            val service = buildSheetsService(accountEmail)
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val title = spreadsheet.properties?.title ?: "Google Sheet"
            val firstSheet = spreadsheet.sheets?.firstOrNull()?.properties?.title ?: "Feuille 1"

            // Verify read access on A1:E
            val readRange = "$firstSheet!A1:E"
            val valuesResponse = service.spreadsheets().values()
                .get(spreadsheetId, readRange)
                .execute()
            val rows = valuesResponse.getValues()?.size ?: 0

            // Perform a no-op dry write check on header cell A1 if present, or empty ValueRange update check
            val headerValue = valuesResponse.getValues()?.firstOrNull()?.firstOrNull()?.toString() ?: "Date"
            val checkBody = ValueRange().setValues(listOf(listOf(headerValue)))
            service.spreadsheets().values()
                .update(spreadsheetId, "$firstSheet!A1", checkBody)
                .setValueInputOption("USER_ENTERED")
                .execute()

            SheetPermissionCheckResult(
                canRead = true,
                canWrite = true,
                spreadsheetTitle = title,
                firstSheetTitle = firstSheet,
                rowCount = rows,
                statusMessage = "Accès Lecture/Écriture vérifié sur « $title » ($firstSheet, $rows lignes)."
            )
        }
    }

    /**
     * Fetches all rows (columns A to E) from the target Google Sheet.
     */
    suspend fun fetchSheetRows(
        accountEmail: String,
        spreadsheetId: String,
        sheetTabName: String = ""
    ): Result<List<List<Any>>> = withContext(Dispatchers.IO) {
        executeWithConsentHandling(accountEmail) {
            val service = buildSheetsService(accountEmail)
            val effectiveTab = if (sheetTabName.isNotBlank()) {
                sheetTabName
            } else {
                val meta = service.spreadsheets().get(spreadsheetId).execute()
                meta.sheets?.firstOrNull()?.properties?.title ?: "A1:E"
            }
            val range = if (effectiveTab == "A1:E") "A1:E" else "$effectiveTab!A1:E"
            val response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()
            response.getValues().orEmpty()
        }
    }

    /**
     * Appends a single 5-column meter capture row to the Google Sheet:
     * 1. Date (e.g., "03Aug26")
     * 2. Meter Type ("Twingo", "Tiguan", "mx5", "clim (T00)", "edf", "Eau generale", "Eau jardin", "Arrosage")
     * 3. Raw Value
     * 4. Normalized Value (Raw + Offset)
     * 5. Comment
     */
    suspend fun appendMeterReading(
        accountEmail: String,
        spreadsheetId: String,
        sheetTabName: String,
        reading: MeterReading
    ): Result<AppendValuesResponse> = withContext(Dispatchers.IO) {
        executeWithConsentHandling(accountEmail) {
            val service = buildSheetsService(accountEmail)
            val effectiveTab = if (sheetTabName.isNotBlank()) {
                sheetTabName
            } else {
                val meta = service.spreadsheets().get(spreadsheetId).execute()
                meta.sheets?.firstOrNull()?.properties?.title ?: "Feuille 1"
            }
            val range = "$effectiveTab!A:E"
            val body = ValueRange().setValues(listOf(reading.toSheetRow()))

            service.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()
        }
    }
}
