package com.streakguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE check_log ADD COLUMN challengeUrl TEXT")
        db.execSQL("ALTER TABLE check_log ADD COLUMN streak INTEGER")
        db.execSQL("ALTER TABLE check_log ADD COLUMN known INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [CheckLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checkLogDao(): CheckLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streakguard.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
