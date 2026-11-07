package com.relevecompteur.app.ui.costs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relevecompteur.app.domain.model.CategoryCostSummary
import com.relevecompteur.app.domain.model.CostCategoryGroup
import com.relevecompteur.app.domain.model.MeterCostProjection
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.ProjectionTimeframe
import com.relevecompteur.app.domain.model.TariffConfig
import com.relevecompteur.app.domain.model.TotalCostProjectionReport
import kotlin.math.roundToLong

@Composable
fun CostSimulatorScreen(
    selectedTimeframe: ProjectionTimeframe,
    tariffConfig: TariffConfig,
    projectionReport: TotalCostProjectionReport,
    onSelectTimeframe: (ProjectionTimeframe) -> Unit,
    onUpdateTariffConfig: (TariffConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Timeframe Selector Chips: Dernier mois, Derniers 3 mois, Derniers 6 mois, Dernière année
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Période d'analyse et de projection",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProjectionTimeframe.entries.forEach { tf ->
                    val selected = (tf == selectedTimeframe)
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTimeframe(tf) },
                        label = {
                            Text(
                                text = "${tf.frenchLabel} (${tf.days}j)",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // 2. Aggregate Estimated Total Cost Banner
        AggregateCostHeaderCard(report = projectionReport)

        // 3. Category Breakdown Cards (Électricité, Gaz, Eau, Véhicules)
        projectionReport.categories.forEach { categorySummary ->
            CategoryBreakdownCard(summary = categorySummary)
        }

        // 4. Configurable Unit Costs Simulator Card
        ConfigurableTariffInputsCard(
            tariffConfig = tariffConfig,
            onUpdateTariffConfig = onUpdateTariffConfig
        )
    }
}

@Composable
private fun AggregateCostHeaderCard(report: TotalCostProjectionReport) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Coût Total Estimé (${report.timeframe.frenchLabel})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Réf. : ${report.timeframe.days} derniers jours avant le dernier relevé de chaque compteur",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Euro,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "%.2f €".format(report.totalPeriodCostEur),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "sur la période de ${report.timeframe.days} jours",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Projection mensuelle : %.2f € / mois".format(report.totalMonthlyCostEur),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Projection annuelle : %.0f € / an".format(report.totalAnnualCostEur),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(summary: CategoryCostSummary) {
    val icon = when (summary.group) {
        CostCategoryGroup.ELECTRICITY -> Icons.Default.Bolt
        CostCategoryGroup.GAS -> Icons.Default.LocalFireDepartment
        CostCategoryGroup.WATER -> Icons.Default.WaterDrop
        CostCategoryGroup.VEHICLES -> Icons.Default.DirectionsCar
    }
    val accentColor = when (summary.group) {
        CostCategoryGroup.ELECTRICITY -> Color(0xFF558B2F)
        CostCategoryGroup.GAS -> Color(0xFFD84315)
        CostCategoryGroup.WATER -> Color(0xFF0277BD)
        CostCategoryGroup.VEHICLES -> Color(0xFF3949AB)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = summary.group.frenchTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = summary.group.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.2f €".format(summary.periodNetCostEur),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                    Text(
                        text = "%.0f €/mois • %.0f €/an".format(
                            summary.monthlyNetCostEur,
                            summary.annualNetCostEur
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            summary.subMeterShareNote?.let { note ->
                Surface(
                    color = accentColor.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider()

            summary.meterProjections.forEach { meterProj ->
                MeterProjectionItemRow(proj = meterProj)
            }
        }
    }
}

@Composable
private fun MeterProjectionItemRow(proj: MeterCostProjection) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "${proj.meterType.displayLabel} (${proj.meterType.frenchName})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (proj.isSubMeterIncludedInMain) {
                        val badgeLabel = if (proj.group == CostCategoryGroup.ELECTRICITY) {
                            "Sous-compteur edf"
                        } else {
                            "Sous-compteur Eau gen."
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "%.2f €".format(proj.periodCostEur),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Période : ${MeterReading.formatSlashDate(proj.startDate)} → ${MeterReading.formatSlashDate(proj.endDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ΔConso : ${proj.normalizedStart.roundToLong()} → ${proj.normalizedEnd.roundToLong()} " +
                        "(+${proj.deltaConsumption.roundToLong()} ${proj.meterType.unit})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "%.1f €/mois | %.0f €/an".format(
                        proj.projectedMonthlyCostEur,
                        proj.projectedAnnualCostEur
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Tarif appliqué : ${proj.rateFormulaDescription}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ConfigurableTariffInputsCard(
    tariffConfig: TariffConfig,
    onUpdateTariffConfig: (TariffConfig) -> Unit
) {
    var waterText by remember(tariffConfig.waterRatePerM3) {
        mutableStateOf(tariffConfig.waterRatePerM3.toString())
    }
    var elecText by remember(tariffConfig.electricityRatePerKwh) {
        mutableStateOf(tariffConfig.electricityRatePerKwh.toString())
    }
    var gasText by remember(tariffConfig.gasRatePerM3) {
        mutableStateOf(tariffConfig.gasRatePerM3.toString())
    }
    var twingoKwhText by remember(tariffConfig.twingoKwhPer100Km) {
        mutableStateOf(tariffConfig.twingoKwhPer100Km.toString())
    }
    var tiguanPriceLText by remember(tariffConfig.tiguanPricePerLitre) {
        mutableStateOf(tariffConfig.tiguanPricePerLitre.toString())
    }
    var tiguanL100Text by remember(tariffConfig.tiguanLitresPer100Km) {
        mutableStateOf(tariffConfig.tiguanLitresPer100Km.toString())
    }
    var mx5PriceLText by remember(tariffConfig.mx5PricePerLitre) {
        mutableStateOf(tariffConfig.mx5PricePerLitre.toString())
    }
    var mx5L100Text by remember(tariffConfig.mx5LitresPer100Km) {
        mutableStateOf(tariffConfig.mx5LitresPer100Km.toString())
    }

    fun parseDoubleOrFallback(text: String, fallback: Double): Double =
        text.replace(",", ".").trim().toDoubleOrNull() ?: fallback

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Simulateur Tarifaire Configurable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Modifiez les coûts unitaires pour recalculer les projections en temps réel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Water, Electricity & Gas Base Rates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = waterText,
                    onValueChange = { value ->
                        waterText = value
                        value.replace(",", ".").toDoubleOrNull()?.let { parsed ->
                            onUpdateTariffConfig(tariffConfig.copy(waterRatePerM3 = parsed))
                        }
                    },
                    label = { Text("Tarif Eau (€ / m³)") },
                    suffix = { Text("€/m³") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = elecText,
                    onValueChange = { value ->
                        elecText = value
                        value.replace(",", ".").toDoubleOrNull()?.let { parsed ->
                            onUpdateTariffConfig(tariffConfig.copy(electricityRatePerKwh = parsed))
                        }
                    },
                    label = { Text("Tarif Élec (€ / kWh)") },
                    suffix = { Text("€/kWh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = gasText,
                onValueChange = { value ->
                    gasText = value
                    value.replace(",", ".").toDoubleOrNull()?.let { parsed ->
                        onUpdateTariffConfig(tariffConfig.copy(gasRatePerM3 = parsed))
                    }
                },
                label = { Text("Tarif Gaz Naturel (€ / m³)") },
                suffix = { Text("€/m³") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // 2. Electric Car: Twingo (kWh/100km combined with Electricity rate €/kWh)
            Text(
                text = "Véhicule Électrique — Twingo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = twingoKwhText,
                    onValueChange = { value ->
                        twingoKwhText = value
                        value.replace(",", ".").toDoubleOrNull()?.let { parsed ->
                            onUpdateTariffConfig(tariffConfig.copy(twingoKwhPer100Km = parsed))
                        }
                    },
                    label = { Text("Conso Twingo (kWh / 100 km)") },
                    suffix = { Text("kWh/100km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Coût combiné (× Tarif Élec)",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "%.2f € / 100 km".format(tariffConfig.twingoCostPer100Km),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider()

            // 3. Thermal Car 1: Volkswagen Tiguan (individual €/L and L/100km)
            Text(
                text = "Véhicule Thermique — Tiguan (conso : %.2f € / 100 km)".format(
                    tariffConfig.tiguanCostPer100Km
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tiguanPriceLText,
                    onValueChange = { value ->
                        tiguanPriceLText = value
                        val parsed = parseDoubleOrFallback(value, tariffConfig.tiguanPricePerLitre)
                        onUpdateTariffConfig(tariffConfig.copy(tiguanPricePerLitre = parsed))
                    },
                    label = { Text("Prix / Litre Tiguan") },
                    suffix = { Text("€/L") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = tiguanL100Text,
                    onValueChange = { value ->
                        tiguanL100Text = value
                        val parsed = parseDoubleOrFallback(value, tariffConfig.tiguanLitresPer100Km)
                        onUpdateTariffConfig(tariffConfig.copy(tiguanLitresPer100Km = parsed))
                    },
                    label = { Text("Litres / 100 km Tiguan") },
                    suffix = { Text("L/100km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // 4. Thermal Car 2: Mazda mx5 (individual €/L and L/100km)
            Text(
                text = "Véhicule Thermique — Mazda mx5 (conso : %.2f € / 100 km)".format(
                    tariffConfig.mx5CostPer100Km
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = mx5PriceLText,
                    onValueChange = { value ->
                        mx5PriceLText = value
                        val parsed = parseDoubleOrFallback(value, tariffConfig.mx5PricePerLitre)
                        onUpdateTariffConfig(tariffConfig.copy(mx5PricePerLitre = parsed))
                    },
                    label = { Text("Prix / Litre mx5") },
                    suffix = { Text("€/L") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = mx5L100Text,
                    onValueChange = { value ->
                        mx5L100Text = value
                        val parsed = parseDoubleOrFallback(value, tariffConfig.mx5LitresPer100Km)
                        onUpdateTariffConfig(tariffConfig.copy(mx5LitresPer100Km = parsed))
                    },
                    label = { Text("Litres / 100 km mx5") },
                    suffix = { Text("L/100km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
