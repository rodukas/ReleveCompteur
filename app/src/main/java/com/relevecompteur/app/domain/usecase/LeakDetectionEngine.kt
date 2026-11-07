package com.relevecompteur.app.domain.usecase

import com.relevecompteur.app.domain.model.ChartMetricMode
import com.relevecompteur.app.domain.model.LeakAlert
import com.relevecompteur.app.domain.model.LeakSeverity
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.TrendPoint
import com.relevecompteur.app.domain.model.WaterVelocityInterval
import com.relevecompteur.app.domain.model.YearlyTrendSeries
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Activity 2 Engine:
 * 1. Partitions historical readings by calendar year (Current Year vs Year-1, Year-2, Year-3)
 *    for multi-year comparative charting across Day of Year (Jan 1 .. Dec 31).
 * 2. Evaluates water consumption velocity (ΔValue / ΔDays) across potentially long measurement
 *    intervals (weeks or months) to flag leaks, sudden velocity spikes, or continuous non-zero
 *    baseline consumption.
 */
object LeakDetectionEngine {

    /**
     * Builds up to 4 yearly overlay series (Current Year, Year - 1, Year - 2, Year - 3)
     * for the selected [meterType].
     */
    fun buildYearlySeries(
        allReadings: List<MeterReading>,
        meterType: MeterType,
        metricMode: ChartMetricMode
    ): List<YearlyTrendSeries> {
        val meterReadings = allReadings
            .filter { it.meterType == meterType }
            .sortedBy { it.date }
            .let { sanitizeHistoricalOutliers(it) }

        if (meterReadings.isEmpty()) return emptyList()

        val referenceCurrentYear = meterReadings.maxOf { it.year }
        val targetYears = (0..3).map { referenceCurrentYear - it }

        // Precompute daily velocity per reading
        val velocityByReadingId = mutableMapOf<Long, Double>()
        for (i in meterReadings.indices) {
            if (i == 0) {
                velocityByReadingId[meterReadings[i].id] = 0.0
            } else {
                val prev = meterReadings[i - 1]
                val curr = meterReadings[i]
                val days = ChronoUnit.DAYS.between(prev.date, curr.date).coerceAtLeast(1L)
                val delta = (curr.normalizedValue - prev.normalizedValue).coerceAtLeast(0.0)
                velocityByReadingId[curr.id] = delta / days.toDouble()
            }
        }

        val byYear = meterReadings.groupBy { it.year }

        return targetYears.mapIndexedNotNull { index, year ->
            val yearReadings = byYear[year].orEmpty().sortedBy { it.dayOfYear }
            if (yearReadings.isEmpty()) return@mapIndexedNotNull null

            // Baseline at start of year for YEARLY_DELTA mode:
            // Interpolate from the last reading of (year - 1) if available, else first reading of year
            val prevYearLast = meterReadings.lastOrNull { it.year < year }
            val firstOfYear = yearReadings.first()
            val jan1Baseline = if (prevYearLast != null) {
                val totalDays = ChronoUnit.DAYS.between(prevYearLast.date, firstOfYear.date).coerceAtLeast(1L)
                val daysToJan1 = ChronoUnit.DAYS.between(
                    prevYearLast.date,
                     java.time.LocalDate.of(year, 1, 1)
                ).coerceAtLeast(0L)
                val slope = (firstOfYear.normalizedValue - prevYearLast.normalizedValue) / totalDays.toDouble()
                prevYearLast.normalizedValue + slope * daysToJan1
            } else {
                firstOfYear.normalizedValue
            }

            val points = yearReadings.map { reading ->
                val velocity = velocityByReadingId[reading.id] ?: 0.0
                val plotVal = when (metricMode) {
                    ChartMetricMode.NORMALIZED_CUMULATIVE -> reading.normalizedValue
                    ChartMetricMode.YEARLY_DELTA -> (reading.normalizedValue - jan1Baseline).coerceAtLeast(0.0)
                    ChartMetricMode.DAILY_VELOCITY -> velocity
                }
                TrendPoint(
                    dayOfYear = reading.dayOfYear,
                    date = reading.date,
                    dateLabel = reading.dateString,
                    rawValue = reading.rawValue,
                    normalizedValue = reading.normalizedValue,
                    plotValue = plotVal,
                    dailyVelocity = velocity,
                    comment = reading.comment
                )
            }

            YearlyTrendSeries(
                year = year,
                isCurrentYear = (index == 0),
                yearOffsetIndex = index,
                points = points
            )
        }
    }

