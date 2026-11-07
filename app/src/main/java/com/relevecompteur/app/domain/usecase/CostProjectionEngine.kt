package com.relevecompteur.app.domain.usecase

import com.relevecompteur.app.domain.model.CategoryCostSummary
import com.relevecompteur.app.domain.model.CostCategoryGroup
import com.relevecompteur.app.domain.model.MeterCostProjection
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.ProjectionTimeframe
import com.relevecompteur.app.domain.model.TariffConfig
import com.relevecompteur.app.domain.model.TotalCostProjectionReport
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Activity 3 Engine: Cost Projection & Tariff Simulator.
 *
 * Calculates:
 *   ΔConsumption = Normalized_end - Normalized_start
 *   Cost = ΔConsumption × Tariff Rate
 *
 * For each meter, the reference period ends on the date of that meter's last recorded reading
 * (Normalized_end = exact last reading value, with no forward extrapolation to today).
 * Normalized_start at (meterEndDate - timeframe.days) is interpolated using the two closest
 * readings immediately before and after that start date.
 */
object CostProjectionEngine {

    fun calculateProjections(
        allReadings: List<MeterReading>,
        tariffConfig: TariffConfig,
        timeframe: ProjectionTimeframe
    ): TotalCostProjectionReport {
        val projectionsByMeter = MeterType.entries.mapNotNull { meterType ->
            computeMeterProjection(
                meterType = meterType,
                readings = allReadings.filter { it.meterType == meterType }.sortedBy { it.date },
                windowDays = timeframe.days,
                tariffConfig = tariffConfig
            )
        }

        val referenceEndDate = projectionsByMeter.maxOfOrNull { it.endDate } ?: LocalDate.now()
        val referenceStartDate = referenceEndDate.minusDays(timeframe.days.toLong())

        // Group into the 4 categories: Electricity, Gas, Water, Vehicles
        val electricityMeters = projectionsByMeter.filter { it.group == CostCategoryGroup.ELECTRICITY }
        val gasMeters = projectionsByMeter.filter { it.group == CostCategoryGroup.GAS }
        val waterMeters = projectionsByMeter.filter { it.group == CostCategoryGroup.WATER }
        val vehicleMeters = projectionsByMeter.filter { it.group == CostCategoryGroup.VEHICLES }

        val edfProj = electricityMeters.firstOrNull { it.meterType == MeterType.EDF }
        val climProj = electricityMeters.firstOrNull { it.meterType == MeterType.CLIM_T00 }
        val elecPeriodTotal = edfProj?.periodCostEur
            ?: electricityMeters.sumOf { it.periodCostEur }
        val elecMonthlyTotal = edfProj?.projectedMonthlyCostEur
            ?: electricityMeters.sumOf { it.projectedMonthlyCostEur }
        val elecAnnualTotal = edfProj?.projectedAnnualCostEur
            ?: electricityMeters.sumOf { it.projectedAnnualCostEur }

        val elecSubNote = if (edfProj != null && climProj != null && edfProj.periodCostEur > 0.0) {
            val pct = ((climProj.periodCostEur / edfProj.periodCostEur) * 100.0).coerceAtMost(100.0)
            "Dont %.2f € (%.0f%%) consommés par le sous-compteur clim (T00)".format(
                climProj.periodCostEur,
                pct
            )
        } else null

        val gasPeriodTotal = gasMeters.sumOf { it.periodCostEur }
        val gasMonthlyTotal = gasMeters.sumOf { it.projectedMonthlyCostEur }
        val gasAnnualTotal = gasMeters.sumOf { it.projectedAnnualCostEur }

        val eauGenProj = waterMeters.firstOrNull { it.meterType == MeterType.EAU_GEN }
        val eauJardinProj = waterMeters.firstOrNull { it.meterType == MeterType.EAU_JARDIN }
        val arrosageProj = waterMeters.firstOrNull { it.meterType == MeterType.ARROSAGE }

        val waterPeriodTotal = eauGenProj?.periodCostEur
            ?: waterMeters.sumOf { it.periodCostEur }
        val waterMonthlyTotal = eauGenProj?.projectedMonthlyCostEur
            ?: waterMeters.sumOf { it.projectedMonthlyCostEur }
        val waterAnnualTotal = eauGenProj?.projectedAnnualCostEur
            ?: waterMeters.sumOf { it.projectedAnnualCostEur }

        val subWaterPeriodCost = (eauJardinProj?.periodCostEur ?: 0.0) +
            (arrosageProj?.periodCostEur ?: 0.0)
        val waterSubNote = if (eauGenProj != null && eauGenProj.periodCostEur > 0.0) {
            val pct = ((subWaterPeriodCost / eauGenProj.periodCostEur) * 100.0).coerceAtMost(100.0)
            "Dont %.2f € (%.0f%%) consommés par les sous-compteurs Eau jardin (%.2f €) et Arrosage (%.2f €)".format(
                subWaterPeriodCost,
                pct,
                eauJardinProj?.periodCostEur ?: 0.0,
                arrosageProj?.periodCostEur ?: 0.0
            )
        } else null

        val vehiclePeriodTotal = vehicleMeters.sumOf { it.periodCostEur }
        val vehicleMonthlyTotal = vehicleMeters.sumOf { it.projectedMonthlyCostEur }
        val vehicleAnnualTotal = vehicleMeters.sumOf { it.projectedAnnualCostEur }

        val categories = listOf(
            CategoryCostSummary(
                group = CostCategoryGroup.ELECTRICITY,
                meterProjections = electricityMeters,
                periodNetCostEur = elecPeriodTotal,
                monthlyNetCostEur = elecMonthlyTotal,
                annualNetCostEur = elecAnnualTotal,
                subMeterShareNote = elecSubNote
            ),
            CategoryCostSummary(
                group = CostCategoryGroup.GAS,
                meterProjections = gasMeters,
                periodNetCostEur = gasPeriodTotal,
                monthlyNetCostEur = gasMonthlyTotal,
                annualNetCostEur = gasAnnualTotal,
                subMeterShareNote = "Compteur gaz GRDF avec offset de remplacement (+33 573 m³)"
            ),
            CategoryCostSummary(
                group = CostCategoryGroup.WATER,
                meterProjections = waterMeters,
                periodNetCostEur = waterPeriodTotal,
                monthlyNetCostEur = waterMonthlyTotal,
                annualNetCostEur = waterAnnualTotal,
                subMeterShareNote = waterSubNote
            ),
            CategoryCostSummary(
                group = CostCategoryGroup.VEHICLES,
                meterProjections = vehicleMeters,
                periodNetCostEur = vehiclePeriodTotal,
                monthlyNetCostEur = vehicleMonthlyTotal,
                annualNetCostEur = vehicleAnnualTotal,
                subMeterShareNote = "Twingo calculée via tarif électrique + Tiguan & mx5 via tarifs carburants dédiés"
            )
        )

        return TotalCostProjectionReport(
            timeframe = timeframe,
            referenceEndDate = referenceEndDate,
            referenceStartDate = referenceStartDate,
            categories = categories,
            totalPeriodCostEur = categories.sumOf { it.periodNetCostEur },
            totalMonthlyCostEur = categories.sumOf { it.monthlyNetCostEur },
            totalAnnualCostEur = categories.sumOf { it.annualNetCostEur }
        )
    }

