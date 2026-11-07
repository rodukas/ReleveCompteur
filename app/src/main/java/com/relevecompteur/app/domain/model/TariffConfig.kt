package com.relevecompteur.app.domain.model

/**
 * Configurable unit tariff rates for Activity 3: Cost Projection & Tariff Simulator.
 *
 * - Water rate: € / m³
 * - Electricity rate (Base or Peak/Off-peak average): € / kWh
 * - Gas rate: € / m³ (approx. 11.2 kWh/m³ × €/kWh)
 * - Electric car (Twingo): kWh / 100 km combined with electricityRatePerKwh (€ / kWh) -> € / 100 km
 * - Thermal cars (Tiguan, mx5): individual price per litre (€ / L) and litres per 100 km (L / 100 km) -> € / 100 km
 */
data class TariffConfig(
    val waterRatePerM3: Double = 4.35,
    val electricityRatePerKwh: Double = 0.2516,
    val gasRatePerM3: Double = 1.18,
    val twingoKwhPer100Km: Double = 13.5,
    val tiguanPricePerLitre: Double = 1.78,
    val tiguanLitresPer100Km: Double = 7.2,
    val mx5PricePerLitre: Double = 1.86,
    val mx5LitresPer100Km: Double = 6.8
) {
    /**
     * Computed € / 100 km for Twingo (Electric): kWh/100km * €/kWh
     */
    val twingoCostPer100Km: Double
        get() = twingoKwhPer100Km * electricityRatePerKwh

    /**
     * Computed € / 100 km for Tiguan (Thermal): L/100km * €/L
     */
    val tiguanCostPer100Km: Double
        get() = tiguanLitresPer100Km * tiguanPricePerLitre

    /**
     * Computed € / 100 km for MX5 (Thermal): L/100km * €/L
     */
    val mx5CostPer100Km: Double
        get() = mx5LitresPer100Km * mx5PricePerLitre

    /**
     * Returns the effective cost per 1 unit of the given meter:
     * - Water meters (m³): waterRatePerM3
     * - Gas meter (m³): gasRatePerM3
     * - Electricity meters (kWh): electricityRatePerKwh
     * - Vehicle odometers (km): costPer100Km / 100.0
     */
    fun unitRateForMeter(meterType: MeterType): Double = when (meterType) {
        MeterType.EAU_GEN, MeterType.EAU_JARDIN, MeterType.ARROSAGE -> waterRatePerM3
        MeterType.GAZ -> gasRatePerM3
        MeterType.EDF, MeterType.CLIM_T00 -> electricityRatePerKwh
        MeterType.TWINGO -> twingoCostPer100Km / 100.0
        MeterType.TIGUAN -> tiguanCostPer100Km / 100.0
        MeterType.MX5 -> mx5CostPer100Km / 100.0
    }
}
