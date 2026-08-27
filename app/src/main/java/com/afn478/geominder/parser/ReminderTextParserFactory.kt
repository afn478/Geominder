package com.afn478.geominder.parser

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
    ): ReminderTextParser = ReminderTextParser(
        keywordOverrides = keywordOverrides,
        options = options,
        language = activeLanguage,
    )

    fun fromCompleteKeywordTable(
        keywordTimes: Map<String, LocalTime>,
        options: ParserOptions = ParserOptions(),
    ): ReminderTextParser = ReminderTextParser.fromCompleteKeywordTable(
        keywordTimes = keywordTimes,
        options = options,
        language = activeLanguage,
    )
}
