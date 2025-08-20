package com.punit.weightdriver.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Insert
    suspend fun insert(entity: WeightEntity): Long

    @Query("SELECT * FROM weights ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<WeightEntity>>

    @Query("DELETE FROM weights WHERE timestamp < :ms")
    suspend fun deleteOlderThan(ms: Long): Int
}