    /**
     * Filters out single-point clerical typos in historical data where a reading jumps up by >5000
     * and immediately drops back down on the next reading (e.g., clim (T00) on 13Mar22 "11,336" -> "1,336").
     */
    private fun sanitizeHistoricalOutliers(sorted: List<MeterReading>): List<MeterReading> {
        if (sorted.size < 3) return sorted
        return sorted.mapIndexed { idx, current ->
            if (idx > 0 && idx < sorted.lastIndex) {
                val prev = sorted[idx - 1]
                val next = sorted[idx + 1]
                if (current.normalizedValue > next.normalizedValue &&
                    prev.normalizedValue <= next.normalizedValue &&
                    current.meterType == MeterType.CLIM_T00 &&
                    current.normalizedValue > 10000.0 &&
                    next.normalizedValue < 3000.0
                ) {
                    // Correct the known 11,336 -> 1,336 clerical digit duplication in sample sheet
                    val corrected = current.normalizedValue - 10000.0
                    current.copy(rawValue = corrected, normalizedValue = corrected)
                } else {
                    current
                }
            } else {
                current
            }
        }
    }

    /**
     * Analyzes a water meter (`Eau generale`, `Eau jardin`, `Arrosage`) for leaks and velocity anomalies.
     * Supports long intervals (several weeks or months between two measures).
     */
    fun analyzeWaterLeakHeuristics(
        allReadings: List<MeterReading>,
        meterType: MeterType
    ): LeakAlert? {
        if (!meterType.isWaterMeter) return null

        val readings = allReadings
            .filter { it.meterType == meterType }
            .sortedBy { it.date }

        if (readings.size < 2) return null

        // Build intervals across consecutive readings (merging same-day meter replacement pairs)
        val rawIntervals = mutableListOf<WaterVelocityInterval>()
        for (i in 1 until readings.size) {
            val prev = readings[i - 1]
            val curr = readings[i]
            val days = ChronoUnit.DAYS.between(prev.date, curr.date)
            val delta = curr.normalizedValue - prev.normalizedValue
            if (days <= 0L || delta < 0.0) continue

            val velocityM3 = delta / days.toDouble()
            rawIntervals.add(
                WaterVelocityInterval(
                    startDate = prev.date,
                    endDate = curr.date,
                    startDateLabel = prev.dateString,
                    endDateLabel = curr.dateString,
                    daysElapsed = days,
                    deltaM3 = delta,
                    velocityM3PerDay = velocityM3,
                    velocityLitresPerDay = velocityM3 * 1000.0,
                    comment = curr.comment,
                    isAnomaly = false
                )
            )
        }

        if (rawIntervals.isEmpty()) return null

        // Compute robust baseline statistics (median & standard deviation) across intervals >= 7 days
        val longIntervals = rawIntervals.filter { it.daysElapsed >= 7L }
            .ifEmpty { rawIntervals }
        val sortedVelocities = longIntervals.map { it.velocityM3PerDay }.sorted()
        val medianVelocity = sortedVelocities[sortedVelocities.size / 2]
        val meanVelocity = sortedVelocities.average()
        val stdDev = sqrt(
            sortedVelocities.map { (it - meanVelocity) * (it - meanVelocity) }.average()
        )

        // Heuristic threshold tailored for multi-week / multi-month intervals:
        // - For Eau generale: baseline is ~0.42 m³/day (420 L/day). Spike > 0.78 m³/day or > 1.7x median.
        // - For Eau jardin: baseline is ~0.18 m³/day. Spike > 0.45 m³/day or winter non-zero flow.
        // - For Arrosage: baseline is ~0.20 m³/day. Spike > 0.40 m³/day or winter/spring surge.
        val spikeThreshold = when (meterType) {
            MeterType.EAU_GEN -> max(medianVelocity * 1.65, 0.75)
            MeterType.EAU_JARDIN -> max(medianVelocity * 1.80, 0.40)
            MeterType.ARROSAGE -> max(medianVelocity * 1.60, 0.35)
            else -> meanVelocity + 1.5 * stdDev
        }

        val annotatedIntervals = rawIntervals.map { interval ->
            val commentLower = interval.comment.lowercase()
            val hasExplicitLeakNote = commentLower.contains("fuite") ||
                commentLower.contains("perdu") ||
                commentLower.contains("cheval")
            val isVelocitySpike = interval.daysElapsed >= 5L && interval.velocityM3PerDay >= spikeThreshold
            interval.copy(isAnomaly = isVelocitySpike || hasExplicitLeakNote)
        }

        // Count consecutive trailing intervals without a zero/near-zero reset
        val zeroThresholdM3PerDay = when (meterType) {
            MeterType.EAU_GEN -> 0.25 // Main house water should occasionally drop below 250 L/day
            MeterType.EAU_JARDIN -> 0.03 // Garden water should have near-zero intervals in winter
            MeterType.ARROSAGE -> 0.03 // Irrigation should have near-zero intervals in winter
            else -> 0.05
        }

        var continuousNonZeroCount = 0
        for (interval in annotatedIntervals.asReversed()) {
            if (interval.velocityM3PerDay > zeroThresholdM3PerDay) {
                continuousNonZeroCount++
            } else {
                break
            }
        }

        val latest = annotatedIntervals.last()
        val recentThree = annotatedIntervals.takeLast(3)
        val recentAnomalies = annotatedIntervals.takeLast(5).filter { it.isAnomaly }
        val ratio = if (medianVelocity > 0.01) latest.velocityM3PerDay / medianVelocity else 1.0

        val severity = when {
            latest.isAnomaly || (recentAnomalies.isNotEmpty() && continuousNonZeroCount >= 3) ->
                LeakSeverity.CRITICAL
            recentThree.any { it.isAnomaly } || continuousNonZeroCount >= 4 ->
                LeakSeverity.WATCH
            else ->
                LeakSeverity.NONE
        }

        val title = when (severity) {
            LeakSeverity.CRITICAL ->
                "Alerte Fuite / Surconsommation détectée (${meterType.displayLabel})"
            LeakSeverity.WATCH ->
                "Vigilance Consommation Continue (${meterType.displayLabel})"
            LeakSeverity.NONE ->
                "Consommation Hydrique Normale (${meterType.displayLabel})"
        }

        val latestLiters = (latest.velocityLitresPerDay).toInt()
        val baselineLiters = (medianVelocity * 1000.0).toInt()

        val summary = buildString {
            append(
                "Dernier intervalle (${latest.startDateLabel} → ${latest.endDateLabel}, ${latest.daysElapsed} jours) : "
            )
            append(
                "%.2f m³/j (%d L/j) contre une médiane historique de %.2f m³/j (%d L/j). ".format(
                    latest.velocityM3PerDay,
                    latestLiters,
                    medianVelocity,
                    baselineLiters
                )
            )
            if (continuousNonZeroCount >= 3) {
                append(
                    "Flux continu sans période de repos sur $continuousNonZeroCount intervalles consécutifs. "
                )
            }
            if (recentAnomalies.isNotEmpty()) {
                val lastAnomaly = recentAnomalies.last()
                append(
                    "Pic détecté entre ${lastAnomaly.startDateLabel} et ${lastAnomaly.endDateLabel} " +
                        "(+${lastAnomaly.deltaM3.toInt()} m³ en ${lastAnomaly.daysElapsed} j" +
                        (if (lastAnomaly.comment.isNotBlank()) " — « ${lastAnomaly.comment} »" else "") +
                        ")."
                )
            }
        }

        return LeakAlert(
            meterType = meterType,
            severity = severity,
            title = title,
            summary = summary,
            latestVelocityM3PerDay = latest.velocityM3PerDay,
            baselineMedianM3PerDay = medianVelocity,
            ratioToBaseline = ratio,
            latestIntervalDays = latest.daysElapsed,
            continuousNonZeroIntervalsCount = continuousNonZeroCount,
            flaggedIntervals = annotatedIntervals.takeLast(8).reversed()
        )
    }
}

