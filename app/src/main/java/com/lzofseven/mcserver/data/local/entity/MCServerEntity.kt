package com.lzofseven.mcserver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "servers")
data class MCServerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String, // Caminho absoluto para a pasta do servidor
    val uri: String? = null, // SAF URI for Android 11+
    val version: String,
    val type: String, // "Paper", "Fabric", "Vanilla", "Forge"
    val ramAllocationMB: Int,
    val javaVersion: Int = 17, // New: support for multiple Java versions
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
