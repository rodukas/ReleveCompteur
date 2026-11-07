package com.relevecompteur.app.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Domain representation of a single meter reading row.
 * Corresponds to the 5-column Google Sheet schema:
 * 1. Date (e.g., "03Aug26" or "03/08/26")
 * 2. Meter Type ("Twingo", "Tiguan", "mx5", "clim (T00)", "edf", "Eau generale", "Eau jardin", "Arrosage")
 * 3. Raw Value (numeric read directly from meter dial/LCD)
 * 4. Normalized Value (Raw Value + Meter_Cumulative_Offset)
 * 5. Comment (optional free-form text)
 */
data class MeterReading(
    val id: Long = 0L,
    val dateString: String,
    val date: LocalDate,
    val meterType: MeterType,
    val rawValue: Double,
    val normalizedValue: Double,
    val comment: String = "",
    val isSyncedToSheet: Boolean = true
) {
    val year: Int get() = date.year
    val dayOfYear: Int get() = date.dayOfYear
    val epochDay: Long get() = date.toEpochDay()

    /**
     * Converts this reading into the exact 5-column Google Sheet row format.
     */
    fun toSheetRow(): List<Any> {
        val rawFormatted = if (rawValue % 1.0 == 0.0) rawValue.toLong() else rawValue
        val normFormatted = if (normalizedValue % 1.0 == 0.0) normalizedValue.toLong() else normalizedValue
        return listOf(
            dateString,
            meterType.displayLabel,
            rawFormatted,
            normFormatted,
            comment
        )
    }

    companion object {
        // Format used in GoogleSheetSample.csv and Prompt.md example: "03Aug26"
        private val SHEET_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyy")
            .toFormatter(Locale.ENGLISH)

        private val FLEXIBLE_CSV_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dMMMyy")
            .toFormatter(Locale.ENGLISH)

        private val SLASH_DATE_FORMATTER_SHORT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.FRANCE)

        private val SLASH_DATE_FORMATTER_LONG: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

        fun formatForSheet(date: LocalDate): String = date.format(SHEET_DATE_FORMATTER)

        fun formatSlashDate(date: LocalDate): String = date.format(SLASH_DATE_FORMATTER_SHORT)

        fun parseSheetDate(raw: String): LocalDate? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return runCatching { LocalDate.parse(trimmed, FLEXIBLE_CSV_DATE_FORMATTER) }
                .recoverCatching { LocalDate.parse(trimmed, SHEET_DATE_FORMATTER) }
                .recoverCatching { LocalDate.parse(trimmed, SLASH_DATE_FORMATTER_SHORT) }
                .recoverCatching { LocalDate.parse(trimmed, SLASH_DATE_FORMATTER_LONG) }
                .recoverCatching { LocalDate.parse(trimmed) }
                .getOrNull()
        }
    }
}

