package com.lzofseven.mcserver.data.local.dao

import androidx.room.*
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<MCServerEntity>>

    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    suspend fun getAllServersList(): List<MCServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: String): MCServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    fun getServerByIdFlow(id: String): Flow<MCServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: MCServerEntity)

    @Delete
    suspend fun deleteServer(server: MCServerEntity)
    
    @Query("UPDATE servers SET ramAllocationMB = :ram WHERE id = :id")
    suspend fun updateRam(id: String, ram: Int)
}
