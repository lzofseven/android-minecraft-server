package com.lzofseven.mcserver.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lzofseven.mcserver.data.model.PlayerEntry
import com.lzofseven.mcserver.data.model.WhitelistEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerManager(private val serverDir: File) {
    private val gson = Gson()
    private val opsFile = File(serverDir, "ops.json")
    private val whitelistFile = File(serverDir, "whitelist.json")

    suspend fun getOps(): List<PlayerEntry> = withContext(Dispatchers.IO) {
        if (!opsFile.exists()) return@withContext emptyList()
        val type = object : TypeToken<List<PlayerEntry>>() {}.type
        opsFile.readText().let { gson.fromJson(it, type) } ?: emptyList()
    }

    suspend fun saveOps(ops: List<PlayerEntry>) = withContext(Dispatchers.IO) {
        opsFile.writeText(gson.toJson(ops))
    }

    suspend fun getWhitelist(): List<WhitelistEntry> = withContext(Dispatchers.IO) {
        if (!whitelistFile.exists()) return@withContext emptyList()
        val type = object : TypeToken<List<WhitelistEntry>>() {}.type
        whitelistFile.readText().let { gson.fromJson(it, type) } ?: emptyList()
    }

    suspend fun saveWhitelist(list: List<WhitelistEntry>) = withContext(Dispatchers.IO) {
        whitelistFile.writeText(gson.toJson(list))
    }
}
