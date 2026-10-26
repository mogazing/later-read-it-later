package com.example.pocketreborn.ui.onboarding

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguage(val tag: String, val nativeName: String)

val supportedAppLanguages = listOf(
    AppLanguage("en", "English"),
    AppLanguage("ar", "العربية"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("it", "Italiano"),
    AppLanguage("fr", "Français"),
    AppLanguage("es", "Español"),
    AppLanguage("pt", "Português"),
    AppLanguage("zh-CN", "简体中文"),
    AppLanguage("ja", "日本語"),
    AppLanguage("hi", "हिन्दी")
)

fun appLanguageName(languageTag: String): String =
    supportedAppLanguages.firstOrNull { it.tag.equals(languageTag, true) }?.nativeName ?: "English"

fun supportedDeviceLanguage(locale: Locale = Locale.getDefault()): String =
    supportedAppLanguages.firstOrNull { it.tag.substringBefore('-').equals(locale.language, true) }?.tag ?: "en"

fun Context.applyAppLanguage(languageTag: String): Context {
    val supportedTag = supportedAppLanguages.firstOrNull { it.tag.equals(languageTag, true) }?.tag ?: "en"
    val locale = Locale.forLanguageTag(supportedTag)
    Locale.setDefault(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(supportedTag)
    }
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}

fun Context.withSavedAppLanguage(): Context {
    val savedTag = getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        .getString("app_language", null)
        .orEmpty()
    val tag = savedTag.ifBlank { supportedDeviceLanguage() }
    return applyAppLanguage(tag)
}

class AppLanguageContext(base: Context) : ContextWrapper(base.withSavedAppLanguage())
