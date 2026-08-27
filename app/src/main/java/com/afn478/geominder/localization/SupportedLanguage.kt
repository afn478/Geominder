package com.afn478.geominder.localization

import java.util.Locale

/** Languages with parser rules and translated Android resources. */
enum class SupportedLanguage(
    val languageTag: String,
    val locale: Locale,
    val isScriptBased: Boolean = false,
) {
    ENGLISH("en", Locale.ENGLISH),
    GERMAN("de", Locale.GERMAN),
    FRENCH("fr", Locale.FRENCH),
    ITALIAN("it", Locale.ITALIAN),
    SPANISH("es", Locale.forLanguageTag("es")),
    RUSSIAN("ru", Locale.forLanguageTag("ru")),
    JAPANESE("ja", Locale.JAPANESE, isScriptBased = true),
    CHINESE("zh", Locale.SIMPLIFIED_CHINESE, isScriptBased = true),
    KOREAN("ko", Locale.KOREAN, isScriptBased = true),
    ;

    companion object {
        /** Resolves a regional locale to the language pack that owns its language. */
        fun fromLocale(locale: Locale): SupportedLanguage = entries.firstOrNull { language ->
            language.languageTag.equals(locale.language, ignoreCase = true)
        } ?: ENGLISH

        fun fromLanguageTagOrNull(languageTag: String?): SupportedLanguage? = languageTag
            ?.let(Locale::forLanguageTag)
            ?.takeIf { it.language.isNotBlank() }
            ?.let { locale ->
                entries.firstOrNull { language ->
                    language.languageTag.equals(locale.language, ignoreCase = true)
                }
            }

        fun fromLanguageTag(languageTag: String?): SupportedLanguage =
            fromLanguageTagOrNull(languageTag) ?: ENGLISH

        /** Returns the first supported preference, preserving the system's ordering. */
        fun firstFrom(locales: Iterable<Locale>): SupportedLanguage = locales
            .firstOrNull { locale ->
                entries.any { language ->
                    language.languageTag.equals(locale.language, ignoreCase = true)
                }
            }
            ?.let(::fromLocale)
            ?: ENGLISH
    }
}
