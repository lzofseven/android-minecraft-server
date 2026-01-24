package com.lzofseven.mcserver.data.repository

import com.lzofseven.mcserver.data.local.dao.AiConstructionDao
import com.lzofseven.mcserver.data.local.entity.AiConstructionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstructionRepository @Inject constructor(
    private val dao: AiConstructionDao
) {
    suspend fun saveConstruction(construction: AiConstructionEntity) {
        dao.insert(construction)
    }

    fun getRecentConstructions(serverId: String, limit: Int = 10): Flow<List<AiConstructionEntity>> {
        return dao.getRecentByServer(serverId, limit)
    }

    suspend fun clearHistory(serverId: String) {
        dao.clearByServer(serverId)
    }
}
