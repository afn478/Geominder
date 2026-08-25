package com.afn478.geominder.backup

import com.afn478.geominder.domain.model.Reminder
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

interface ReminderCalendarCodec {
    fun encode(reminders: Collection<Reminder>, output: OutputStream)

    fun decode(input: InputStream, importedAt: Instant): CalendarDecodeResult
}
