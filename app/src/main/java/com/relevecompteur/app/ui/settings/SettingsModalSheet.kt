package com.relevecompteur.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.relevecompteur.app.data.local.AppSettingsState
import com.relevecompteur.app.data.remote.GoogleAuthManager
import com.relevecompteur.app.domain.model.MeterType
import com.relevecompteur.app.domain.vision.ClassifierEngineMode
import com.relevecompteur.app.ui.AuthUiEvent
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: AppSettingsState,
    googleAuthManager: GoogleAuthManager,
    authUiEvent: Flow<AuthUiEvent>,
    isSyncing: Boolean,
    permissionStatusMessage: String?,
    onGoogleSignedIn: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onConsentGranted: () -> Unit,
    onSaveSpreadsheetConfig: (String, String) -> Unit,
    onVerifyPermissions: () -> Unit,
    onSyncSheetNow: () -> Unit,
    onUpdateMeterOffset: (MeterType, Double) -> Unit,
    onResetOffsetsFromCsv: () -> Unit,
    onSelectClassifierMode: (ClassifierEngineMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var spreadsheetInput by remember(settings.spreadsheetId) {
        mutableStateOf(settings.spreadsheetId)
    }
    var sheetTabInput by remember(settings.sheetTabName) {
        mutableStateOf(settings.sheetTabName)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching {
                val account = task.result
                account?.email?.let { onGoogleSignedIn(it) }
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onConsentGranted() // Retry the sheet R/W verification
        }
    }

    LaunchedEffect(authUiEvent) {
        authUiEvent.collect { event ->
            when (event) {
                is AuthUiEvent.LaunchConsent -> consentLauncher.launch(event.intent)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configuration Google Sheets & Offsets Compteurs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Google Identity OAuth 2.0 Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Authentification Google OAuth 2.0",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings.googleAccountEmail.isNotBlank()) {
                                    "Connecté : ${settings.googleAccountEmail}"
                                } else {
                                    "Non connecté (Scopes: spreadsheets + drive.file)"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                googleSignInLauncher.launch(googleAuthManager.getSignInIntent())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (settings.googleAccountEmail.isBlank()) {
                                    "Se connecter avec Google"
                                } else {
                                    "Changer de compte"
                                }
                            )
                        }

                        if (settings.googleAccountEmail.isNotBlank()) {
                            OutlinedButton(onClick = onGoogleSignOut) {
                                Text("Déconnexion")
                            }
                        }
                    }
                }
            }

            // 2. Target Google Spreadsheet ID & Read/Write Permission Verification
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Fichier Google Sheet Cible (Google Drive)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = spreadsheetInput,
                        onValueChange = { spreadsheetInput = it },
                        label = { Text("ID ou URL complète du Google Sheet") },
                        placeholder = { Text("Ex: 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = sheetTabInput,
                        onValueChange = { sheetTabInput = it },
                        label = { Text("Nom de l'onglet (ex: Feuille 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveSpreadsheetConfig(spreadsheetInput, sheetTabInput) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Enregistrer l'ID")
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveSpreadsheetConfig(spreadsheetInput, sheetTabInput)
                                onVerifyPermissions()
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vérifier R/W")
                        }

                        Button(
                            onClick = {
                                onSaveSpreadsheetConfig(spreadsheetInput, sheetTabInput)
                                onSyncSheetNow()
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Synchro")
                        }
                    }

                    permissionStatusMessage?.let { status ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 3. OCR / MeterClassifier Engine Switcher (Section 5 of Prompt.md)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Moteur de Classification Optique (MeterClassifier)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ClassifierEngineMode.entries.forEach { mode ->
                        FilterChip(
                            selected = (settings.classifierMode == mode),
                            onClick = { onSelectClassifierMode(mode) },
                            label = { Text(mode.frenchLabel) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = settings.classifierMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Meter Replacement Offsets (Normalized = Raw + Offset)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offsets de Remplacement des Compteurs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Valeur Normalisée = Valeur Brute + Offset cumulé",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(onClick = onResetOffsetsFromCsv) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Analyser CSV")
                        }
                    }

                    HorizontalDivider()

                    MeterType.entries.forEach { meter ->
                        val currentOffset = settings.meterOffsets[meter] ?: meter.defaultOffset
                        var offsetText by remember(meter, currentOffset) {
                            mutableStateOf(currentOffset.roundToLong().toString())
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = "${meter.displayLabel} (${meter.keyId})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = meter.frenchName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedTextField(
                                value = offsetText,
                                onValueChange = { value ->
                                    offsetText = value
                                    value.replace(",", ".").trim().toDoubleOrNull()?.let { parsed ->
                                        onUpdateMeterOffset(meter, parsed)
                                    }
                                },
                                label = { Text("Offset") },
                                suffix = { Text(meter.unit) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
