package com.relevecompteur.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.relevecompteur.app.domain.model.MeterReading
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.ui.CaptureModalUiState
import kotlin.math.roundToLong

@Composable
fun CaptureScreen(
    modalState: CaptureModalUiState,
    meterOffsets: Map<MeterType, Double>,
    recentReadings: List<MeterReading>,
    onBitmapCaptured: (Bitmap) -> Unit,
    onTestReferenceAsset: (MeterType) -> Unit,
    onOpenManualModal: (MeterType) -> Unit,
    onUpdateModalMeterType: (MeterType) -> Unit,
    onUpdateModalRawValue: (String) -> Unit,
    onUpdateModalComment: (String) -> Unit,
    onCancelModal: () -> Unit,
    onRetakeCapture: () -> Unit,
    onConfirmAndSave: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        onBitmapCaptured(bmp)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. CameraX Viewfinder with Alignment Reticle & Flash Toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF10141C))
        ) {
            if (hasCameraPermission) {
                CameraXViewfinderWithReticle(
                    isAnalyzing = modalState.isAnalyzing,
                    onBitmapCaptured = onBitmapCaptured,
                    onPickGalleryImage = { photoPickerLauncher.launch("image/*") },
                    onManualEntry = { onOpenManualModal(MeterType.EDF) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Autorisation Caméra requise pour la lecture optique des compteurs",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Autoriser la caméra")
                        }
                        OutlinedButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                            Text("Importer une photo", color = Color.White)
                        }
                    }
                }
            }

            if (modalState.isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Column {
                                Text(
                                    text = "Identification & OCR en cours…",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Comparaison avec /assets/meter_references/",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Reference Asset Test Bar & Latest Captures Summary
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tester avec une photo de référence (OCR + Classification) :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recentReadings.size} relevés",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MeterType.entries.forEach { meter ->
                        AssistChip(
                            onClick = { onTestReferenceAsset(meter) },
                            label = {
                                Text(
                                    text = "${meter.displayLabel} (${meter.unit})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // 3. Confirmation Modal / Sheet (Section 4 - Activity 1.3)
    if (modalState.isVisible) {
        MeterConfirmationModalSheet(
            modalState = modalState,
            meterOffsets = meterOffsets,
            onUpdateMeterType = onUpdateModalMeterType,
            onUpdateRawValue = onUpdateModalRawValue,
            onUpdateComment = onUpdateModalComment,
            onCancel = onCancelModal,
            onRetake = onRetakeCapture,
            onConfirmAndSave = onConfirmAndSave
        )
    }
}

@Composable
private fun CameraXViewfinderWithReticle(
    isAnalyzing: Boolean,
    onBitmapCaptured: (Bitmap) -> Unit,
    onPickGalleryImage: () -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var isFlashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                cameraProvider.unbindAll()
                cameraControl = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Alignment Reticle Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleWidth = size.width * 0.78f
            val reticleHeight = size.height * 0.32f
            val left = (size.width - reticleWidth) / 2f
            val top = (size.height - reticleHeight) / 2f - 20.dp.toPx()

            // Outer dashed guide
            drawRoundRect(
                color = Color(0xFF4FC3F7),
                topLeft = Offset(left, top),
                size = Size(reticleWidth, reticleHeight),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 14f), 0f)
                )
            )

            // Inner digit target box
            val innerW = reticleWidth * 0.75f
            val innerH = reticleHeight * 0.45f
            drawRoundRect(
                color = Color(0xFF81C784).copy(alpha = 0.85f),
                topLeft = Offset((size.width - innerW) / 2f, top + (reticleHeight - innerH) / 2f),
                size = Size(innerW, innerH),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Top overlay instruction pill + Flash Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Alignez les chiffres du compteur dans le cadre vert",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            FilledIconButton(
                onClick = {
                    val nextState = !isFlashEnabled
                    isFlashEnabled = nextState
                    cameraControl?.cameraControl?.enableTorch(nextState)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isFlashEnabled) Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.65f),
                    contentColor = if (isFlashEnabled) Color.Black else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Activer/Désactiver le flash"
                )
            }
        }

        // Bottom Camera Capture Controls (Gallery, Capture Shutter, Manual Entry)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onPickGalleryImage,
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Importer depuis la galerie"
                )
            }

            FloatingActionButton(
                onClick = {
                    if (!isAnalyzing) {
                        captureFrameFromCameraX(context, imageCapture, onBitmapCaptured)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capturer le compteur",
                    modifier = Modifier.size(32.dp)
                )
            }

            FilledIconButton(
                onClick = onManualEntry,
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = "Saisie manuelle"
                )
            }
        }
    }
}

