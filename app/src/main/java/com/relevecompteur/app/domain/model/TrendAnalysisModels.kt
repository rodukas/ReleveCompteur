package com.relevecompteur.app.domain.model

import java.time.LocalDate

enum class ChartMetricMode(val frenchLabel: String) {
    NORMALIZED_CUMULATIVE("Valeur normalisée cumulée"),
    YEARLY_DELTA("Progression annuelle (Δ depuis 1er Jan)"),
    DAILY_VELOCITY("Vitesse journalière (Δ / jour)")
}

data class TrendPoint(
    val dayOfYear: Int,
    val date: LocalDate,
    val dateLabel: String,
    val rawValue: Double,
    val normalizedValue: Double,
    val plotValue: Double,
    val dailyVelocity: Double,
    val comment: String
)

data class YearlyTrendSeries(
    val year: Int,
    val isCurrentYear: Boolean,
    val yearOffsetIndex: Int, // 0 = Current Year, 1 = Year - 1, 2 = Year - 2, 3 = Year - 3
    val points: List<TrendPoint>
)

enum class LeakSeverity {
    NONE,
    WATCH,
    CRITICAL
}

data class WaterVelocityInterval(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startDateLabel: String,
    val endDateLabel: String,
    val daysElapsed: Long,
    val deltaM3: Double,
    val velocityM3PerDay: Double,
    val velocityLitresPerDay: Double,
    val comment: String,
    val isAnomaly: Boolean
)

data class LeakAlert(
    val meterType: MeterType,
    val severity: LeakSeverity,
    val title: String,
    val summary: String,
    val latestVelocityM3PerDay: Double,
    val baselineMedianM3PerDay: Double,
    val ratioToBaseline: Double,
    val latestIntervalDays: Long,
    val continuousNonZeroIntervalsCount: Int,
    val flaggedIntervals: List<WaterVelocityInterval>
)

