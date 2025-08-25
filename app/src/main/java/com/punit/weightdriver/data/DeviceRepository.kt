package com.punit.weightdriver.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DeviceRepository private constructor(private val dao: DeviceDao) {

    fun all(): Flow<List<DeviceProfile>> = dao.observeAll()

    suspend fun getOrCreateDefault(
        vid: Int, pid: Int, serial: String?, displayName: String?
    ): DeviceProfile {
        dao.find(vid, pid, serial)?.let { return it }
        val def = DeviceProfile(
            vid = vid, pid = pid, serial = serial, displayName = displayName
        )
        dao.upsert(def)
        return def
    }

    suspend fun update(profile: DeviceProfile) = dao.update(profile)

    suspend fun byId(id: Long): DeviceProfile? = dao.byId(id)

    companion object {
        @Volatile private var inst: DeviceRepository? = null
        fun getInstance(ctx: Context): DeviceRepository {
            return inst ?: synchronized(this) {
                val db = AppDatabase.getInstance(ctx.applicationContext)
                DeviceRepository(db.deviceDao()).also { inst = it }
            }
        }
    }
}
