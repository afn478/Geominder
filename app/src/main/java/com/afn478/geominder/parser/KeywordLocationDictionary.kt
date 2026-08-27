package com.afn478.geominder.parser

import com.afn478.geominder.domain.model.PresetLocation

/** The editable named-location table used by the reminder parser. */
class KeywordLocationDictionary private constructor(
    table: NormalizedLocationTable,
) {
    val entries: Map<String, PresetLocation> = table.entries
    private val matcher = KeywordLocationMatcher(entries)

    constructor(overrides: Map<String, PresetLocation> = emptyMap()) : this(
        NormalizedLocationTable(normalizeAndValidate(overrides)),
    )

    internal fun findMatches(source: String): Sequence<KeywordLocationMatch> = matcher.findAll(source)

    private data class NormalizedLocationTable(
        val entries: Map<String, PresetLocation>,
    )

    companion object {
        /** Builds a table containing exactly [entries], after key normalization. */
        fun fromCompleteTable(entries: Map<String, PresetLocation>): KeywordLocationDictionary =
            KeywordLocationDictionary(NormalizedLocationTable(normalizeAndValidate(entries)))

        private fun normalizeAndValidate(
            entries: Map<String, PresetLocation>,
        ): Map<String, PresetLocation> {
            val normalizedEntries = entries.map { (keyword, location) ->
                normalizeKeywordPhrase(keyword) to location
            }
            require(normalizedEntries.none { (keyword) -> keyword.isEmpty() }) {
                "Preset locations cannot use a blank key"
            }

            val duplicateKeys = normalizedEntries
                .groupingBy { (keyword) -> keyword }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .sorted()
            require(duplicateKeys.isEmpty()) {
                "Preset locations contain duplicate normalized keys: ${duplicateKeys.joinToString()}"
            }

            return normalizedEntries
                .sortedBy { (keyword) -> keyword }
                .toMap(linkedMapOf())
        }
    }
}

internal data class KeywordLocationMatch(
    val span: SourceSpan,
    val location: PresetLocation,
)

/** One compiled expression keeps named-location parsing cheap even with many presets. */
private class KeywordLocationMatcher(
    entries: Map<String, PresetLocation>,
) {
    private val values = entries
    private val regex = entries.keys
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        .map(::keywordPattern)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|")
        ?.let { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    fun findAll(source: String): Sequence<KeywordLocationMatch> = regex
        ?.findAll(source)
        ?.mapNotNull { match ->
            values[normalizeKeywordPhrase(match.value)]?.let { location ->
                KeywordLocationMatch(
                    span = SourceSpan(match.range.first, match.range.last + 1),
                    location = location,
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
