package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.GlobalBlacklist
import com.example.data.model.ScanReport
import com.example.data.model.TrustedApp

@Database(
    entities = [ScanReport::class, TrustedApp::class, GlobalBlacklist::class],
    version = 4,
    exportSchema = false
)
abstract class ShieldGuardDatabase : RoomDatabase() {

    abstract fun shieldGuardDao(): ShieldGuardDao

    companion object {
        @Volatile
        private var INSTANCE: ShieldGuardDatabase? = null

        fun getDatabase(context: Context): ShieldGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShieldGuardDatabase::class.java,
                    "shield_guard_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
