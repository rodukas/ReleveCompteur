package com.relevecompteur.app

import com.relevecompteur.app.data.local.SampleCsvParser
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.ProjectionTimeframe
import com.relevecompteur.app.domain.model.TariffConfig
import com.relevecompteur.app.domain.usecase.CostProjectionEngine
import com.relevecompteur.app.domain.usecase.LeakDetectionEngine
import com.relevecompteur.app.domain.usecase.OffsetAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DomainEnginesTest {

    @Test
    fun `extractInitialOffsets discovers 4377 offset for Eau generale from GoogleSheetSample csv`() {
        val csvFile = File("../GoogleSheetSample.csv").takeIf { it.exists() }
            ?: File("GoogleSheetSample.csv")
        if (!csvFile.exists()) return

        val rows = csvFile.readLines(Charsets.UTF_8)
            .drop(1)
            .mapNotNull { SampleCsvParser.parseCsvLineToRow(it) }

        val offsets = OffsetAnalyzer.extractInitialOffsets(rows)
        assertEquals(4377.0, offsets[MeterType.EAU_GEN] ?: 0.0, 0.01)
        assertEquals(33573.0, offsets[MeterType.GAZ] ?: 0.0, 0.01)
        assertEquals(0.0, offsets[MeterType.TWINGO] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.TIGUAN] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.MX5] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.EDF] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.CLIM_T00] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.EAU_JARDIN] ?: -1.0, 0.01)
        assertEquals(0.0, offsets[MeterType.ARROSAGE] ?: -1.0, 0.01)
    }

    @Test
    fun `toSheetRow produces exact 5-column Google Sheet schema`() {
        val reading = MeterReading(
            id = 1L,
            dateString = "03Aug26",
            date = LocalDate.of(2026, 8, 3),
            meterType = MeterType.EAU_GEN,
            rawValue = 412.0,
            normalizedValue = 4789.0,
            comment = "Remplissage piscine"
        )
        val row = reading.toSheetRow()
        assertEquals(5, row.size)
        assertEquals("03Aug26", row[0])
        assertEquals("Eau generale", row[1])
        assertEquals(412L, row[2])
        assertEquals(4789L, row[3])
        assertEquals("Remplissage piscine", row[4])
    }

    @Test
    fun `CostProjectionEngine combines Twingo kWh per 100km with electricity rate and separate thermal car rates`() {
        val tariff = TariffConfig(
            waterRatePerM3 = 4.0,
            electricityRatePerKwh = 0.25,
            twingoKwhPer100Km = 14.0, // 14 * 0.25 = 3.50 € / 100 km
            tiguanPricePerLitre = 1.80,
            tiguanLitresPer100Km = 7.0, // 12.60 € / 100 km
            mx5PricePerLitre = 1.90,
            mx5LitresPer100Km = 6.5 // 12.35 € / 100 km
        )
        assertEquals(3.50, tariff.twingoCostPer100Km, 1e-4)
        assertEquals(12.60, tariff.tiguanCostPer100Km, 1e-4)
        assertEquals(12.35, tariff.mx5CostPer100Km, 1e-4)

        val readings = listOf(
            MeterReading(1, "08Apr26", LocalDate.of(2026, 4, 8), MeterType.EDF, 56424.0, 56424.0),
            MeterReading(2, "01Aug26", LocalDate.of(2026, 8, 1), MeterType.EDF, 59953.0, 59953.0),
            MeterReading(3, "08Apr26", LocalDate.of(2026, 4, 8), MeterType.CLIM_T00, 3912.0, 3912.0),
            MeterReading(4, "17Jul26", LocalDate.of(2026, 7, 17), MeterType.CLIM_T00, 4251.0, 4251.0),
            MeterReading(5, "25Jul26", LocalDate.of(2026, 7, 25), MeterType.CLIM_T00, 4373.0, 4373.0),
            MeterReading(6, "01Aug26", LocalDate.of(2026, 8, 1), MeterType.CLIM_T00, 4431.0, 4431.0),
            // Even with a newer reading on another meter (e.g. Nov 7), EDF & CLIM end on 01Aug26:
            MeterReading(7, "01Jul26", LocalDate.of(2026, 7, 1), MeterType.TWINGO, 20000.0, 20000.0),
            MeterReading(8, "07Nov26", LocalDate.of(2026, 11, 7), MeterType.TWINGO, 21000.0, 21000.0)
        )
        val report = CostProjectionEngine.calculateProjections(
            allReadings = readings,
            tariffConfig = tariff,
            timeframe = ProjectionTimeframe.LAST_MONTH
        )
        val elecCat = report.categories.first { it.group.name == "ELECTRICITY" }
        val edfProj = elecCat.meterProjections.first { it.meterType == MeterType.EDF }
        val climProj = elecCat.meterProjections.first { it.meterType == MeterType.CLIM_T00 }

        assertEquals(LocalDate.of(2026, 8, 1), edfProj.endDate)
        assertEquals(LocalDate.of(2026, 7, 2), edfProj.startDate)
        assertEquals(59953.0, edfProj.normalizedEnd, 0.01)
        // 08Apr26 -> 02Jul26 is 85 days out of 115 days: 56424 + (59953 - 56424) * (85 / 115) = 59032.39
        assertEquals(59032.39, edfProj.normalizedStart, 0.05)

        assertEquals(LocalDate.of(2026, 8, 1), climProj.endDate)
        assertEquals(LocalDate.of(2026, 7, 2), climProj.startDate)
        assertEquals(4431.0, climProj.normalizedEnd, 0.01)
        // 02Jul26 is between 08Apr26 (3912) and 17Jul26 (4251): 85 / 100 days -> 3912 + 339 * 0.85 = 4200.15
        assertEquals(4200.15, climProj.normalizedStart, 0.05)
    }

    @Test
    fun `LeakDetectionEngine flags multi-month velocity spike on Arrosage`() {
        val readings = listOf(
            MeterReading(1, "01Jul24", LocalDate.of(2024, 7, 1), MeterType.ARROSAGE, 0.0, 0.0),
            MeterReading(2, "30Aug24", LocalDate.of(2024, 8, 30), MeterType.ARROSAGE, 13.0, 13.0),
            MeterReading(3, "09Nov24", LocalDate.of(2024, 11, 9), MeterType.ARROSAGE, 14.0, 14.0),
            MeterReading(4, "05Apr25", LocalDate.of(2025, 4, 5), MeterType.ARROSAGE, 15.0, 15.0),
            MeterReading(5, "03Feb26", LocalDate.of(2026, 2, 3), MeterType.ARROSAGE, 31.0, 31.0),
            MeterReading(6, "08Apr26", LocalDate.of(2026, 4, 8), MeterType.ARROSAGE, 75.0, 75.0, "40m3 de perdu")
        )
        val alert = LeakDetectionEngine.analyzeWaterLeakHeuristics(readings, MeterType.ARROSAGE)
        assertNotNull(alert)
        assertTrue(alert!!.flaggedIntervals.any { it.isAnomaly })
    }
}

