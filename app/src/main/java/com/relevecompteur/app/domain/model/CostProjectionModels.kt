package com.relevecompteur.app.domain.model

import java.time.LocalDate

/**
 * Timeframe selector chips for Activity 3: Cost Projection & Tariff Simulator.
 */
enum class ProjectionTimeframe(
    val frenchLabel: String,
    val days: Int
) {
    LAST_MONTH("Dernier mois", 30),
    LAST_3_MONTHS("Derniers 3 mois", 90),
    LAST_6_MONTHS("Derniers 6 mois", 180),
    LAST_YEAR("Dernière année", 365)
}

enum class CostCategoryGroup(
    val frenchTitle: String,
    val subtitle: String
) {
    ELECTRICITY("Électricité", "Réseau principal Linky (edf) & Sous-compteur Climatisation (T00)"),
    GAS("Gaz Naturel (GRDF)", "Chauffage et eau chaude sanitaire (compteur gaz)"),
    WATER("Eau & Jardin", "Eau générale maison, Eau jardin/piscine & Arrosage"),
    VEHICLES("Véhicules", "Twingo (Électrique), Tiguan (Thermique) & Mazda mx5 (Thermique)")
}

data class MeterCostProjection(
    val meterType: MeterType,
    val group: CostCategoryGroup,
    val isSubMeterIncludedInMain: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val normalizedStart: Double,
    val normalizedEnd: Double,
    val deltaConsumption: Double,
    val dailyAverageConsumption: Double,
    val unitRateEur: Double,
    val rateFormulaDescription: String,
    val periodCostEur: Double,
    val projectedMonthlyCostEur: Double,
    val projectedAnnualCostEur: Double
)

data class CategoryCostSummary(
    val group: CostCategoryGroup,
    val meterProjections: List<MeterCostProjection>,
    val periodNetCostEur: Double,
    val monthlyNetCostEur: Double,
    val annualNetCostEur: Double,
    val subMeterShareNote: String? = null
)

data class TotalCostProjectionReport(
    val timeframe: ProjectionTimeframe,
    val referenceEndDate: LocalDate,
    val referenceStartDate: LocalDate,
    val categories: List<CategoryCostSummary>,
    val totalPeriodCostEur: Double,
    val totalMonthlyCostEur: Double,
    val totalAnnualCostEur: Double
)
