package com.relevecompteur.app.domain.model

enum class MeterCategory(
    val frenchLabel: String,
    val description: String
) {
    VEHICLE("Odomètre Véhicule", "Kilométrage des véhicules"),
    ELECTRICITY_MAIN("Réseau Électrique Principal", "Électricité générale de la maison (Linky)"),
    ELECTRICITY_SUB("Sous-compteur Électrique", "Sous-comptage climatisation / chauffage"),
    GAS_MAIN("Gaz Naturel", "Consommation de gaz de ville (GRDF)"),
    WATER_MAIN("Eau Générale", "Consommation totale d'eau de la maison"),
    WATER_SUB("Sous-compteur Eau", "Sous-comptage jardin, piscine et arrosage")
}

/**
 * Complete 9-meter taxonomy including Gaz ("gaz").
 */
enum class MeterType(
    val keyId: String,
    val displayLabel: String,
    val frenchName: String,
    val category: MeterCategory,
    val unit: String,
    val description: String,
    val referenceAssetPath: String,
    val defaultOffset: Double,
    val isWaterMeter: Boolean,
    val hasMechanicalRedDecimals: Boolean,
    val ocrKeywords: List<String>,
    val ignoredSerialTokens: List<String>
) {
    TWINGO(
        keyId = "TWINGO",
        displayLabel = "Twingo",
        frenchName = "Renault Twingo E-Tech",
        category = MeterCategory.VEHICLE,
        unit = "km",
        description = "Kilométrage véhicule électrique Renault Twingo",
        referenceAssetPath = "meter_references/twingo_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = false,
        ocrKeywords = listOf("eco", "100%", "twingo", "ready", "cruise"),
        ignoredSerialTokens = listOf("100", "159", "160", "140", "130", "120", "110", "90", "80", "70", "60", "50", "40", "30", "20", "10", "17")
    ),
    TIGUAN(
        keyId = "TIGUAN",
        displayLabel = "Tiguan",
        frenchName = "Volkswagen Tiguan",
        category = MeterCategory.VEHICLE,
        unit = "km",
        description = "Kilométrage véhicule thermique Volkswagen Tiguan",
        referenceAssetPath = "meter_references/tiguan_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = false,
        ocrKeywords = listOf("trip", "km/h", "240", "210", "180", "tiguan"),
        ignoredSerialTokens = listOf("5311", "53113", "943", "240", "210", "180", "160", "140", "120", "100", "130")
    ),
    MX5(
        keyId = "MX5",
        displayLabel = "mx5",
        frenchName = "Mazda MX-5",
        category = MeterCategory.VEHICLE,
        unit = "km",
        description = "Kilométrage véhicule thermique Mazda MX-5",
        referenceAssetPath = "meter_references/mx5_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = false,
        ocrKeywords = listOf("i-stop", "istop", "l/100km", "l/100", "r/min", "x1000", "trip"),
        ignoredSerialTokens = listOf("1000", "100", "110", "120", "17")
    ),
    EDF(
        keyId = "EDF",
        displayLabel = "edf",
        frenchName = "Électricité Générale (Linky)",
        category = MeterCategory.ELECTRICITY_MAIN,
        unit = "kWh",
        description = "Consommation électrique totale (compteur Linky tarif de base)",
        referenceAssetPath = "meter_references/edf_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = false,
        ocrKeywords = listOf("linky", "base", "itron", "kwh", "m18", "0071", "t10797", "ite411"),
        ignoredSerialTokens = listOf("10797", "0071", "027310", "230", "411")
    ),
    CLIM_T00(
        keyId = "CLIM_T00",
        displayLabel = "clim (T00)",
        frenchName = "Climatisation (T00)",
        category = MeterCategory.ELECTRICITY_SUB,
        unit = "kWh",
        description = "Sous-compteur électrique dédié climatisation (HIKING TOMZN DDS238-2 SW)",
        referenceAssetPath = "meter_references/clim_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = false,
        ocrKeywords = listOf(
            "hiking", "tomzn", "dds238", "t00", "t 00", "clim", "1000imp",
            "iec62053", "202111267266", "differentiel", "class1"
        ),
        ignoredSerialTokens = listOf(
            "202111267266", "11267266", "67266", "62053", "238", "220", "230",
            "1000", "100", "50", "60", "63", "21", "00"
        )
    ),
    GAZ(
        keyId = "GAZ",
        displayLabel = "gaz",
        frenchName = "Gaz Naturel (GRDF)",
        category = MeterCategory.GAS_MAIN,
        unit = "m³",
        description = "Compteur gaz naturel GRDF (Itron Gallus G4)",
        referenceAssetPath = "meter_references/gaz_ref.jpg",
        defaultOffset = 33573.0,
        isWaterMeter = false,
        hasMechanicalRedDecimals = true,
        ocrKeywords = listOf("grdf", "gallus", "g4", "523038", "37075", "gaz", "meudon", "insaisissable", "qmax", "pmax"),
        ignoredSerialTokens = listOf("523038", "37075", "2021", "1359", "2006", "0071", "4421")
    ),
    EAU_GEN(
        keyId = "EAU_GEN",
        displayLabel = "Eau generale",
        frenchName = "Eau Générale",
        category = MeterCategory.WATER_MAIN,
        unit = "m³",
        description = "Compteur général d'eau de la maison (Diehl Metering)",
        referenceAssetPath = "meter_references/eau_generale_ref.jpg",
        defaultOffset = 4377.0,
        isWaterMeter = true,
        hasMechanicalRedDecimals = true,
        ocrKeywords = listOf("diehl", "metering", "m24", "24fb", "067058", "saint-louis", "lne", "6250", "pma16"),
        ignoredSerialTokens = listOf("067058", "67058", "68304", "6250", "0071", "0001", "160", "24")
    ),
    EAU_JARDIN(
        keyId = "EAU_JARDIN",
        displayLabel = "Eau jardin",
        frenchName = "Eau Jardin & Piscine",
        category = MeterCategory.WATER_SUB,
        unit = "m³",
        description = "Sous-compteur eau jardin et remplissage piscine",
        referenceAssetPath = "meter_references/eau_jardin_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = true,
        hasMechanicalRedDecimals = true,
        ocrKeywords = listOf("02ca001132", "02ca", "001132", "1132", "ca00"),
        ignoredSerialTokens = listOf("001132", "1132", "02001132")
    ),
    ARROSAGE(
        keyId = "ARROSAGE",
        displayLabel = "Arrosage",
        frenchName = "Arrosage Automatique",
        category = MeterCategory.WATER_SUB,
        unit = "m³",
        description = "Sous-compteur dédié arrosage plantes & goutte-à-goutte",
        referenceAssetPath = "meter_references/arrosage_ref.jpg",
        defaultOffset = 0.0,
        isWaterMeter = true,
        hasMechanicalRedDecimals = true,
        ocrKeywords = listOf("hidroconta", "m23", "0300", "033624", "r160h", "142303001", "16 bar", "u0 d0"),
        ignoredSerialTokens = listOf("0300", "300", "033624", "33624", "23033624", "142303001", "160")
    );

    companion object {
        /**
         * Matches a label from Google Sheets or CSV (case-insensitive, handling historical variants
         * like "MX5" vs "mx5", "EDF" vs "edf", "Gaz" vs "gaz").
         */
        fun fromSheetLabel(rawLabel: String?): MeterType? {
            if (rawLabel.isNullOrBlank()) return null
            val cleaned = rawLabel.trim()
            entries.firstOrNull {
                it.displayLabel.equals(cleaned, ignoreCase = true) ||
                    it.keyId.equals(cleaned, ignoreCase = true)
            }?.let { return it }

            return when (cleaned.lowercase()) {
                "twingo" -> TWINGO
                "tiguan" -> TIGUAN
                "mx5", "mazda mx5", "mx-5" -> MX5
                "edf", "linky" -> EDF
                "clim (t00)", "clim", "t00" -> CLIM_T00
                "gaz", "grdf" -> GAZ
                "eau generale", "eau générale", "eau_gen" -> EAU_GEN
                "eau jardin", "eau_jardin" -> EAU_JARDIN
                "arrosage" -> ARROSAGE
                else -> null
            }
        }
    }
}
