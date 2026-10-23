package com.afn478.geominder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afn478.geominder.data.local.entity.ChecklistItemEntity
import com.afn478.geominder.data.local.entity.GeoTriggerEntity
import com.afn478.geominder.data.local.entity.ReminderEntity
import com.afn478.geominder.data.local.entity.TimeTriggerEntity

@Database(
    entities = [
        ReminderEntity::class,
        TimeTriggerEntity::class,
        GeoTriggerEntity::class,
        ChecklistItemEntity::class,
    ],
    version = 4,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (" +
                        "`id` TEXT NOT NULL, `reminder_id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`reminder_id`) REFERENCES `reminders`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_reminder_id` " +
                        "ON `checklist_items` (`reminder_id`)",
                )
            }
        }
    }
}
