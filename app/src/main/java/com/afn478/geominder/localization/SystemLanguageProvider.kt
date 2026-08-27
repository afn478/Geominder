package com.afn478.geominder.localization

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.util.Locale

interface SystemLanguageProvider {
    fun locales(): List<Locale>

    fun activeLanguage(): SupportedLanguage = SupportedLanguage.firstFrom(locales())
}

/** Reads the ordered locale preferences supplied by Android; no in-app language setting is used. */
class AndroidSystemLanguageProvider(
    context: Context,
) : SystemLanguageProvider {
    private val applicationContext = context.applicationContext

    override fun locales(): List<Locale> = ConfigurationCompat
        .getLocales(applicationContext.resources.configuration)
        .let { localeList ->
            (0 until localeList.size()).mapNotNull(localeList::get)
        }
        .ifEmpty { listOf(Locale.getDefault()) }
}