private fun captureFrameFromCameraX(
    context: Context,
    imageCapture: ImageCapture,
    onBitmapReady: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                if (bitmap != null) {
                    onBitmapReady(bitmap)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                // Fallback if emulator camera has no live frame
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return runCatching {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MeterConfirmationModalSheet(
    modalState: CaptureModalUiState,
    meterOffsets: Map<MeterType, Double>,
    onUpdateMeterType: (MeterType) -> Unit,
    onUpdateRawValue: (String) -> Unit,
    onUpdateComment: (String) -> Unit,
    onCancel: () -> Unit,
    onRetake: () -> Unit,
    onConfirmAndSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedMeter = modalState.selectedMeterType
    val activeOffset = meterOffsets[selectedMeter] ?: selectedMeter.defaultOffset
    val calculatedNormalized = modalState.computeNormalizedValue(meterOffsets)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Vérification du Relevé Optique",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Cropped preview of the meter readout
            modalState.recognitionResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        result.croppedReadoutBitmap?.let { croppedBmp ->
                            Image(
                                bitmap = croppedBmp.asImageBitmap(),
                                contentDescription = "Aperçu cadré de la lecture du compteur",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(125.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = result.diagnosticReasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Dropdown pre-filled with auto-detected meter type (editable)
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "${selectedMeter.displayLabel} — ${selectedMeter.frenchName} (${selectedMeter.unit})",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type de compteur détecté") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    MeterType.entries.forEach { meter ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${meter.displayLabel} (${meter.unit})",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = meter.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onUpdateMeterType(meter)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 3. Numeric input field pre-filled with extracted raw value (editable)
            OutlinedTextField(
                value = modalState.rawValueInput,
                onValueChange = onUpdateRawValue,
                label = { Text("Valeur brute lue sur le cadran (${selectedMeter.unit})") },
                suffix = { Text(selectedMeter.unit, fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Display of the resulting calculated Normalized Value based on configured offsets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Valeur Normalisée Calculée",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Brut (${modalState.parsedRawValue.roundToLong()}) + Offset (+${activeOffset.roundToLong()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "${calculatedNormalized.roundToLong()} ${selectedMeter.unit}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 5. Optional Comment / Remark field + quick suggestion chips
            OutlinedTextField(
                value = modalState.commentInput,
                onValueChange = onUpdateComment,
                label = { Text("Commentaire / Remarque (optionnel)") },
                placeholder = { Text("Ex: Mise en route chauffage, Remplissage piscine…") },
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val quickComments = listOf(
                    "Mise en route chauffage",
                    "Fin chauffage",
                    "Remplissage piscine",
                    "Début clim"
                )
                quickComments.forEach { preset ->
                    SuggestionChip(
                        onClick = { onUpdateComment(preset) },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Preview of the 5-column Google Sheet row to be appended
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val dateFormatted = MeterReading.formatForSheet(modalState.captureDate)
                Text(
                    text = "Ligne Google Sheet (5 col.) : $dateFormatted | ${selectedMeter.displayLabel} | " +
                        "${modalState.parsedRawValue.roundToLong()} | ${calculatedNormalized.roundToLong()} | " +
                        modalState.commentInput.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // 6. Action buttons: Annuler, Reprendre, Valider et Enregistrer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Annuler")
                }

                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reprendre")
                }

                Button(
                    onClick = onConfirmAndSave,
                    modifier = Modifier.weight(1.6f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Valider et Enregistrer")
                }
            }
        }
    }
}

