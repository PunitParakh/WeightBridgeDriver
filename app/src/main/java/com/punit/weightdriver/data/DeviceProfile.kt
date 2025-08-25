package com.punit.weightdriver.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_profiles",
    indices = [Index(value = ["vid","pid","serial"], unique = true)]
)
data class DeviceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vid: Int,
    val pid: Int,
    val serial: String?,                 // nullable if device doesn’t expose one
    val displayName: String? = null,     // optional user-friendly name

    val baudRate: Int = 9600,
    val dataBits: Int = 8,               // 5..8
    val stopBits: Int = 1,               // 1 or 2
    val parity: Int = 0,                 // 0=None,1=Odd,2=Even,3=Mark,4=Space

    // Parsing config
    val lineTerminator: String = "\\r?\\n",            // regex to split frames
    val weightRegex: String = "([0-9]+(?:\\.[0-9]+)?)",// group(1) is weight

    val portIndex: Int = 0
)
