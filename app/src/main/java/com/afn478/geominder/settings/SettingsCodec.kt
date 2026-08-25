package com.afn478.geominder.settings

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Versioned, deterministic serialization for the complete editable keyword table. */
object SettingsCodec {
    private const val VERSION = "v1"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val charset = StandardCharsets.UTF_8.name()

    fun encodeKeywordTimes(entries: Map<String, LocalTime>): String = buildString {
        append(VERSION)
        entries.toSortedMap().forEach { (keyword, time) ->
            append('\n')
            append(URLEncoder.encode(keyword, charset))
            append('=')
            append(time.format(timeFormatter))
        }
    }

    fun decodeKeywordTimes(value: String): Map<String, LocalTime>? {
        val lines = value.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return null

        val result = linkedMapOf<String, LocalTime>()
        for (line in lines.drop(1)) {
            val separator = line.lastIndexOf('=')
            if (separator <= 0 || separator == line.lastIndex) return null
            val keyword = try {
                URLDecoder.decode(line.substring(0, separator), charset)
            } catch (_: IllegalArgumentException) {
                return null
            }
            val normalized = (SettingsValidation.keyword(keyword) as? ValidationResult.Valid)?.value
                ?: return null
            val time = (SettingsValidation.time(line.substring(separator + 1)) as?
                ValidationResult.Valid)?.value ?: return null
            result[normalized] = time
        }
        return result
    }

    fun decodeRadius(value: String?): Double = value
        ?.let(SettingsValidation::radius)
        ?.let { it as? ValidationResult.Valid }
        ?.value
        ?: SettingsValidation.DEFAULT_RADIUS_METERS
}
