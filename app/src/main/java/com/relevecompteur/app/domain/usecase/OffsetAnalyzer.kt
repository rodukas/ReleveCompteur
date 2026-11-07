package com.relevecompteur.app.domain.usecase

import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Analyzes historical Google Sheet / CSV rows to determine each meter's cumulative replacement offset:
 *   Normalized = Raw + Meter_Cumulative_Offset
 *
 * In GoogleSheetSample.csv:
 * - Eau generale was replaced on 24Dec24 at 4,377 m³ (Raw = 0, Normalized = 4,377 -> Offset = 4377.0)
 * - From 05Apr25 onwards, the "Valeur normalisee" column in the CSV is blank, so this analyzer
 *   automatically applies the discovered offset (4377 for Eau generale, 0 for others).
 */
object OffsetAnalyzer {

    data class RawSheetRow(
        val dateString: String,
        val meterLabel: String,
        val rawValue: Double,
        val normalizedValueOrNull: Double?,
        val comment: String
    )

    /**
     * Infers the cumulative offset for each of the 8 meters from historical rows.
     */
    fun extractInitialOffsets(rows: List<RawSheetRow>): Map<MeterType, Double> {
        val detectedOffsets = mutableMapOf<MeterType, Double>()
        MeterType.entries.forEach { meter ->
            detectedOffsets[meter] = meter.defaultOffset
        }

        val grouped = rows.mapNotNull { row ->
            val type = MeterType.fromSheetLabel(row.meterLabel) ?: return@mapNotNull null
            type to row
        }.groupBy({ it.first }, { it.second })

        grouped.forEach { (meterType, meterRows) ->
            // Find the latest row where both rawValue and normalizedValueOrNull are present
            val lastRowWithNormalized = meterRows.lastOrNull { it.normalizedValueOrNull != null }
            if (lastRowWithNormalized != null && lastRowWithNormalized.normalizedValueOrNull != null) {
                val diff = lastRowWithNormalized.normalizedValueOrNull - lastRowWithNormalized.rawValue
                detectedOffsets[meterType] = if (abs(diff) < 0.01) 0.0 else diff.roundToLong().toDouble()
            }
        }

        return detectedOffsets
    }

    /**
     * Converts raw parsed rows into domain [MeterReading] objects, applying the cumulative offset
     * whenever the sheet's Normalized column is blank or when a meter has an active offset.
     */
    fun buildNormalizedReadings(
        rows: List<RawSheetRow>,
        activeOffsets: Map<MeterType, Double>
    ): List<MeterReading> {
        // Track running offset per meter as we walk chronologically through the sheet
        val runningOffset = mutableMapOf<MeterType, Double>()
        MeterType.entries.forEach { runningOffset[it] = 0.0 }

        val readings = mutableListOf<MeterReading>()
        var nextId = 1L

        for (row in rows) {
            val meterType = MeterType.fromSheetLabel(row.meterLabel) ?: continue
            val parsedDate = MeterReading.parseSheetDate(row.dateString) ?: continue

            val explicitNorm = row.normalizedValueOrNull
            val effectiveNormalized = if (explicitNorm != null) {
                val rowOffset = explicitNorm - row.rawValue
                if (abs(rowOffset) >= 1.0) {
                    runningOffset[meterType] = rowOffset
                }
                explicitNorm
            } else {
                val configuredOffset = activeOffsets[meterType]
                    ?: runningOffset[meterType]
                    ?: meterType.defaultOffset
                // Use runningOffset once a reset occurred, or configuredOffset for recent rows
                val offsetToApply = if ((runningOffset[meterType] ?: 0.0) > 0.0) {
                    runningOffset[meterType]!!
                } else {
                    configuredOffset
                }
                row.rawValue + offsetToApply
            }

            readings.add(
                MeterReading(
                    id = nextId++,
                    dateString = row.dateString.trim(),
                    date = parsedDate,
                    meterType = meterType,
                    rawValue = row.rawValue,
                    normalizedValue = effectiveNormalized,
                    comment = row.comment.trim(),
                    isSyncedToSheet = true
                )
            )
        }

        return readings.sortedWith(compareBy<MeterReading> { it.date }.thenBy { it.id })
    }
}

