package com.punit.weightdriver.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class WeightRepository private constructor(
    private val dao: WeightDao
) {

    suspend fun insert(value: String) = withContext(Dispatchers.IO) {
        dao.insert(WeightEntity(value = value, timestamp = System.currentTimeMillis()))
    }

    fun recent(limit: Int = 100): Flow<List<WeightEntity>> {
        return dao.getRecent(limit)
    }

    suspend fun pruneOlderThan(ms: Long) = withContext(Dispatchers.IO) {
        dao.deleteOlderThan(ms)
    }

    companion object {
        @Volatile private var INSTANCE: WeightRepository? = null

        fun getInstance(context: Context): WeightRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context.applicationContext)
                WeightRepository(db.weightDao()).also { INSTANCE = it }
            }
        }
    }
}
