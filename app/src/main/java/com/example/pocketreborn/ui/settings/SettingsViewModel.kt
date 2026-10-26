package com.example.pocketreborn.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.repository.UserPreferences
import com.example.pocketreborn.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class TtsEngineInfo(
    val packageName: String,
    val label: String,
    val description: String = ""
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val _testSpeechStatus = MutableStateFlow<String?>(null)
    val testSpeechStatus: StateFlow<String?> = _testSpeechStatus.asStateFlow()

    private var testTts: TextToSpeech? = null

    fun areSystemNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openSystemNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    fun getAvailableTtsEngines(context: Context): List<TtsEngineInfo> {
        val engines = mutableListOf(
            TtsEngineInfo("", "System Default", "Follows Android system preferred speech engine")
        )
        try {
            val pm = context.packageManager
            val ttsIntent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val resolveInfos = pm.queryIntentServices(ttsIntent, 0)

            Log.d("LaterTTS", "PackageManager discovered ${resolveInfos.size} TTS service(s)")
            resolveInfos.forEach { resolveInfo ->
                val pkgName = resolveInfo.serviceInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                Log.d("LaterTTS", "Discovered TTS Engine: '$label' ($pkgName)")
                if (engines.none { it.packageName == pkgName }) {
                    engines.add(TtsEngineInfo(pkgName, label, pkgName))
                }
            }
        } catch (e: Exception) {
            Log.e("LaterTTS", "Error querying PackageManager for TTS engines: ${e.message}")
        }
        return engines
    }

    fun testSpeech(context: Context, enginePackage: String) {
        Log.d("LaterTTS", "Test speech requested for enginePackage='${enginePackage.ifBlank { "System Default" }}'")
        _testSpeechStatus.value = "Initializing test speech engine..."

        testTts?.stop()
        testTts?.shutdown()
        testTts = null

        val listener = object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    val engine = testTts
                    if (engine != null) {
                        engine.setLanguage(Locale.US)
                        val sampleText = "This is a speech test using Later's text to speech engine."
                        val result = engine.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "test_sample")
                        if (result == TextToSpeech.SUCCESS) {
                            Log.d("LaterTTS", "Test speech speak() returned SUCCESS")
                            _testSpeechStatus.value = "Speech test started successfully!"
                        } else {
                            Log.e("LaterTTS", "Test speech speak() returned ERROR code $result")
                            _testSpeechStatus.value = "Speech test failed: speak() returned code $result."
                        }
                    }
                } else {
                    Log.e("LaterTTS", "Test speech onInit failed with status $status")
                    _testSpeechStatus.value = "Speech test failed: engine onInit status $status."
                }
            }
        }

        testTts = if (enginePackage.isNotBlank()) {
            TextToSpeech(context.applicationContext, listener, enginePackage)
        } else {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    fun stopTestSpeech() {
        testTts?.stop()
        testTts?.shutdown()
        testTts = null
        _testSpeechStatus.value = null
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
    }

    fun updateFontSize(sizeSp: Int) {
        viewModelScope.launch { preferencesRepository.updateFontSize(sizeSp) }
    }

    fun updateTtsSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.updateTtsSpeed(speed) }
    }

    fun updateSelectedTtsEngine(enginePackage: String) {
        viewModelScope.launch { preferencesRepository.updateSelectedTtsEngine(enginePackage) }
    }

    fun updateDownloadOnCellular(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateDownloadImagesOnCellular(enabled) }
    }

    fun updateMasterNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateMasterNotificationsEnabled(enabled) }
    }

    fun markNotificationPermissionPrompted() {
        viewModelScope.launch { preferencesRepository.markNotificationPermissionPrompted() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.completeOnboarding() }
    }

    fun updateAppLanguage(languageTag: String) {
        viewModelScope.launch { preferencesRepository.updateAppLanguage(languageTag) }
    }

    fun updateTtsNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTtsNotificationEnabled(enabled) }
    }

    fun updateArticleSavingNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateArticleSavingNotificationEnabled(enabled) }
    }

    fun updateArticleSavedConfirmationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateArticleSavedConfirmationEnabled(enabled) }
    }

    override fun onCleared() {
        stopTestSpeech()
        super.onCleared()
    }
}
