package com.afn478.geominder.geofence

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface MapLinkResult {
    data class Found(val coordinates: ClipboardCoordinates, val label: String?) : MapLinkResult
    data object NotFound : MapLinkResult
    /** The text needed a network lookup (a short link or a place name) that couldn't be made. */
    data object Unreachable : MapLinkResult
}

/**
 * Turns pasted text into coordinates. Coordinates written in the text or link win; short share
 * links (such as maps.app.goo.gl, which carry no coordinates) are expanded by following their
 * redirects; a remaining place name or address is looked up with [placeSearch].
 */
class MapLinkResolver(
    private val placeSearch: PlaceSearch,
    private val redirectTarget: (String) -> String? = HttpText::redirectTarget,
) {
    /** Blocking; call it off the main thread. */
    fun resolve(text: String): MapLinkResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return MapLinkResult.NotFound
        ClipboardGeoInputParser.parse(trimmed)?.let {
            return MapLinkResult.Found(it, placeName(trimmed))
        }

        val link = URL_PATTERN.find(trimmed)?.value?.trimEnd('.', ',', ')', ']')
        var expanded = link
        if (link != null && isShortLink(link)) {
            try {
                expanded = expand(link)
            } catch (_: IOException) {
                return MapLinkResult.Unreachable
            } catch (_: IllegalArgumentException) {
                return MapLinkResult.NotFound
            }
            ClipboardGeoInputParser.parse(expanded)?.let {
                return MapLinkResult.Found(it, placeName(expanded))
            }
        }

        val query = when {
            expanded != null -> placeName(expanded)
            // Plain text such as an address; long passages are not worth a lookup.
            trimmed.length <= MAX_QUERY_LENGTH && trimmed.any(Char::isLetter) -> trimmed
            else -> null
        } ?: return MapLinkResult.NotFound

        val place = try {
            placeSearch.search(query, nearLatitude = null, nearLongitude = null).firstOrNull()
        } catch (_: IOException) {
            return MapLinkResult.Unreachable
        } catch (_: RuntimeException) {
            return MapLinkResult.NotFound
        } ?: return MapLinkResult.NotFound
        return MapLinkResult.Found(
            ClipboardCoordinates(place.latitude, place.longitude),
            label = if (expanded != null) query else null,
        )
    }

    /** Follows redirects until a hop carries coordinates, leaves Google, or the chain ends. */
    private fun expand(link: String): String {
        var current = link
        repeat(MAX_REDIRECTS) {
            if (ClipboardGeoInputParser.parse(current) != null) return current
            if (!isGoogleHost(hostOf(current))) return current
            current = redirectTarget(current) ?: return current
        }
        return current
    }

    private fun isShortLink(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        return host == "maps.app.goo.gl" ||
            (host == "goo.gl" && path.startsWith("/maps")) ||
            (host == "g.co" && path.startsWith("/kgs"))
    }

    private fun isGoogleHost(host: String?): Boolean {
        val value = host?.lowercase() ?: return false
        return value == "goo.gl" || value.endsWith(".goo.gl") || value == "g.co" ||
            GOOGLE_HOST.matches(value)
    }

    private fun hostOf(link: String): String? = runCatching { URI(link).host }.getOrNull()

    /** A place name carried by a map link: Google's /place/<name>/ or a textual q= / query=. */
    internal fun placeName(link: String): String? {
        // Decoding first also reaches a link wrapped in a redirect page's continue= parameter.
        val decodedLink = decode(link)
        val name = PLACE_PATH.find(decodedLink)?.groupValues?.get(1)
            ?: QUERY_PARAMETER.find(decodedLink)?.groupValues?.get(1)
            ?: return null
        // A wrapped link was encoded twice, so its name still carries "+" for spaces.
        return decode(name).trim().takeIf { it.any(Char::isLetter) && ClipboardGeoInputParser.parse(it) == null }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_QUERY_LENGTH = 200
        val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        val GOOGLE_HOST = Regex("(?:[a-z0-9-]+\\.)*google\\.[a-z.]{2,6}")
        val PLACE_PATH = Regex("/maps/(?:place|search)/([^/?#@]+)")
        val QUERY_PARAMETER = Regex("[?&](?:q|query)=([^&#]+)")
    }
}
