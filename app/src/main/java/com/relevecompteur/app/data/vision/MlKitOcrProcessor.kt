package com.relevecompteur.app.data.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.relevecompteur.app.domain.vision.OcrTextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MultiOrientationOcrResult(
    val orientedBitmap: Bitmap,
    val rotationDegrees: Int,
    val elements: List<OcrTextElement>
)

/**
 * Runs on-device ML Kit Text Recognition across multiple orientations (0°, 90°, 270°, 180°)
 * because physical meters and car dashboards are frequently captured sideways or upside-down.
 */
class MlKitOcrProcessor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBestOrientation(sourceBitmap: Bitmap): MultiOrientationOcrResult =
        withContext(Dispatchers.Default) {
            val scaledSource = scaleDownIfNeeded(sourceBitmap, maxDimension = 1600)
            val candidateRotations = listOf(0, 90, 270, 180)

            var bestRotation = 0
            var bestBitmap = scaledSource
            var bestElements = emptyList<OcrTextElement>()
            var bestScore = -1.0

            for (rotation in candidateRotations) {
                val rotated = if (rotation == 0) scaledSource else rotateBitmap(scaledSource, rotation)
                val elements = runOcrOnBitmap(rotated)
                val score = scoreOcrQuality(elements)
                if (score > bestScore) {
                    bestScore = score
                    bestRotation = rotation
                    bestBitmap = rotated
                    bestElements = elements
                }
            }

            MultiOrientationOcrResult(
                orientedBitmap = bestBitmap,
                rotationDegrees = bestRotation,
                elements = bestElements
            )
        }

    private suspend fun runOcrOnBitmap(bitmap: Bitmap): List<OcrTextElement> {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            val results = mutableListOf<OcrTextElement>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    results.add(
                        OcrTextElement(
                            text = line.text.trim(),
                            boundingBox = line.boundingBox,
                            confidence = line.confidence ?: 0.85f
                        )
                    )
                }
            }
            results
        }.getOrDefault(emptyList())
    }

    /**
     * Scores an orientation based on digit sequences (3..7 digits) and known meter keywords.
     */
    private fun scoreOcrQuality(elements: List<OcrTextElement>): Double {
        if (elements.isEmpty()) return 0.0
        var score = 0.0
        val domainKeywords = listOf(
            "km", "kwh", "linky", "base", "itron", "diehl", "hidroconta",
            "trip", "eco", "m23", "m24", "02ca", "100km", "bar",
            "hiking", "tomzn", "dds238", "1000imp", "differentiel", "grdf", "gallus"
        )
        for (el in elements) {
            val lower = el.text.lowercase()
            val digitCount = lower.count { it.isDigit() }
            if (digitCount in 3..8) {
                score += digitCount * 3.0
            }
            for (kw in domainKeywords) {
                if (lower.contains(kw)) {
                    score += 18.0
                }
            }
        }
        return score
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

