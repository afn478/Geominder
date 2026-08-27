package com.afn478.geominder.parser

import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.localization.SystemLanguageProvider
import java.time.LocalTime

/** Creates parsers using the first supported language in the system's ordered locale list. */
class ReminderTextParserFactory(
    private val languageProvider: SystemLanguageProvider,
) {
    val activeLanguage: SupportedLanguage
        get() = languageProvider.activeLanguage()

    fun create(
        keywordOverrides: Map<String, LocalTime> = emptyMap(),
        options: ParserOptions = ParserOptions(),
        keywordLocationOverrides: Map<String, PresetLocation> = emptyMap(),
    ): ReminderTextParser = ReminderTextParser(
        keywordOverrides = keywordOverrides,
        options = options,
        language = activeLanguage,
        keywordLocationOverrides = keywordLocationOverrides,
    )

    fun fromCompleteKeywordTable(
        keywordTimes: Map<String, LocalTime>,
        options: ParserOptions = ParserOptions(),
        keywordLocations: Map<String, PresetLocation> = emptyMap(),
    ): ReminderTextParser = ReminderTextParser.fromCompleteKeywordTable(
        keywordTimes = keywordTimes,
        options = options,
        language = activeLanguage,
        keywordLocations = keywordLocations,
    )
}
