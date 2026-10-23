package com.afn478.geominder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URLEncoder

class MapLinkResolverTest {
    @Test
    fun `coordinates in the text need no network`() {
        val resolver = MapLinkResolver(
            placeSearch = { _, _, _ -> error("no search") },
            redirectTarget = { error("no redirect") },
        )

        val result = resolver.resolve("40.7128, -74.0060") as MapLinkResult.Found

        assertEquals(40.7128, result.coordinates.latitude, 0.0)
        assertEquals(-74.0060, result.coordinates.longitude, 0.0)
    }

    @Test
    fun `follows short link redirects until a hop carries coordinates`() {
        val hops = mutableListOf<String>()
        val target = "https://www.google.com/maps/place/Penn+Station/data=!3d40.7506!4d-73.9935"
        val resolver = MapLinkResolver(
            placeSearch = { _, _, _ -> error("no search") },
            redirectTarget = { url ->
                hops += url
                when (url) {
                    "https://maps.app.goo.gl/abc123?g_st=ic" ->
                        "https://consent.google.com/ml?continue=" + URLEncoder.encode(target, "UTF-8")
                    else -> null
                }
            },
        )

        val result = resolver.resolve("https://maps.app.goo.gl/abc123?g_st=ic") as MapLinkResult.Found

        assertEquals(listOf("https://maps.app.goo.gl/abc123?g_st=ic"), hops)
        assertEquals(40.7506, result.coordinates.latitude, 0.0)
        assertEquals(-73.9935, result.coordinates.longitude, 0.0)
        assertEquals("Penn Station", result.label)
    }

    @Test
    fun `a link without coordinates is looked up by its place name`() {
        var query: String? = null
        val resolver = MapLinkResolver(
            placeSearch = { text, _, _ ->
                query = text
                listOf(PlaceResult("Penn Station", null, 40.7506, -73.9935))
            },
            redirectTarget = { "https://maps.google.com/?q=Penn+Station,+New+York&ftid=0x0:0x1" },
        )

        val result = resolver.resolve("https://maps.app.goo.gl/xyz") as MapLinkResult.Found

        assertEquals("Penn Station, New York", query)
        assertEquals(40.7506, result.coordinates.latitude, 0.0)
        assertEquals("Penn Station, New York", result.label)
    }

    @Test
    fun `a pasted address is searched as text`() {
        val resolver = MapLinkResolver(
            placeSearch = { _, _, _ -> listOf(PlaceResult("Main St 1", null, 40.0, -74.0)) },
            redirectTarget = { error("no redirect") },
        )

        assertTrue(resolver.resolve("Main St 1, Springfield") is MapLinkResult.Found)
    }

    @Test
    fun `network failures are reported as unreachable`() {
        val offlineRedirect = MapLinkResolver(
            placeSearch = { _, _, _ -> emptyList() },
            redirectTarget = { throw IOException("offline") },
        )
        val offlineSearch = MapLinkResolver(
            placeSearch = { _, _, _ -> throw IOException("offline") },
            redirectTarget = { null },
        )

        assertEquals(MapLinkResult.Unreachable, offlineRedirect.resolve("https://maps.app.goo.gl/abc"))
        assertEquals(MapLinkResult.Unreachable, offlineSearch.resolve("Main St 1"))
    }

    @Test
    fun `other links are never fetched and nothing is found`() {
        val resolver = MapLinkResolver(
            placeSearch = { _, _, _ -> emptyList() },
            redirectTarget = { error("must not follow arbitrary links") },
        )

        assertEquals(MapLinkResult.NotFound, resolver.resolve("https://example.com/some/page"))
    }
}
