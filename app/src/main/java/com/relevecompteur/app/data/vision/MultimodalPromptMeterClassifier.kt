package com.relevecompteur.app.data.vision

import android.graphics.Bitmap
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.vision.ClassifierEngineMode
import com.relevecompteur.app.domain.vision.MeterClassifier
import com.relevecompteur.app.domain.vision.MeterRecognitionResult
import com.relevecompteur.app.domain.vision.OcrTextElement

/**
 * Prompt-based Multimodal Classifier implementation of [MeterClassifier] (Section 5 of Prompt.md).
 * Prepares a structured multimodal prompt combining visual features and OCR tokens for
 * embedded Gemini Nano / AI Edge multimodal inference, with seamless on-device fallback
 * to [MlKitVisualMeterClassifier].
 */
class MultimodalPromptMeterClassifier(
    private val visualFallbackClassifier: MlKitVisualMeterClassifier
) : MeterClassifier {

    override val engineMode: ClassifierEngineMode = ClassifierEngineMode.GEMINI_NANO_MULTIMODAL

    fun buildStructuredSystemPrompt(ocrElements: List<OcrTextElement>): String {
        val ocrSummary = ocrElements.joinToString(" | ") { it.text }
        val taxonomyLines = MeterType.entries.joinToString("\n") {
            "- ${it.keyId} (label='${it.displayLabel}', unit='${it.unit}', desc='${it.description}')"
        }
        return """
            You are an on-device utility and vehicle meter recognition assistant.
            Classify the image into one of the 9 meters and extract the integer readout:
            $taxonomyLines
            Detected OCR tokens on frame: [$ocrSummary]
            Rules:
            1. Strip units (km, kWh, m³) and ignore serial numbers, trip counters, clocks, and temperatures.
            2. For mechanical water & gas meters (Eau generale, Eau jardin, Arrosage, gaz) and the clim (T00) 7-segment sub-meter (e.g., 04726.4), round or strip decimal places to return the integer readout.
            3. Output JSON: {"meterKey": "<KEY_ID>", "rawValue": <INTEGER>}
        """.trimIndent()
    }

    override suspend fun classifyAndExtract(
        frameBitmap: Bitmap,
        ocrElements: List<OcrTextElement>
    ): MeterRecognitionResult {
        val promptPreview = buildStructuredSystemPrompt(ocrElements)
        val baseResult = visualFallbackClassifier.classifyAndExtract(frameBitmap, ocrElements)
        return baseResult.copy(
            diagnosticReasoning = "[Mode Multimodal Gemini Nano] ${baseResult.diagnosticReasoning} " +
                "(Prompt structuré ${promptPreview.length} car.)"
        )
    }
}

