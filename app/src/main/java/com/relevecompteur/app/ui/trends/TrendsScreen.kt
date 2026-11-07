package com.relevecompteur.app.ui.trends

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relevecompteur.app.domain.model.ChartMetricMode
import com.relevecompteur.app.domain.model.LeakAlert
import com.relevecompteur.app.domain.model.LeakSeverity
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.model.WaterVelocityInterval
import com.relevecompteur.app.domain.model.YearlyTrendSeries
import com.relevecompteur.app.ui.TrendsUiState
import kotlin.math.roundToLong

@Composable
fun TrendsScreen(
    state: TrendsUiState,
    onSelectMeter: (MeterType) -> Unit,
    onSelectMetricMode: (ChartMetricMode) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Meter Selector (9 meters) - Placed at the very top for immediate access
        Text(
            text = "Sélection du Compteur (9 compteurs suivis)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MeterType.entries.forEach { meter ->
                val selected = (meter == state.selectedMeter)
                FilterChip(
                    selected = selected,
                    onClick = { onSelectMeter(meter) },
                    label = {
                        Text(
                            text = "${meter.displayLabel} (${meter.unit})",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = when {
                        meter.isWaterMeter -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        meter == MeterType.GAZ -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        else -> null
                    }
                )
            }
        }

        // 2. Chart Metric Selector (Normalized Value vs Yearly Delta vs Daily Velocity)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartMetricMode.entries.forEach { mode ->
                FilterChip(
                    selected = (mode == state.metricMode),
                    onClick = { onSelectMetricMode(mode) },
                    label = {
                        Text(
                            text = mode.frenchLabel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }

        // 3. Multi-Year Comparative Line Chart Card (Current Year vs Year-1, Year-2, Year-3)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Courbes Multi-Années : ${state.selectedMeter.frenchName} (${state.selectedMeter.unit})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Axe X : Jour de l'année (1er Jan → 31 Déc) | ${state.metricMode.frenchLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Legend for Current Year vs N-1, N-2, N-3
                YearlySeriesLegend(seriesList = state.yearlySeries)

                Spacer(modifier = Modifier.height(10.dp))

                MultiYearTrendChartCanvas(
                    seriesList = state.yearlySeries,
                    unit = state.selectedMeter.unit,
                    metricMode = state.metricMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }
        }

        // 4. Leak & Velocity Heuristics ONLY for the currently selected meter (if it is a water meter)
        state.selectedMeterLeakAlert?.let { leakAlert ->
            if (leakAlert.severity != LeakSeverity.NONE) {
                SelectedWaterMeterLeakBanner(alert = leakAlert)
            }
            WaterMeterVelocityDetailCard(alert = leakAlert)
        }

        // 5. Yearly Data Points Breakdown Card
        YearlyReadingsSummaryCard(
            meterType = state.selectedMeter,
            seriesList = state.yearlySeries
        )
    }
}

@Composable
private fun SelectedWaterMeterLeakBanner(alert: LeakAlert) {
    val isCritical = alert.severity == LeakSeverity.CRITICAL
    val bgColor = if (isCritical) Color(0xFFFFEBEE) else Color(0xFFFFF3E0)
    val borderColor = if (isCritical) Color(0xFFD32F2F) else Color(0xFFF57C00)
    val titleColor = if (isCritical) Color(0xFFB71C1C) else Color(0xFFE65100)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerte fuite d'eau",
                    tint = borderColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "%.2f m³/j (×%.1f)".format(
                        alert.latestVelocityM3PerDay,
                        alert.ratioToBaseline
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = borderColor
                )
            }
            Text(
                text = alert.summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF37474F)
            )
        }
    }
}

private fun colorForYearOffset(offsetIndex: Int): Color = when (offsetIndex) {
    0 -> Color(0xFF0B57D0) // Current Year: Bold Primary Royal Blue
    1 -> Color(0xFF00897B) // Year - 1: Muted Teal
    2 -> Color(0xFFE65100) // Year - 2: Muted Amber/Orange
    else -> Color(0xFF7E57C2) // Year - 3: Muted Purple
}