    private fun computeMeterProjection(
        meterType: MeterType,
        readings: List<MeterReading>,
        windowDays: Int,
        tariffConfig: TariffConfig
    ): MeterCostProjection? {
        if (readings.isEmpty()) return null

        val lastReading = readings.last()
        val endDate = lastReading.date
        val startDate = endDate.minusDays(windowDays.toLong())

        val normEnd = lastReading.normalizedValue
        val normStart = interpolateNormalizedAtDate(readings, startDate)
        val delta = (normEnd - normStart).coerceAtLeast(0.0)
        val dailyAvg = delta / windowDays.toDouble()

        val unitRate = tariffConfig.unitRateForMeter(meterType)
        val periodCost = delta * unitRate
        val monthlyCost = (dailyAvg * 30.4375) * unitRate
        val annualCost = (dailyAvg * 365.0) * unitRate

        val group = when (meterType) {
            MeterType.EDF, MeterType.CLIM_T00 -> CostCategoryGroup.ELECTRICITY
            MeterType.GAZ -> CostCategoryGroup.GAS
            MeterType.EAU_GEN, MeterType.EAU_JARDIN, MeterType.ARROSAGE -> CostCategoryGroup.WATER
            MeterType.TWINGO, MeterType.TIGUAN, MeterType.MX5 -> CostCategoryGroup.VEHICLES
        }

        val formulaDesc = when (meterType) {
            MeterType.EDF, MeterType.CLIM_T00 ->
                "%.4f € / kWh".format(tariffConfig.electricityRatePerKwh)
            MeterType.GAZ ->
                "%.2f € / m³ (≈ 11,2 kWh/m³)".format(tariffConfig.gasRatePerM3)
            MeterType.EAU_GEN, MeterType.EAU_JARDIN, MeterType.ARROSAGE ->
                "%.2f € / m³".format(tariffConfig.waterRatePerM3)
            MeterType.TWINGO ->
                "%.1f kWh/100km × %.4f €/kWh = %.2f € / 100 km".format(
                    tariffConfig.twingoKwhPer100Km,
                    tariffConfig.electricityRatePerKwh,
                    tariffConfig.twingoCostPer100Km
                )
            MeterType.TIGUAN ->
                "%.1f L/100km × %.2f €/L = %.2f € / 100 km".format(
                    tariffConfig.tiguanLitresPer100Km,
                    tariffConfig.tiguanPricePerLitre,
                    tariffConfig.tiguanCostPer100Km
                )
            MeterType.MX5 ->
                "%.1f L/100km × %.2f €/L = %.2f € / 100 km".format(
                    tariffConfig.mx5LitresPer100Km,
                    tariffConfig.mx5PricePerLitre,
                    tariffConfig.mx5CostPer100Km
                )
        }

        return MeterCostProjection(
            meterType = meterType,
            group = group,
            isSubMeterIncludedInMain = (
                meterType == MeterType.CLIM_T00 ||
                    meterType == MeterType.EAU_JARDIN ||
                    meterType == MeterType.ARROSAGE
                ),
            startDate = startDate,
            endDate = endDate,
            normalizedStart = normStart,
            normalizedEnd = normEnd,
            deltaConsumption = delta,
            dailyAverageConsumption = dailyAvg,
            unitRateEur = unitRate,
            rateFormulaDescription = formulaDesc,
            periodCostEur = periodCost,
            projectedMonthlyCostEur = monthlyCost,
            projectedAnnualCostEur = annualCost
        )
    }

