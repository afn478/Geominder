package com.geominder.reminder.backup

import com.geominder.reminder.domain.model.Reminder
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

interface ReminderCalendarCodec {
    fun encode(reminders: Collection<Reminder>, output: OutputStream)

    fun decode(input: InputStream, importedAt: Instant): CalendarDecodeResult
}
