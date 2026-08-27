package com.afn478.geominder.parser

import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.localization.SystemLanguageProvider
import java.time.LocalTime

/** Creates parsers using a requested language or the first supported system language by default. */
class ReminderTextParserFactory(
    private val languageProvider: SystemLanguageProvider,
) {
    val activeLanguage: SupportedLanguage
        get() = languageProvider.activeLanguage()

    fun create(
        keywordOverrides: Map<String, LocalTime> = emptyMap(),
        options: ParserOptions = ParserOptions(),
        language: SupportedLanguage = activeLanguage,
        keywordLocationOverrides: Map<String, PresetLocation> = emptyMap(),
    ): ReminderTextParser = ReminderTextParser(
        keywordOverrides = keywordOverrides,
        options = options,
        language = language,
        keywordLocationOverrides = keywordLocationOverrides,
    )

    fun fromCompleteKeywordTable(
        keywordTimes: Map<String, LocalTime>,
        options: ParserOptions = ParserOptions(),
        language: SupportedLanguage = activeLanguage,
        keywordLocations: Map<String, PresetLocation> = emptyMap(),
    ): ReminderTextParser = ReminderTextParser.fromCompleteKeywordTable(
        keywordTimes = keywordTimes,
        options = options,
        language = language,
        keywordLocations = keywordLocations,
    )
}
