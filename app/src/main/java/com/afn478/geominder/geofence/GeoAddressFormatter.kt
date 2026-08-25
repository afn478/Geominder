package com.afn478.geominder.geofence

import java.util.Locale

/** Address data independent of Android, suitable for deterministic presentation tests. */
data class GeoAddressComponents(
    val featureName: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val postalCode: String? = null,
    val postalTown: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val subAdminArea: String? = null,
    val adminArea: String? = null,
    val countryName: String? = null,
)

/** Chooses a useful full address, never reducing a rich address to a feature name. */
object GeoAddressFormatter {
    fun format(components: GeoAddressComponents, addressLines: Iterable<String?> = emptyList()): String? {
        val componentValues = buildList {
            val streetNumber = components.subThoroughfare ?: components.featureName
            add(components.thoroughfare?.let { join(it, streetNumber) })
            add(join(components.postalCode, components.postalTown ?: components.locality))
            add(components.subLocality)
            add(components.subAdminArea)
            add(components.adminArea)
            add(components.countryName)
        }.filterNotNull().flatMap(::splitAndTrim).deduplicate()

        val componentSet = (componentValues + listOfNotNull(components.featureName))
            .map(::key)
            .toSet()
        val lines = addressLines.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.deduplicate()
        val meaningfulLine = lines
            .filter { it.length > 1 && key(it) !in componentSet }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        return meaningfulLine ?: componentValues.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun join(first: String?, second: String?): String? = when {
        first.isNullOrBlank() -> second
        second.isNullOrBlank() -> first
        key(first) == key(second) -> first
        else -> "$first $second"
    }

    private fun splitAndTrim(value: String): List<String> =
        value.split(',').map(String::trim).filter(String::isNotBlank)

    private fun Iterable<String>.deduplicate(): List<String> =
        fold(mutableListOf()) { result, value ->
            if (result.none { key(it) == key(value) }) result += value
            result
        }

    private fun key(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
