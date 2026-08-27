package com.afn478.geominder.settings

import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.parser.ReminderTextParser
import java.time.LocalTime
import java.util.Locale

fun interface KeywordTimeDefaultsProvider {
    fun get(): Map<String, LocalTime>

    fun language(): SupportedLanguage = SupportedLanguage.fromLocale(Locale.getDefault())
}

/** Makes reset behavior follow the parser instance supplied by application integration. */
class ParserKeywordTimeDefaultsProvider(
    private val parser: ReminderTextParser,
) : KeywordTimeDefaultsProvider {
    override fun get(): Map<String, LocalTime> = parser.keywordTimes

    override fun language(): SupportedLanguage = parser.language
}
