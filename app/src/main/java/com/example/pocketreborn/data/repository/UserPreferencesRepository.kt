package com.example.pocketreborn.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserPreferences(
    val themeMode: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val fontSizeSp: Int = 18,
    val lineSpacingMultiplier: Float = 1.4f,
    val fontFamily: String = "DEFAULT", // "DEFAULT", "SERIF", "SANS_SERIF", "MONOSPACE"
    val defaultTtsSpeed: Float = 1.0f,
    val selectedTtsEngine: String = "", // Empty string = System Default
    val downloadImagesOnCellular: Boolean = true,
    val maxImageDownloadCount: Int = 20,
    // Notification Preferences
    val masterNotificationsEnabled: Boolean = true,
    val ttsNotificationEnabled: Boolean = true,
    val articleSavingNotificationEnabled: Boolean = true,
    val articleSavedConfirmationEnabled: Boolean = true,
    val notificationPermissionPrompted: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val appLanguage: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val LINE_SPACING_MULTIPLIER = floatPreferencesKey("line_spacing_multiplier")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val DEFAULT_TTS_SPEED = floatPreferencesKey("default_tts_speed")
        val SELECTED_TTS_ENGINE = stringPreferencesKey("selected_tts_engine")
        val DOWNLOAD_IMAGES_ON_CELLULAR = booleanPreferencesKey("download_images_on_cellular")
        val MAX_IMAGE_DOWNLOAD_COUNT = intPreferencesKey("max_image_download_count")

        val MASTER_NOTIFICATIONS_ENABLED = booleanPreferencesKey("master_notifications_enabled")
        val TTS_NOTIFICATION_ENABLED = booleanPreferencesKey("tts_notification_enabled")
        val ARTICLE_SAVING_NOTIFICATION_ENABLED = booleanPreferencesKey("article_saving_notification_enabled")
        val ARTICLE_SAVED_CONFIRMATION_ENABLED = booleanPreferencesKey("article_saved_confirmation_enabled")
        val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            fontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: 18,
            lineSpacingMultiplier = prefs[Keys.LINE_SPACING_MULTIPLIER] ?: 1.4f,
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "DEFAULT",
            defaultTtsSpeed = prefs[Keys.DEFAULT_TTS_SPEED] ?: 1.0f,
            selectedTtsEngine = prefs[Keys.SELECTED_TTS_ENGINE] ?: "",
            downloadImagesOnCellular = prefs[Keys.DOWNLOAD_IMAGES_ON_CELLULAR] ?: true,
            maxImageDownloadCount = prefs[Keys.MAX_IMAGE_DOWNLOAD_COUNT] ?: 20,
            masterNotificationsEnabled = prefs[Keys.MASTER_NOTIFICATIONS_ENABLED] ?: true,
            ttsNotificationEnabled = prefs[Keys.TTS_NOTIFICATION_ENABLED] ?: true,
            articleSavingNotificationEnabled = prefs[Keys.ARTICLE_SAVING_NOTIFICATION_ENABLED] ?: true,
            articleSavedConfirmationEnabled = prefs[Keys.ARTICLE_SAVED_CONFIRMATION_ENABLED] ?: true,
            notificationPermissionPrompted = prefs[Keys.NOTIFICATION_PERMISSION_PROMPTED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: ""
        )
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun updateFontSize(sizeSp: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE_SP] = sizeSp
        }
    }

    suspend fun updateLineSpacing(spacing: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LINE_SPACING_MULTIPLIER] = spacing
        }
    }

    suspend fun updateFontFamily(family: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = family
        }
    }

    suspend fun updateTtsSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TTS_SPEED] = speed
        }
    }

    suspend fun updateSelectedTtsEngine(enginePackage: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_TTS_ENGINE] = enginePackage
        }
    }

    suspend fun updateDownloadImagesOnCellular(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_IMAGES_ON_CELLULAR] = enabled
        }
    }

    suspend fun updateMaxImageDownloadCount(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_IMAGE_DOWNLOAD_COUNT] = count
        }
    }

    suspend fun updateMasterNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MASTER_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateTtsNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TTS_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun updateArticleSavingNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ARTICLE_SAVING_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun updateArticleSavedConfirmationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ARTICLE_SAVED_CONFIRMATION_ENABLED] = enabled
        }
    }

    suspend fun markNotificationPermissionPrompted() {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_PERMISSION_PROMPTED] = true
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun updateAppLanguage(languageTag: String) {
        context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language", languageTag)
            .apply()
        context.dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = languageTag }
    }
}
