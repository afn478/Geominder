package com.geominder.reminder.settings

import com.geominder.reminder.parser.ReminderTextParser
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
