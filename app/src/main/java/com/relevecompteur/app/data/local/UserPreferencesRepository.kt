package com.relevecompteur.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.TariffConfig
import com.relevecompteur.app.domain.vision.ClassifierEngineMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "releve_compteur_prefs"
)

data class AppSettingsState(
    val spreadsheetId: String = "",
    val sheetTabName: String = "Feuille 1",
    val googleAccountEmail: String = "",
    val classifierMode: ClassifierEngineMode = ClassifierEngineMode.MLKIT_VISUAL_TEMPLATE,
    val meterOffsets: Map<MeterType, Double> = MeterType.entries.associateWith { it.defaultOffset },
    val tariffConfig: TariffConfig = TariffConfig()
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val SHEET_TAB_NAME = stringPreferencesKey("sheet_tab_name")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val CLASSIFIER_MODE = stringPreferencesKey("classifier_mode")

        // Tariff rates
        val WATER_RATE_M3 = doublePreferencesKey("tariff_water_m3")
        val ELEC_RATE_KWH = doublePreferencesKey("tariff_elec_kwh")
        val GAS_RATE_M3 = doublePreferencesKey("tariff_gas_m3")
        val TWINGO_KWH_100KM = doublePreferencesKey("tariff_twingo_kwh_100km")
        val TIGUAN_PRICE_L = doublePreferencesKey("tariff_tiguan_price_l")
        val TIGUAN_L_100KM = doublePreferencesKey("tariff_tiguan_l_100km")
        val MX5_PRICE_L = doublePreferencesKey("tariff_mx5_price_l")
        val MX5_L_100KM = doublePreferencesKey("tariff_mx5_l_100km")

        fun offsetKeyFor(meter: MeterType) = doublePreferencesKey("offset_${meter.keyId}")
    }

    val settingsFlow: Flow<AppSettingsState> = context.dataStore.data.map { prefs ->
        val offsets = MeterType.entries.associateWith { meter ->
            prefs[Keys.offsetKeyFor(meter)] ?: meter.defaultOffset
        }

        val modeStr = prefs[Keys.CLASSIFIER_MODE]
        val mode = ClassifierEngineMode.entries.firstOrNull { it.name == modeStr }
            ?: ClassifierEngineMode.MLKIT_VISUAL_TEMPLATE

        val defaults = TariffConfig()
        val tariffs = TariffConfig(
            waterRatePerM3 = prefs[Keys.WATER_RATE_M3] ?: defaults.waterRatePerM3,
            electricityRatePerKwh = prefs[Keys.ELEC_RATE_KWH] ?: defaults.electricityRatePerKwh,
            gasRatePerM3 = prefs[Keys.GAS_RATE_M3] ?: defaults.gasRatePerM3,
            twingoKwhPer100Km = prefs[Keys.TWINGO_KWH_100KM] ?: defaults.twingoKwhPer100Km,
            tiguanPricePerLitre = prefs[Keys.TIGUAN_PRICE_L] ?: defaults.tiguanPricePerLitre,
            tiguanLitresPer100Km = prefs[Keys.TIGUAN_L_100KM] ?: defaults.tiguanLitresPer100Km,
            mx5PricePerLitre = prefs[Keys.MX5_PRICE_L] ?: defaults.mx5PricePerLitre,
            mx5LitresPer100Km = prefs[Keys.MX5_L_100KM] ?: defaults.mx5LitresPer100Km
        )

        AppSettingsState(
            spreadsheetId = prefs[Keys.SPREADSHEET_ID] ?: "",
            sheetTabName = prefs[Keys.SHEET_TAB_NAME] ?: "Feuille 1",
            googleAccountEmail = prefs[Keys.GOOGLE_ACCOUNT_EMAIL] ?: "",
            classifierMode = mode,
            meterOffsets = offsets,
            tariffConfig = tariffs
        )
    }

    suspend fun updateGoogleAccountEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = email.trim()
        }
    }

    /**
     * Accepts either a raw Spreadsheet ID or a full Google Sheets URL and extracts the ID.
     */
    suspend fun updateSpreadsheetId(rawInput: String, tabName: String = "Feuille 1") {
        val extractedId = extractSpreadsheetId(rawInput)
        context.dataStore.edit { prefs ->
            prefs[Keys.SPREADSHEET_ID] = extractedId
            prefs[Keys.SHEET_TAB_NAME] = tabName.trim().ifEmpty { "Feuille 1" }
        }
    }

    suspend fun updateClassifierMode(mode: ClassifierEngineMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CLASSIFIER_MODE] = mode.name
        }
    }

    suspend fun updateMeterOffset(meterType: MeterType, offsetValue: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.offsetKeyFor(meterType)] = offsetValue
        }
    }

    suspend fun updateAllMeterOffsets(offsets: Map<MeterType, Double>) {
        context.dataStore.edit { prefs ->
            offsets.forEach { (meter, value) ->
                prefs[Keys.offsetKeyFor(meter)] = value
            }
        }
    }

    suspend fun updateTariffConfig(config: TariffConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WATER_RATE_M3] = config.waterRatePerM3
            prefs[Keys.ELEC_RATE_KWH] = config.electricityRatePerKwh
            prefs[Keys.GAS_RATE_M3] = config.gasRatePerM3
            prefs[Keys.TWINGO_KWH_100KM] = config.twingoKwhPer100Km
            prefs[Keys.TIGUAN_PRICE_L] = config.tiguanPricePerLitre
            prefs[Keys.TIGUAN_L_100KM] = config.tiguanLitresPer100Km
            prefs[Keys.MX5_PRICE_L] = config.mx5PricePerLitre
            prefs[Keys.MX5_L_100KM] = config.mx5LitresPer100Km
        }
    }

    companion object {
        private val SHEET_URL_REGEX = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")

        fun extractSpreadsheetId(input: String): String {
            val trimmed = input.trim()
            val match = SHEET_URL_REGEX.find(trimmed)
            return match?.groupValues?.getOrNull(1) ?: trimmed
        }
    }
}
