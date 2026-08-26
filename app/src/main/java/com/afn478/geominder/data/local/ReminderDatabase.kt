package com.afn478.geominder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afn478.geominder.data.local.entity.GeoTriggerEntity
import com.afn478.geominder.data.local.entity.ReminderEntity
import com.afn478.geominder.data.local.entity.TimeTriggerEntity

@Database(
    entities = [ReminderEntity::class, TimeTriggerEntity::class, GeoTriggerEntity::class],
    version = 3,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN tag TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN deleted_at INTEGER")
            }
        }
    }
}