@Composable
private fun YearlySeriesLegend(seriesList: List<YearlyTrendSeries>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        seriesList.forEach { series ->
            val color = colorForYearOffset(series.yearOffsetIndex)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (series.isCurrentYear) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                val suffix = if (series.isCurrentYear) " (Année courante)" else " (N-${series.yearOffsetIndex})"
                Text(
                    text = "${series.year}$suffix [${series.points.size} pts]",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (series.isCurrentYear) FontWeight.Bold else FontWeight.Normal,
                    color = if (series.isCurrentYear) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MultiYearTrendChartCanvas(
    seriesList: List<YearlyTrendSeries>,
    unit: String,
    metricMode: ChartMetricMode,
    modifier: Modifier = Modifier
) {
    val allPoints = seriesList.flatMap { it.points }
    if (allPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Aucune donnée historique pour ce compteur.")
        }
        return
    }

    val rawMinY = allPoints.minOf { it.plotValue }
    val rawMaxY = allPoints.maxOf { it.plotValue }
    val ySpan = (rawMaxY - rawMinY).let { if (it < 1e-3) 10.0 else it }
    val minY = (rawMinY - ySpan * 0.08).coerceAtLeast(0.0)
    val maxY = rawMaxY + ySpan * 0.10

    val monthLabels = listOf(
        1 to "Jan", 32 to "Fév", 60 to "Mar", 91 to "Avr",
        121 to "Mai", 152 to "Juin", 182 to "Juil", 213 to "Aoû",
        244 to "Sep", 274 to "Oct", 305 to "Nov", 335 to "Déc"
    )

    Canvas(modifier = modifier) {
        val leftPad = 58.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 28.dp.toPx()

        val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun dayToX(dayOfYear: Int): Float {
            val clamped = dayOfYear.coerceIn(1, 366)
            return leftPad + ((clamped - 1f) / 365f) * chartW
        }

        fun valToY(v: Double): Float {
            val norm = ((v - minY) / (maxY - minY).coerceAtLeast(1e-6)).toFloat().coerceIn(0f, 1f)
            return topPad + chartH * (1f - norm)
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(100, 110, 125)
            textSize = 26f
        }

        // Draw 4 horizontal Y-axis grid lines & labels
        val ySteps = 4
        for (i in 0..ySteps) {
            val frac = i.toFloat() / ySteps
            val yVal = minY + (maxY - minY) * (1.0 - frac)
            val yPx = topPad + chartH * frac

            drawLine(
                color = Color(0xFFE0E4EC),
                start = Offset(leftPad, yPx),
                end = Offset(leftPad + chartW, yPx),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            val formattedY = if (metricMode == ChartMetricMode.DAILY_VELOCITY) {
                "%.1f".format(yVal)
            } else {
                yVal.roundToLong().toString()
            }
            drawContext.canvas.nativeCanvas.drawText(
                formattedY,
                6f,
                yPx + 8f,
                textPaint
            )
        }

        // Draw vertical month grid lines & X-axis labels (Jan 1 .. Dec 31)
        for ((day, label) in monthLabels) {
            val xPx = dayToX(day)
            drawLine(
                color = Color(0xFFECEFF4),
                start = Offset(xPx, topPad),
                end = Offset(xPx, topPad + chartH),
                strokeWidth = 1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                xPx - 16f,
                topPad + chartH + 22.dp.toPx(),
                textPaint
            )
        }

        // Draw historical years first (Year-3, Year-2, Year-1) and Current Year last on top
        for (series in seriesList.sortedByDescending { it.yearOffsetIndex }) {
            val color = colorForYearOffset(series.yearOffsetIndex)
            val strokeW = if (series.isCurrentYear) 4.dp.toPx() else 2.2.dp.toPx()
            val pointRadius = if (series.isCurrentYear) 5.5.dp.toPx() else 3.5.dp.toPx()

            val pts = series.points.sortedBy { it.dayOfYear }
            if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { idx, pt ->
                    val x = dayToX(pt.dayOfYear)
                    val y = valToY(pt.plotValue)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = if (series.isCurrentYear) color else color.copy(alpha = 0.72f),
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            pts.forEach { pt ->
                val x = dayToX(pt.dayOfYear)
                val y = valToY(pt.plotValue)
                drawCircle(
                    color = Color.White,
                    radius = pointRadius + 2.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = color,
                    radius = pointRadius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun WaterMeterVelocityDetailCard(alert: LeakAlert) {
    val isAlert = alert.severity != LeakSeverity.NONE
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFFF8F6) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isAlert) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isAlert) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Analyse de Vélocité Hydrique & Fuites (${alert.meterType.displayLabel})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adaptée aux intervalles espacés de plusieurs semaines ou mois",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = alert.summary,
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text(
                text = "Derniers intervalles mesurés (ΔVolume / ΔJours) :",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            alert.flaggedIntervals.take(6).forEach { interval ->
                WaterIntervalRow(interval = interval)
            }
        }
    }
}

@Composable
private fun WaterIntervalRow(interval: WaterVelocityInterval) {
    val bgColor = if (interval.isAnomaly) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val textColor = if (interval.isAnomaly) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurface

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${interval.startDateLabel} → ${interval.endDateLabel} (${interval.daysElapsed} j)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "+${interval.deltaM3.roundToLong()} m³ | %.2f m³/j (%d L/j)".format(
                        interval.velocityM3PerDay,
                        interval.velocityLitresPerDay.roundToLong()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            if (interval.comment.isNotBlank()) {
                Text(
                    text = "Note : ${interval.comment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun YearlyReadingsSummaryCard(
    meterType: MeterType,
    seriesList: List<YearlyTrendSeries>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Récapitulatif Annuel — ${meterType.displayLabel} (${meterType.unit})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            seriesList.forEach { series ->
                val minNorm = series.points.minOfOrNull { it.normalizedValue } ?: 0.0
                val maxNorm = series.points.maxOfOrNull { it.normalizedValue } ?: 0.0
                val spanDelta = (maxNorm - minNorm).coerceAtLeast(0.0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Année ${series.year} (${series.points.size} relevés)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (series.isCurrentYear) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "${minNorm.roundToLong()} → ${maxNorm.roundToLong()} ${meterType.unit} (Δ +${spanDelta.roundToLong()} ${meterType.unit})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorForYearOffset(series.yearOffsetIndex)
                    )
                }
            }
        }
    }
}