    /**
     * Predicts the normalized reading at [targetDate] from sorted historical [readings].
     * If an exact measure exists at [targetDate], returns its normalizedValue.
     * Otherwise, interpolates linearly between the closest measure before [targetDate]
     * and the closest measure after [targetDate].
     */
    private fun interpolateNormalizedAtDate(
        readings: List<MeterReading>,
        targetDate: LocalDate
    ): Double {
        if (readings.size == 1) return readings.first().normalizedValue

        // 1. Exact match on targetDate
        readings.lastOrNull { it.date == targetDate }?.let { exact ->
            return exact.normalizedValue
        }

        val first = readings.first()
        val last = readings.last()

        if (!targetDate.isAfter(first.date)) return first.normalizedValue
        if (!targetDate.isBefore(last.date)) return last.normalizedValue

        // 2. Closest reading strictly before targetDate and closest reading strictly after targetDate
        val before = readings.lastOrNull { it.date.isBefore(targetDate) } ?: first
        val after = readings.firstOrNull { it.date.isAfter(targetDate) } ?: last

        val spanDays = ChronoUnit.DAYS.between(before.date, after.date)
        if (spanDays <= 0L) return after.normalizedValue

        val elapsed = ChronoUnit.DAYS.between(before.date, targetDate)
        val fraction = elapsed.toDouble() / spanDays.toDouble()
        return before.normalizedValue + (after.normalizedValue - before.normalizedValue) * fraction
    }
}
