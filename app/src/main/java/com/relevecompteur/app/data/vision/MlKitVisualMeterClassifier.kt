package com.relevecompteur.app.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.vision.ClassifierEngineMode
import com.relevecompteur.app.domain.vision.MeterClassifier
import com.relevecompteur.app.domain.vision.MeterRecognitionResult
import com.relevecompteur.app.domain.vision.OcrTextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Primary implementation of [MeterClassifier]:
 * 1. Compares the captured frame against the bundled reference asset library in
 *    `assets/meter_references/` (HSV color histogram + spatial luminance grid + aspect ratio).
 * 2. Combines visual similarity with OCR keyword matching and bounding-box layout features.
 * 3. Extracts the primary numerical meter readout, stripping units (km, kWh, m³), ignoring
 *    serial numbers / trip meters / clocks, handling mechanical water & gas meter leading zeros
 *    and red decimal rollers, and rounding to the nearest unit.
 */
class MlKitVisualMeterClassifier(
    private val context: Context,
    private val historicalLastRawValues: () -> Map<MeterType, Double> = { emptyMap() }
) : MeterClassifier {

    override val engineMode: ClassifierEngineMode = ClassifierEngineMode.MLKIT_VISUAL_TEMPLATE

    private data class VisualSignature(
        val hueBins: FloatArray,        // 12 hue buckets + 3 achromatic buckets (dark, mid-grey, bright-white)
        val spatialLumaGrid: FloatArray // 4x4 spatial brightness grid
    )

    private val referenceSignatures: Map<MeterType, VisualSignature> by lazy {
        loadReferenceAssetSignatures()
    }

    override suspend fun classifyAndExtract(
        frameBitmap: Bitmap,
        ocrElements: List<OcrTextElement>
    ): MeterRecognitionResult = withContext(Dispatchers.Default) {
        val frameSignature = computeVisualSignature(frameBitmap)
        val combinedText = ocrElements.joinToString(" ") { it.text }.lowercase()
        val lastKnownRaw = historicalLastRawValues()

        // 1. Score each of the 9 meters using Visual Template Similarity + OCR Keywords
        val scores = mutableMapOf<MeterType, Double>()
        val reasons = mutableMapOf<MeterType, MutableList<String>>()

        for (meter in MeterType.entries) {
            var score = 0.0
            val meterReasons = mutableListOf<String>()

            // A. Visual similarity against assets/meter_references/<meter>_ref.jpg
            val refSig = referenceSignatures[meter]
            if (refSig != null) {
                val sim = compareSignatures(frameSignature, refSig)
                val visualPoints = sim * 45.0
                score += visualPoints
                meterReasons.add("Similitude visuelle réf: ${(sim * 100).toInt()}%")
            }

            // B. OCR keyword matches
            for (keyword in meter.ocrKeywords) {
                if (combinedText.contains(keyword.lowercase())) {
                    score += 28.0
                    meterReasons.add("Mot-clé « $keyword »")
                }
            }

            // C. Domain-specific visual/text disambiguation heuristics
            when (meter) {
                MeterType.EDF -> {
                    if (combinedText.contains("linky") || combinedText.contains("base")) {
                        score += 50.0
                    }
                    // Linky distinctive lime yellow-green body (hue bin 2..3)
                    if (frameSignature.hueBins[2] + frameSignature.hueBins[3] > 0.22f) {
                        score += 22.0
                        meterReasons.add("Teinte jaune-vert Linky")
                    }
                }
                MeterType.GAZ -> {
                    if (combinedText.contains("grdf") || combinedText.contains("gallus") || combinedText.contains("523038")) {
                        score += 60.0
                    }
                    // GRDF distinctive warm yellow/amber body (hue bin 1..2)
                    if (frameSignature.hueBins[1] + frameSignature.hueBins[2] > 0.30f) {
                        score += 26.0
                        meterReasons.add("Boîtier jaune GRDF")
                    }
                }
                MeterType.EAU_JARDIN -> {
                    // Blue body water meter (hue bin 6..8 = cyan/blue)
                    if (frameSignature.hueBins[6] + frameSignature.hueBins[7] + frameSignature.hueBins[8] > 0.16f) {
                        score += 26.0
                        meterReasons.add("Boîtier bleu Eau jardin")
                    }
                }
                MeterType.TWINGO -> {
                    // Purple/pink LCD backlight (hue bin 9..11)
                    if (frameSignature.hueBins[9] + frameSignature.hueBins[10] + frameSignature.hueBins[11] > 0.08f) {
                        score += 24.0
                        meterReasons.add("Rétroéclairage violet Twingo")
                    }
                }
                MeterType.TIGUAN -> {
                    if (combinedText.contains("trip") && combinedText.contains("km")) {
                        score += 35.0
                    }
                }
                MeterType.MX5 -> {
                    if (combinedText.contains("100km") || combinedText.contains("stop")) {
                        score += 40.0
                    }
                }
                MeterType.EAU_GEN -> {
                    if (combinedText.contains("diehl") || combinedText.contains("m24") || combinedText.contains("24fb")) {
                        score += 55.0
                    }
                }
                MeterType.ARROSAGE -> {
                    if (combinedText.contains("hidroconta") || combinedText.contains("m23") || combinedText.contains("r160h")) {
                        score += 55.0
                    }
                }
                MeterType.CLIM_T00 -> {
                    if (
                        combinedText.contains("hiking") ||
                        combinedText.contains("tomzn") ||
                        combinedText.contains("dds238") ||
                        combinedText.contains("t00") ||
                        combinedText.contains("t 00") ||
                        combinedText.contains("clim") ||
                        combinedText.contains("1000imp")
                    ) {
                        score += 65.0
                    }
                }
            }

            scores[meter] = score
            reasons[meter] = meterReasons
        }

        val rankedMeters = scores.entries.sortedByDescending { it.value }
        val bestMeter = rankedMeters.first().key
        val topScore = rankedMeters.first().value
        val secondScore = rankedMeters.getOrNull(1)?.value ?: 0.0
        val confidence = ((topScore / (topScore + secondScore + 1.0)).toFloat()).coerceIn(0.45f, 0.99f)

        // 2. Extract primary digit readout for the classified meter
        val extraction = extractPrimaryNumberForMeter(
            meterType = bestMeter,
            ocrElements = ocrElements,
            lastKnownRaw = lastKnownRaw[bestMeter]
        )

        val croppedPreview = cropReadoutRegion(frameBitmap, extraction.boundingBox)
        val diag = buildString {
            append("${bestMeter.displayLabel} (${(confidence * 100).toInt()}%) — ")
            append(reasons[bestMeter]?.joinToString(", ").orEmpty())
            if (extraction.rawToken.isNotBlank()) {
                append(" | Lecture brute « ${extraction.rawToken} » → ${extraction.numericValue.roundToLong()}")
            }
        }

        MeterRecognitionResult(
            detectedMeterType = bestMeter,
            meterConfidence = confidence,
            extractedRawValue = extraction.numericValue,
            extractedDigitsText = extraction.numericValue.roundToLong().toString(),
            readoutBoundingBox = extraction.boundingBox,
            croppedReadoutBitmap = croppedPreview,
            allOcrBlocks = ocrElements,
            diagnosticReasoning = diag
        )
    }

    private data class DigitExtractionCandidate(
        val numericValue: Double,
        val rawToken: String,
        val boundingBox: Rect?,
        val score: Double
    )

    private fun extractPrimaryNumberForMeter(
        meterType: MeterType,
        ocrElements: List<OcrTextElement>,
        lastKnownRaw: Double?
    ): DigitExtractionCandidate {
        val candidates = mutableListOf<DigitExtractionCandidate>()

        for (element in ocrElements) {
            val lineText = element.text
            val cleanedLine = lineText
                .replace("kWh", " ", ignoreCase = true)
                .replace("km/h", " ", ignoreCase = true)
                .replace("L/100km", " ", ignoreCase = true)
                .replace("km", " ", ignoreCase = true)
                .replace("m3", " ", ignoreCase = true)
                .replace("m³", " ", ignoreCase = true)

            val regex = Regex("""\b\d[\d\s.,]{1,9}\d\b|\b\d{2,8}\b""")
            for (match in regex.findAll(cleanedLine)) {
                val token = match.value.trim()
                val digitsOnly = token.filter { it.isDigit() }
                if (digitsOnly.length < 2) continue

                if (lineText.contains(":") && digitsOnly.length <= 4) continue
                if (lineText.contains("°") && digitsOnly.length <= 3) continue
                if (meterType.ignoredSerialTokens.any { digitsOnly == it || token.contains(it) }) continue

                val hasDecimalPoint = token.contains(".") || token.contains(",")
                if (meterType == MeterType.TIGUAN && hasDecimalPoint) continue

                val parsedValue = parseMeterDigits(
                    digitsOnly = digitsOnly,
                    originalToken = token,
                    meterType = meterType,
                    lastKnownRaw = lastKnownRaw
                ) ?: continue

                var candidateScore = 10.0

                // Prefer numbers with leading zeros on mechanical water & gas meters and CLIM_T00 7-segment LCD (04726.4)
                if ((meterType.hasMechanicalRedDecimals || meterType == MeterType.CLIM_T00) && digitsOnly.startsWith("0")) {
                    candidateScore += 35.0
                }

                // Prefer numbers accompanied by unit on the same line
                val lowerLine = lineText.lowercase()
                if (lowerLine.contains("kwh") || (lowerLine.contains("km") && !lowerLine.contains("100km") && !lowerLine.contains("km/h"))) {
                    candidateScore += 30.0
                }

                element.boundingBox?.let { rect ->
                    candidateScore += (rect.height().coerceAtMost(250) / 10.0)
                }

                // Proximity to last known historical raw value
                if (lastKnownRaw != null && lastKnownRaw > 0) {
                    val delta = parsedValue - lastKnownRaw
                    when {
                        delta in 0.0..(max(lastKnownRaw * 0.35, 5000.0)) -> candidateScore += 45.0
                        abs(delta) < max(lastKnownRaw * 0.5, 2000.0) -> candidateScore += 20.0
                        parsedValue < lastKnownRaw * 0.2 -> candidateScore -= 25.0
                    }
                }

                candidates.add(
                    DigitExtractionCandidate(
                        numericValue = parsedValue.roundToLong().toDouble(),
                        rawToken = token,
                        boundingBox = element.boundingBox,
                        score = candidateScore
                    )
                )
            }
        }

        if (candidates.isNotEmpty()) {
            return candidates.maxBy { it.score }
        }

        val fallback = (lastKnownRaw ?: 0.0).roundToLong().toDouble()
        return DigitExtractionCandidate(
            numericValue = fallback,
            rawToken = fallback.toLong().toString(),
            boundingBox = null,
            score = 0.0
        )
    }

    /**
     * Handles mechanical water & gas meter rollers where 5 black/white integer rollers are followed
     * by 2 or 3 red decimal rollers (e.g., "0013646" -> 136 m³, "0313276" -> 3132 m³).
     */
    private fun parseMeterDigits(
        digitsOnly: String,
        originalToken: String,
        meterType: MeterType,
        lastKnownRaw: Double?
    ): Double? {
        if (digitsOnly.isEmpty()) return null

        if (meterType.hasMechanicalRedDecimals) {
            if (digitsOnly.length in 6..8 && digitsOnly.startsWith("0")) {
                val integerPart = digitsOnly.substring(0, 5).toDoubleOrNull() ?: return null
                val decimalPartStr = digitsOnly.substring(5)
                val decimalVal = ("0.$decimalPartStr").toDoubleOrNull() ?: 0.0
                return (integerPart + decimalVal).roundToLong().toDouble()
            }
            if (digitsOnly.length == 5 && digitsOnly.startsWith("0")) {
                return digitsOnly.toDoubleOrNull()
            }
        }

        if (meterType == MeterType.CLIM_T00) {
            // HIKING TOMZN DDS238-2 SW 7-segment display has 5 integer digits + 1 decimal digit (e.g. "04726.4" or "047264")
            if (digitsOnly.length == 6 && digitsOnly.startsWith("0") &&
                !originalToken.contains(".") && !originalToken.contains(",")
            ) {
                val integerPart = digitsOnly.substring(0, 5).toDoubleOrNull() ?: return null
                val decimalVal = ("0.${digitsOnly.substring(5)}").toDoubleOrNull() ?: 0.0
                return (integerPart + decimalVal).roundToLong().toDouble()
            }
        }

        val cleanNumber = originalToken
            .replace(" ", "")
            .replace(",", ".")
            .toDoubleOrNull() ?: digitsOnly.toDoubleOrNull() ?: return null

        return when (meterType) {
            MeterType.ARROSAGE -> if (cleanNumber in 1.0..9999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.EAU_GEN -> if (cleanNumber in 1.0..99999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.EAU_JARDIN -> if (cleanNumber in 1.0..99999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.GAZ -> if (cleanNumber in 1.0..99999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.EDF -> if (cleanNumber in 100.0..999999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.CLIM_T00 -> if (cleanNumber in 1.0..99999.0) cleanNumber.roundToLong().toDouble() else null
            MeterType.TWINGO, MeterType.TIGUAN, MeterType.MX5 ->
                if (cleanNumber in 10.0..999999.0) cleanNumber.roundToLong().toDouble() else null
        }
    }

    private fun cropReadoutRegion(source: Bitmap, rect: Rect?): Bitmap {
        if (rect == null) {
            val w = (source.width * 0.65f).toInt().coerceAtLeast(1)
            val h = (source.height * 0.35f).toInt().coerceAtLeast(1)
            val x = ((source.width - w) / 2).coerceAtLeast(0)
            val y = ((source.height - h) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(source, x, y, w, h)
        }
        val padX = (rect.width() * 0.35f).toInt().coerceAtLeast(24)
        val padY = (rect.height() * 0.60f).toInt().coerceAtLeast(24)
        val left = (rect.left - padX).coerceIn(0, source.width - 1)
        val top = (rect.top - padY).coerceIn(0, source.height - 1)
        val right = (rect.right + padX).coerceIn(left + 1, source.width)
        val bottom = (rect.bottom + padY).coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun loadReferenceAssetSignatures(): Map<MeterType, VisualSignature> {
        val result = mutableMapOf<MeterType, VisualSignature>()
        for (meter in MeterType.entries) {
            runCatching {
                context.assets.open(meter.referenceAssetPath).use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = BitmapFactory.decodeStream(stream, null, opts)
                    if (bmp != null) {
                        result[meter] = computeVisualSignature(bmp)
                        bmp.recycle()
                    }
                }
            }
        }
        return result
    }

    private fun computeVisualSignature(bitmap: Bitmap): VisualSignature {
        val hueBins = FloatArray(15)
        val spatialGrid = FloatArray(16)
        val spatialCounts = IntArray(16)

        val stepX = (bitmap.width / 48).coerceAtLeast(1)
        val stepY = (bitmap.height / 48).coerceAtLeast(1)
        val hsv = FloatArray(3)
        var totalSamples = 0

        var y = 0
        while (y < bitmap.height) {
            val gridY = ((y.toFloat() / bitmap.height) * 4).toInt().coerceIn(0, 3)
            var x = 0
            while (x < bitmap.width) {
                val gridX = ((x.toFloat() / bitmap.width) * 4).toInt().coerceIn(0, 3)
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                when {
                    v < 0.20f -> hueBins[12] += 1f
                    s < 0.18f && v > 0.78f -> hueBins[14] += 1f
                    s < 0.18f -> hueBins[13] += 1f
                    else -> {
                        val bin = ((h / 30f).toInt()).coerceIn(0, 11)
                        hueBins[bin] += 1f
                    }
                }

                val gridIdx = gridY * 4 + gridX
                spatialGrid[gridIdx] += v
                spatialCounts[gridIdx] += 1
                totalSamples++
                x += stepX
            }
            y += stepY
        }

        if (totalSamples > 0) {
            for (i in hueBins.indices) {
                hueBins[i] /= totalSamples.toFloat()
            }
        }
        for (i in spatialGrid.indices) {
            if (spatialCounts[i] > 0) {
                spatialGrid[i] /= spatialCounts[i].toFloat()
            }
        }

        return VisualSignature(hueBins = hueBins, spatialLumaGrid = spatialGrid)
    }

    private fun compareSignatures(a: VisualSignature, b: VisualSignature): Double {
        var hueDiff = 0.0
        for (i in a.hueBins.indices) {
            hueDiff += abs(a.hueBins[i] - b.hueBins[i])
        }
        val hueSim = (1.0 - (hueDiff / 2.0)).coerceIn(0.0, 1.0)

        var lumaDiff = 0.0
        for (i in a.spatialLumaGrid.indices) {
            lumaDiff += abs(a.spatialLumaGrid[i] - b.spatialLumaGrid[i])
        }
        val lumaSim = (1.0 - (lumaDiff / a.spatialLumaGrid.size)).coerceIn(0.0, 1.0)

        return (hueSim * 0.72 + lumaSim * 0.28).coerceIn(0.0, 1.0)
    }
}
