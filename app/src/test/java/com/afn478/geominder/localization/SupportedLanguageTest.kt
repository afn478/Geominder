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
}
