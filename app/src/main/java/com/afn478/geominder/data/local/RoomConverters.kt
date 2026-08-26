package com.afn478.geominder.data.local

import androidx.room.TypeConverter
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
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

    @TypeConverter
    fun tagToName(value: ReminderTag?): String? = value?.name

    @TypeConverter
    fun nameToTag(value: String?): ReminderTag? = value?.let(ReminderTag::valueOf)
}
