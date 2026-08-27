package com.afn478.geominder.localization

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SupportedLanguageTest {
    @Test
    fun `system preference order chooses the first supported language`() {
        assertEquals(
            SupportedLanguage.JAPANESE,
            SupportedLanguage.firstFrom(listOf(Locale.forLanguageTag("xx"), Locale.JAPAN, Locale.US)),
        )
        assertEquals(
            SupportedLanguage.CHINESE,
            SupportedLanguage.fromLocale(Locale.forLanguageTag("zh-TW")),
        )
    }

    @Test
    fun `unsupported system languages fall back to English`() {
        assertEquals(
            SupportedLanguage.ENGLISH,
            SupportedLanguage.firstFrom(
                listOf(Locale.forLanguageTag("xx"), Locale.forLanguageTag("yy")),
            ),
        )
    }

    @Test
    fun `invalid stored language tags do not select a language`() {
        assertEquals(null, SupportedLanguage.fromLanguageTagOrNull("not-a-language"))
        assertEquals(
            SupportedLanguage.GERMAN,
            SupportedLanguage.fromLanguageTagOrNull("de-DE"),
        )
    }
}
