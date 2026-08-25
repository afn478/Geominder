package com.afn478.geominder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.afn478.geominder.data.local.entity.GeoTriggerEntity
import com.afn478.geominder.data.local.entity.ReminderEntity
import com.afn478.geominder.data.local.entity.TimeTriggerEntity

@Database(
    entities = [ReminderEntity::class, TimeTriggerEntity::class, GeoTriggerEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "reminders.db"

        @Volatile
        private var instance: ReminderDatabase? = null

        fun getInstance(context: Context): ReminderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReminderDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
