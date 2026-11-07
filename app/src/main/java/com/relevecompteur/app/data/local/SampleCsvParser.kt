package com.relevecompteur.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.usecase.OffsetAnalyzer
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses the bundled GoogleSheetSample.csv and meter_offsets.json assets,
 * as well as live rows retrieved from Google Sheets API v4.
 */
object SampleCsvParser {

    private data class OffsetsAssetPayload(
        val version: Int = 1,
        val offsets: Map<String, Double> = emptyMap()
    )

    fun loadInitialOffsetsFromAsset(context: Context): Map<MeterType, Double> {
        val result = mutableMapOf<MeterType, Double>()
        MeterType.entries.forEach { result[it] = it.defaultOffset }

        runCatching {
            context.assets.open("meter_offsets.json").use { input ->
                val reader = InputStreamReader(input, Charsets.UTF_8)
                val type = object : TypeToken<OffsetsAssetPayload>() {}.type
                val payload: OffsetsAssetPayload = Gson().fromJson(reader, type)
                payload.offsets.forEach { (key, value) ->
                    MeterType.fromSheetLabel(key)?.let { meter ->
                        result[meter] = value
                    }
                }
            }
        }
        return result
    }

    fun loadSampleCsvRows(context: Context): List<OffsetAnalyzer.RawSheetRow> {
        val rows = mutableListOf<OffsetAnalyzer.RawSheetRow>()
        runCatching {
            context.assets.open("GoogleSheetSample.csv").use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        parseCsvLineToRow(line)?.let { rows.add(it) }
                    }
                }
            }
        }
        return rows
    }

    /**
     * Parses a single CSV line handling quoted fields with commas (e.g., "59,953").
     */
    fun parseCsvLineToRow(line: String): OffsetAnalyzer.RawSheetRow? {
        if (line.isBlank()) return null
        val columns = splitCsvLine(line)
        if (columns.size < 3) return null

        val dateStr = columns[0].trim()
        val meterLabel = columns[1].trim()
        val rawVal = parseSheetNumber(columns[2]) ?: return null
        val normVal = if (columns.size >= 4) parseSheetNumber(columns[3]) else null
        val comment = if (columns.size >= 5) columns[4].trim() else ""

        if (dateStr.isEmpty() || meterLabel.isEmpty()) return null

        return OffsetAnalyzer.RawSheetRow(
            dateString = dateStr,
            meterLabel = meterLabel,
            rawValue = rawVal,
            normalizedValueOrNull = normVal,
            comment = comment
        )
    }

    /**
     * Converts Google Sheets API v4 ValueRange rows into [OffsetAnalyzer.RawSheetRow].
     */
    fun parseGoogleSheetValues(sheetValues: List<List<Any>>): List<OffsetAnalyzer.RawSheetRow> {
        val rows = mutableListOf<OffsetAnalyzer.RawSheetRow>()
        for (row in sheetValues) {
            if (row.size < 3) continue
            val dateStr = row[0].toString().trim()
            if (dateStr.equals("Date", ignoreCase = true)) continue
            val meterLabel = row[1].toString().trim()
            val rawVal = parseSheetNumber(row[2].toString()) ?: continue
            val normVal = if (row.size >= 4) parseSheetNumber(row[3].toString()) else null
            val comment = if (row.size >= 5) row[4].toString().trim() else ""

            rows.add(
                OffsetAnalyzer.RawSheetRow(
                    dateString = dateStr,
                    meterLabel = meterLabel,
                    rawValue = rawVal,
                    normalizedValueOrNull = normVal,
                    comment = comment
                )
            )
        }
        return rows
    }

    fun parseSheetNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .trim()
            .replace("\"", "")
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace(",", "")
        return cleaned.toDoubleOrNull()
    }

    private fun splitCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                }
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(ch)
                }
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}

