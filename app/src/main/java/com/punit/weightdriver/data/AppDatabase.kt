package com.punit.weightdriver.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeightEntity::class,
        DeviceProfile::class
    ],
    version = 2, // bump from previous version
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weight_driver.db"
                )
                    .fallbackToDestructiveMigration() // dev-friendly while iterating
                    .build().also { INSTANCE = it }
            }
        }
    }
}
