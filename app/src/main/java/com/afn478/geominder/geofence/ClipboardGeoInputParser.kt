package com.afn478.geominder.geofence

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

data class ClipboardCoordinates(
    val latitude: Double,
    val longitude: Double,
)

/** Parses coordinates copied as text or embedded in common map links. */
object ClipboardGeoInputParser {
    fun parse(clipboardText: String): ClipboardCoordinates? {
        val text = decodeUrlComponents(clipboardText.trim())
        if (text.isBlank()) return null

        parameterNames.forEach { parameterName ->
            parameterCoordinates(parameterName).find(text)?.toCoordinates()?.let { return it }
        }

        return geoCoordinates.find(text)?.toCoordinates()
            ?: labeledCoordinates.find(text)?.toCoordinates()
            ?: reversedLabeledCoordinates.find(text)?.let { match ->
                coordinates(match.groupValues[2], match.groupValues[1])
            }
            ?: mapFragmentCoordinates.find(text)?.let { match ->
                coordinates(match.groupValues[1], match.groupValues[2])
            }
            ?: atCoordinates.find(text)?.toCoordinates()
            ?: dmsCoordinates.find(text)?.toDmsCoordinates()
            ?: plainCoordinates.matchEntire(text)?.toPlainCoordinates()
    }

    private fun decodeUrlComponents(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrDefault(value)

    private fun parameterCoordinates(name: String) = Regex(
        "(?i)(?:[?&#]|\\b)$name=(?:loc:)?\\s*($number)\\s*[,; ]\\s*($number)",
    )

    private fun MatchResult.toCoordinates(): ClipboardCoordinates? =
        coordinates(groupValues[1], groupValues[2])

    private fun MatchResult.toPlainCoordinates(): ClipboardCoordinates? {
        val latitude = signedDecimal(groupValues[1], groupValues[2])
        val longitude = signedDecimal(groupValues[3], groupValues[4])
        return coordinates(latitude, longitude)
    }

    private fun signedDecimal(value: String, hemisphere: String): Double? {
        val parsed = value.toDoubleOrNull() ?: return null
        return when {
            hemisphere.equals("S", true) || hemisphere.equals("W", true) -> -abs(parsed)
            hemisphere.isNotEmpty() -> abs(parsed)
            else -> parsed
        }
    }

    private fun MatchResult.toDmsCoordinates(): ClipboardCoordinates? {
        val latitude = degreesMinutesSeconds(
            degreesText = groupValues[1],
            minutesText = groupValues[2],
            secondsText = groupValues[3],
            hemisphere = groupValues[4],
        )
        val longitude = degreesMinutesSeconds(
            degreesText = groupValues[5],
            minutesText = groupValues[6],
            secondsText = groupValues[7],
            hemisphere = groupValues[8],
        )
        return coordinates(latitude, longitude)
    }

    private fun degreesMinutesSeconds(
        degreesText: String,
        minutesText: String,
        secondsText: String,
        hemisphere: String,
    ): Double? {
        val degrees = degreesText.toDoubleOrNull() ?: return null
        val minutes = minutesText.toDoubleOrNull() ?: return null
        val seconds = secondsText.toDoubleOrNull() ?: 0.0
        if (minutes !in 0.0..<60.0 || seconds !in 0.0..<60.0) return null
        val magnitude = abs(degrees) + minutes / 60.0 + seconds / 3_600.0
        return if (hemisphere.equals("S", true) || hemisphere.equals("W", true)) {
            -magnitude
        } else {
            magnitude
        }
    }

    private fun coordinates(latitudeText: String, longitudeText: String): ClipboardCoordinates? =
        coordinates(latitudeText.toDoubleOrNull(), longitudeText.toDoubleOrNull())

    private fun coordinates(latitude: Double?, longitude: Double?): ClipboardCoordinates? {
        if (latitude == null || longitude == null) return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return ClipboardCoordinates(latitude, longitude)
    }

    private const val number = "[+-]?(?:\\d{1,3}(?:\\.\\d+)?|\\.\\d+)"
    private val parameterNames = listOf(
        "pin", "q", "query", "ll", "destination", "daddr", "finish", "start", "saddr",
    )
    private val geoCoordinates = Regex("(?i)\\bgeo:\\s*($number)\\s*,\\s*($number)")
    private val labeledCoordinates = Regex(
        "(?i)\\b(?:lat|latitude)\\s*[:=]?\\s*($number).*?" +
            "(?:lon|lng|longitude)\\s*[:=]?\\s*($number)",
    )
    private val reversedLabeledCoordinates = Regex(
        "(?i)\\b(?:lon|lng|longitude)\\s*[:=]?\\s*($number).*?" +
            "(?:lat|latitude)\\s*[:=]?\\s*($number)",
    )
    private val mapFragmentCoordinates = Regex(
        "(?i)#(?:map=)?\\d+(?:\\.\\d+)?/($number)/($number)",
    )
    private val atCoordinates = Regex("@($number),($number)")
    private val plainCoordinates = Regex(
        "(?i)\\s*($number)\\s*°?\\s*([NS]?)\\s*(?:[,;]|\\s)\\s*" +
            "($number)\\s*°?\\s*([EW]?)\\s*",
    )
    private val dmsCoordinates = Regex(
        "(?i)(\\d{1,2})[°\\s]+(\\d{1,2}(?:\\.\\d+)?)['′]?\\s*" +
            "(?:(\\d{1,2}(?:\\.\\d+)?)[\"″]?\\s*)?([NS])[,;\\s]+" +
            "(\\d{1,3})[°\\s]+(\\d{1,2}(?:\\.\\d+)?)['′]?\\s*" +
            "(?:(\\d{1,2}(?:\\.\\d+)?)[\"″]?\\s*)?([EW])",
    )
}
