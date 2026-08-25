package com.geominder.reminder.parser

import java.time.LocalTime
import java.util.Locale

/**
 * Preset natural-language times.
 *
 * The public constructor keeps the original override behavior: supplied values are merged into
 * [DEFAULTS]. Use [fromCompleteTable] when a map represents the entire persisted table, including
 * removals of shipped defaults.
 */
class KeywordTimeDictionary private constructor(
    table: NormalizedKeywordTable,
) {
    val entries: Map<String, LocalTime> = table.entries

    constructor(overrides: Map<String, LocalTime> = emptyMap()) : this(
        table = NormalizedKeywordTable(
            buildMap {
                putAll(DEFAULTS)
                putAll(normalizeAndValidate(overrides))
            },
        ),
    )

    private data class NormalizedKeywordTable(
        val entries: Map<String, LocalTime>,
    )

    companion object {
        val DEFAULTS: Map<String, LocalTime> = loadDefaults()

        /** Builds a dictionary containing exactly [entries], after key normalization. */
        fun fromCompleteTable(entries: Map<String, LocalTime>): KeywordTimeDictionary =
            KeywordTimeDictionary(NormalizedKeywordTable(normalizeAndValidate(entries)))

        internal fun normalize(value: String): String = value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

        private fun normalizeAndValidate(entries: Map<String, LocalTime>): Map<String, LocalTime> {
            val normalizedEntries = entries.map { (keyword, time) ->
                normalize(keyword) to time
            }
            require(normalizedEntries.none { (keyword) -> keyword.isEmpty() }) {
                "Keyword times cannot use a blank key"
            }

            val duplicateKeys = normalizedEntries
                .groupingBy { (keyword) -> keyword }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .sorted()
            require(duplicateKeys.isEmpty()) {
                "Keyword times contain duplicate normalized keys: ${duplicateKeys.joinToString()}"
            }

            return normalizedEntries
                .sortedBy { (keyword) -> keyword }
                .toMap(linkedMapOf())
        }

        private fun loadDefaults(): Map<String, LocalTime> {
            val stream = KeywordTimeDictionary::class.java.classLoader
                ?.getResourceAsStream("reminder_keyword_times.json")
                ?: KeywordTimeDictionary::class.java.getResourceAsStream("/reminder_keyword_times.json")
                ?: error("Missing reminder_keyword_times.json resource")
            val json = stream.bufferedReader().use { it.readText() }
            val values = Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"(\\d{2}):(\\d{2})\\\"")
                .findAll(json)
                .associate { match ->
                    match.groupValues[1] to LocalTime.of(
                        match.groupValues[2].toInt(), match.groupValues[3].toInt(),
                    )
                }
            require(values.isNotEmpty()) { "reminder_keyword_times.json contains no keyword times" }
            return normalizeAndValidate(values)
        }
    }
}
