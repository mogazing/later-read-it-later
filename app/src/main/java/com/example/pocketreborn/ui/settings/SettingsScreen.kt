package com.example.pocketreborn.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import com.example.pocketreborn.ui.onboarding.LanguageSelectorDialog
import com.example.pocketreborn.ui.onboarding.appLanguageName
import com.example.pocketreborn.ui.onboarding.supportedDeviceLanguage
import androidx.compose.ui.res.stringResource
import com.example.pocketreborn.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onChangeLanguage: (String) -> Unit
) {
    val prefs by viewModel.preferences.collectAsState()
    val testStatus by viewModel.testSpeechStatus.collectAsState()
    val context = LocalContext.current
    val installedEngines = remember(context) { viewModel.getAvailableTtsEngines(context) }
    var systemNotificationsAllowed by remember(context) {
        mutableStateOf(viewModel.areSystemNotificationsEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsAllowed = viewModel.areSystemNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showRationaleDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateMasterNotificationsEnabled(true)
        }
    }

    fun openManageVoiceData(enginePackage: String) {
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            if (enginePackage.isNotBlank()) {
                intent.setPackage(enginePackage)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Open Android Settings -> System -> Languages & Input -> Text-to-speech output to manage voices.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Enable Notifications?") },
            text = {
                Text("Notifications allow Later to display media controls during background speech reading and progress alerts while article links are saved.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        viewModel.markNotificationPermissionPrompted()
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    viewModel.markNotificationPermissionPrompted()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            appLanguageName(prefs.appLanguage.ifBlank { supportedDeviceLanguage() }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showLanguageDialog = true }) {
                        Text(stringResource(R.string.language))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dedicated Notification Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.notifications_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Master Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.enable_notifications_setting), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = stringResource(if (systemNotificationsAllowed) R.string.notifications_allowed_system else R.string.notifications_blocked_system),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (systemNotificationsAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = prefs.masterNotificationsEnabled && systemNotificationsAllowed,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                        if (status == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.updateMasterNotificationsEnabled(true)
                                        } else {
                                            showRationaleDialog = true
                                        }
                                    } else {
                                        viewModel.updateMasterNotificationsEnabled(true)
                                    }
                                } else {
                                    viewModel.updateMasterNotificationsEnabled(false)
                                }

                            }
                        )
                    }

                    if (!systemNotificationsAllowed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.openSystemNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open App Notification Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val subControlsEnabled = prefs.masterNotificationsEnabled && systemNotificationsAllowed

                    // TTS Player Notification Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TTS player notification",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (subControlsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Media controls notification in status bar during speech playback",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.ttsNotificationEnabled,
                            onCheckedChange = { viewModel.updateTtsNotificationEnabled(it) },
                            enabled = subControlsEnabled
                        )
                    }

                    // Article Saving Notification Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Article-saving notifications",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (subControlsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Show progress status while an article link is being downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.articleSavingNotificationEnabled,
                            onCheckedChange = { viewModel.updateArticleSavingNotificationEnabled(it) },
                            enabled = subControlsEnabled
                        )
                    }

                    // Article Saved Confirmation Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Article-saved confirmation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (subControlsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Show confirmation alert when an article finishes saving successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.articleSavedConfirmationEnabled,
                            onCheckedChange = { viewModel.updateArticleSavedConfirmationEnabled(it) },
                            enabled = subControlsEnabled
                        )
                    }
                }

                if (showLanguageDialog) {
                    LanguageSelectorDialog(
                        selectedLanguage = prefs.appLanguage.ifBlank { supportedDeviceLanguage() },
                        onSelect = { language ->
                            showLanguageDialog = false
                            viewModel.updateAppLanguage(language)
                            onChangeLanguage(language)
                        },
                        onDismiss = { showLanguageDialog = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preferred speech engine section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Preferred speech engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Detects installed speech synthesizers dynamically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    installedEngines.forEach { engine ->
                        val isSelected = prefs.selectedTtsEngine == engine.packageName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSelectedTtsEngine(engine.packageName) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateSelectedTtsEngine(engine.packageName) }
                            )
                            Column {
                                Text(
                                    text = engine.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (engine.description.isNotBlank()) {
                                    Text(
                                        text = engine.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons: Test Speech & Manage Voice Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (testStatus != null && testStatus?.contains("started") == true) {
                                    viewModel.stopTestSpeech()
                                } else {
                                    viewModel.testSpeech(context, prefs.selectedTtsEngine)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (testStatus != null && testStatus?.contains("started") == true) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test speech")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { openManageVoiceData(prefs.selectedTtsEngine) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage voices")
                        }
                    }

                    if (!testStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testStatus.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testStatus?.contains("failed") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Theme Setting
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf("SYSTEM" to "System Default", "LIGHT" to "Light Mode", "DARK" to "Dark Mode").forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateThemeMode(mode) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reader Default Font Size
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Default Reader Font Size (${prefs.fontSizeSp} sp)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = prefs.fontSizeSp.toFloat(),
                        onValueChange = { viewModel.updateFontSize(it.toInt()) },
                        valueRange = 12f..32f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text to Speech Speed
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Speech Speed (${prefs.defaultTtsSpeed}x)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = prefs.defaultTtsSpeed,
                        onValueChange = { viewModel.updateTtsSpeed(it) },
                        valueRange = 0.5f..2.5f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Offline Download Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Download Images on Cellular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Fetch and store article images when on mobile data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = prefs.downloadImagesOnCellular,
                        onCheckedChange = { viewModel.updateDownloadOnCellular(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Version Info
            Text(
                text = "Later App v1.0 • Offline First Reader",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
