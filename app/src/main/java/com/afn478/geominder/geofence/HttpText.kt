package com.afn478.geominder.geofence

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP for the open geocoder and for expanding shared map short links. */
internal object HttpText {
    private const val USER_AGENT = "Geominder (Android reminder app)"
    private const val TIMEOUT_MILLIS = 10_000
    private const val MAX_BODY_CHARS = 512 * 1024

    fun get(url: String, acceptLanguage: String? = null): String {
        val connection = open(url)
        try {
            acceptLanguage?.let { connection.setRequestProperty("Accept-Language", it) }
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_BODY_CHARS)
                var length = 0
                while (length < buffer.size) {
                    val read = reader.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                String(buffer, 0, length)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** The target of a single redirect, or null when [url] doesn't redirect. */
    fun redirectTarget(url: String): String? {
        val connection = open(url)
        try {
            connection.instanceFollowRedirects = false
            val code = connection.responseCode
            if (code !in 300..399) return null
            val location = connection.getHeaderField("Location") ?: return null
            return URL(URL(url), location).toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val parsed = URL(url)
        require(parsed.protocol == "https" || parsed.protocol == "http") { "Unsupported URL" }
        return (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }
}
