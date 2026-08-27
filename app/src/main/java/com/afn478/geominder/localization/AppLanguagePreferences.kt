package com.afn478.geominder.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Reads and applies the user's optional in-app language override. */
object AppLanguagePreferences {
    const val PREFERENCES_NAME = "reminder_settings"
    const val KEY_LANGUAGE_OVERRIDE = "keyword_language_override"

    fun languageOverride(context: Context): SupportedLanguage? = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE_OVERRIDE, null)
        ?.let(SupportedLanguage::fromLanguageTagOrNull)

    fun localizedContext(context: Context): Context {
        val language = languageOverride(context) ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(language.locale)
        return context.createConfigurationContext(configuration)
    }

    fun locale(context: Context): Locale = languageOverride(context)?.locale
        ?: context.resources.configuration.locales.let { locales ->
            if (locales.isEmpty) Locale.getDefault() else locales[0]
        }
}
