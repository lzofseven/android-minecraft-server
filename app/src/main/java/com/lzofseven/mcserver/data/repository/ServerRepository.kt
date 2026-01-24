package com.lzofseven.mcserver.data.repository

import com.lzofseven.mcserver.data.local.dao.ServerDao
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    val allServers: Flow<List<MCServerEntity>> = serverDao.getAllServers()

    suspend fun getServerById(id: String): MCServerEntity? {
        return serverDao.getServerById(id)
    }

    fun getServerByIdFlow(id: String): Flow<MCServerEntity?> {
        return serverDao.getServerByIdFlow(id)
    }

    suspend fun insertServer(server: MCServerEntity) {
        serverDao.insertServer(server)
    }

    suspend fun deleteServer(server: MCServerEntity) {
        serverDao.deleteServer(server)
    }
    
    suspend fun updateRam(id: String, ram: Int) {
        serverDao.updateRam(id, ram)
    }

    suspend fun updateServer(server: MCServerEntity) {
        serverDao.insertServer(server)
    }
}
