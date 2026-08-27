package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import java.time.LocalTime
import java.util.Locale

/**
 * The editable preset table used by one source-language parser.
 *
 * The public override constructor keeps the original API: overrides are merged into that
 * language's shipped defaults. A complete table is used for persisted settings so removing a
 * shipped preset remains meaningful.
 */
class KeywordTimeDictionary private constructor(
    val language: SupportedLanguage,
    table: NormalizedKeywordTable,
) {
    val entries: Map<String, LocalTime> = table.entries
    val defaultEntries: Map<String, LocalTime> = TimeLanguagePacks.defaultsFor(language)
    private val matcher = KeywordTimeMatcher(entries)

    constructor(
        overrides: Map<String, LocalTime> = emptyMap(),
        language: SupportedLanguage = SupportedLanguage.fromLocale(Locale.getDefault()),
    ) : this(
        language = language,
        table = NormalizedKeywordTable(
            buildMap {
                putAll(TimeLanguagePacks.defaultsFor(language))
                putAll(normalizeAndValidate(overrides))
            },
        ),
    )

    internal fun findMatches(source: String): Sequence<KeywordTimeMatch> = matcher.findAll(source)

    private data class NormalizedKeywordTable(
        val entries: Map<String, LocalTime>,
    )

    companion object {
        /** English remains the compatibility default for callers that do not supply a language. */
        val DEFAULTS: Map<String, LocalTime> = loadDefaults()

        /** Builds a dictionary containing exactly [entries], after key normalization. */
        fun fromCompleteTable(
            entries: Map<String, LocalTime>,
            language: SupportedLanguage = SupportedLanguage.fromLocale(Locale.getDefault()),
        ): KeywordTimeDictionary = KeywordTimeDictionary(
            language = language,
            table = NormalizedKeywordTable(normalizeAndValidate(entries)),
        )

        internal fun normalize(value: String): String = normalizeKeywordPhrase(value)

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

internal data class KeywordTimeMatch(
    val span: SourceSpan,
    val time: LocalTime,
)

internal fun normalizeKeywordPhrase(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")

/** One compiled expression replaces the old per-keyword regex compilation in every parse call. */
private class KeywordTimeMatcher(
    entries: Map<String, LocalTime>,
) {
    private val values = entries
    private val regex = entries.keys
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        .map(::keywordPattern)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|")
        ?.let { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    fun findAll(source: String): Sequence<KeywordTimeMatch> = regex
        ?.findAll(source)
        ?.mapNotNull { match ->
            values[normalizeKeywordPhrase(match.value)]?.let { time ->
                KeywordTimeMatch(
                    span = SourceSpan(match.range.first, match.range.last + 1),
                    time = time,
                )
            }
        }
        ?: emptySequence()

    private fun keywordPattern(keyword: String): String {
        val phrase = keyword.split(' ').joinToString("\\s+") { Regex.escape(it) }
        return if (keyword.requiresScriptBoundary()) {
            "(?<![\\p{L}\\p{N}])$phrase(?![\\p{L}\\p{N}])"
        } else {
            phrase
        }
    }
}

private fun String.requiresScriptBoundary(): Boolean = none { character ->
    when (Character.UnicodeScript.of(character.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true
        else -> false
    }
}
