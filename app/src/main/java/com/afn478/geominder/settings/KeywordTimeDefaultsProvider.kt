package com.afn478.geominder.settings

import com.afn478.geominder.parser.ReminderTextParser
import java.time.LocalTime

fun interface KeywordTimeDefaultsProvider {
    fun get(): Map<String, LocalTime>
}

/** Makes reset behavior follow the parser instance supplied by application integration. */
class ParserKeywordTimeDefaultsProvider(
    private val parser: ReminderTextParser,
) : KeywordTimeDefaultsProvider {
    override fun get(): Map<String, LocalTime> = parser.keywordTimes
}
