package com.geominder.reminder.ui.add

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import com.geominder.reminder.parser.SourceSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeHighlightTransformationTest {
    private val source = "Buy milk at 5"

    @Test
    fun `highlights the detected source span without changing text`() {
        val transformed = transform(SourceSpan(start = 9, endExclusive = 13)).filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        assertEquals(1, transformed.text.spanStyles.size)
        assertEquals(9, transformed.text.spanStyles.single().start)
        assertEquals(13, transformed.text.spanStyles.single().end)
    }

    @Test
    fun `uses identity offset mapping`() {
        val mapping = transform(SourceSpan(9, 13)).filter(AnnotatedString(source)).offsetMapping

        listOf(0, 4, 9, 12, source.length).forEach { offset ->
            assertEquals(offset, mapping.originalToTransformed(offset))
            assertEquals(offset, mapping.transformedToOriginal(offset))
        }
        assertTrue(mapping === OffsetMapping.Identity)
    }

    @Test
    fun `invalid or stale spans safely produce no highlight`() {
        val cases = listOf<SourceSpan?>(
            null,
            SourceSpan(9, 9),
            SourceSpan(9, source.length + 1),
        )

        cases.forEach { span ->
            val result = transform(span).filter(AnnotatedString(source))
            assertEquals(source, result.text.text)
            assertTrue(result.text.spanStyles.isEmpty())
        }

        val mismatch = transform(SourceSpan(9, 13)).filter(AnnotatedString("Buy milk"))
        assertEquals("Buy milk", mismatch.text.text)
        assertTrue(mismatch.text.spanStyles.isEmpty())
    }

    private fun transform(span: SourceSpan?) = DateTimeHighlightTransformation(
        span = span,
        source = source,
        background = Color.Yellow,
        foreground = Color.Black,
    )
}
