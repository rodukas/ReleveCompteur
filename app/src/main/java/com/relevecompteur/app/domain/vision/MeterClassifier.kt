package com.relevecompteur.app.domain.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.relevecompteur.app.domain.model.MeterType

enum class ClassifierEngineMode(val frenchLabel: String, val description: String) {
    MLKIT_VISUAL_TEMPLATE(
        "ML Kit OCR + Empreintes Visuelles",
        "Reconnaissance rapide sur appareil par profils de couleur, gabarits de cadran et mots-clés OCR"
    ),
    GEMINI_NANO_MULTIMODAL(
        "Inférence Multimodale (Gemini Nano / Prompt)",
        "Classification multimodale par prompt structuré avec repli local ML Kit"
    )
}

data class OcrTextElement(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float = 1.0f
)

data class MeterRecognitionResult(
    val detectedMeterType: MeterType,
    val meterConfidence: Float,
    val extractedRawValue: Double,
    val extractedDigitsText: String,
    val readoutBoundingBox: Rect?,
    val croppedReadoutBitmap: Bitmap?,
    val allOcrBlocks: List<OcrTextElement>,
    val diagnosticReasoning: String
)

/**
 * Explicit matching interface (Section 5 of Prompt.md) capable of switching between
 * ML Kit visual feature similarity and prompt-based multimodal inference.
 */
interface MeterClassifier {
    val engineMode: ClassifierEngineMode

    suspend fun classifyAndExtract(
        frameBitmap: Bitmap,
        ocrElements: List<OcrTextElement>
    ): MeterRecognitionResult
}

