package com.punit.weightdriver.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    // ORDER: rows with displayName != null first (displayName sorted), then vid, pid
    @Query("""
        SELECT * FROM device_profiles 
        ORDER BY CASE WHEN displayName IS NULL THEN 1 ELSE 0 END, displayName, vid, pid
    """)
    fun observeAll(): Flow<List<DeviceProfile>>

    @Query("""
        SELECT * FROM device_profiles 
        WHERE vid=:vid AND pid=:pid AND 
              ((serial IS NULL AND :serial IS NULL) OR serial=:serial)
        LIMIT 1
    """)
    suspend fun find(vid: Int, pid: Int, serial: String?): DeviceProfile?

    @Query("SELECT * FROM device_profiles WHERE id=:id LIMIT 1")
    suspend fun byId(id: Long): DeviceProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: DeviceProfile): Long

    @Update
    suspend fun update(profile: DeviceProfile)

    @Delete
    suspend fun delete(profile: DeviceProfile)
}
