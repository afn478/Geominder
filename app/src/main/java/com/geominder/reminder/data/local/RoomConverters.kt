package com.geominder.reminder.data.local

import androidx.room.TypeConverter
import com.geominder.reminder.domain.model.ReminderStatus
import java.time.Instant

class RoomConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun statusToName(value: ReminderStatus): String = value.name

    @TypeConverter
    fun nameToStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)
}
