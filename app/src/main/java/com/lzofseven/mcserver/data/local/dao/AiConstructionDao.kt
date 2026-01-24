package com.lzofseven.mcserver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lzofseven.mcserver.data.local.entity.AiConstructionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiConstructionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(construction: AiConstructionEntity)

    @Query("SELECT * FROM ai_constructions WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentByServer(serverId: String, limit: Int = 10): Flow<List<AiConstructionEntity>>

    @Query("DELETE FROM ai_constructions WHERE serverId = :serverId")
    suspend fun clearByServer(serverId: String)
}
