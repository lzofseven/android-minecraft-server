package com.lzofseven.mcserver.core.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.lzofseven.mcserver.core.execution.RealServerManager
import com.lzofseven.mcserver.core.network.RconClient
import com.lzofseven.mcserver.data.local.dao.AiConstructionDao
import com.lzofseven.mcserver.util.ServerPropertiesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerStatus(
    val name: String,
    val isOp: Boolean,
    val gameMode: String = "unknown"
)

@Singleton
class AiContextManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: RealServerManager,
    private val constructionDao: AiConstructionDao,
    private val rconClient: RconClient
) {

    private suspend fun getServerTechnicalMetadata(serverId: String): String {
        return try {
            val sb = StringBuilder("DETALHES TÉCNICOS DO SERVIDOR:\n")
            
            // 1. World/Level Detection
            val executionDir = serverManager.getExecutionDirectory(serverId)
            val props = File(executionDir, "server.properties")
            val worldName = if (props.exists()) {
                val levelLine = props.readLines().find { it.startsWith("level-name=") }
                levelLine?.substringAfter("=") ?: "world"
            } else "world"
            sb.append("- Pasta do Mundo: $worldName (Datapacks em $worldName/datapacks/)\n")

            // 2. Version Detection
            val server = serverManager.getActiveServerEntity()
            if (server != null) {
                sb.append("- Versão detectada: ${server.version} (${server.type})\n") 
            }

            // 3. Plugins Detection
            val pluginsDir = File(executionDir, "plugins")
            if (pluginsDir.exists() && pluginsDir.isDirectory) {
                val plugins = pluginsDir.list()?.filter { it.endsWith(".jar") } ?: emptyList()
                if (plugins.isNotEmpty()) {
                    sb.append("- Plugins instalados: ${plugins.take(10).joinToString(", ")}\n")
                }
            }

            // 4. Modpack Detection
            val modsDir = File(executionDir, "mods")
            if (modsDir.exists() && modsDir.isDirectory) {
                 val mods = modsDir.list()?.filter { it.endsWith(".jar") } ?: emptyList()
                 if (mods.isNotEmpty()) {
                     sb.append("- Mods detectados: ${mods.take(10).joinToString(", ")}\n")
                 }
            }

            sb.append("\n")
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun getComprehensiveContext(serverId: String): String = withContext(Dispatchers.IO) {
        val contextBuilder = StringBuilder()
        
        // 0. Technical Metadata
        contextBuilder.append(getServerTechnicalMetadata(serverId))

        // 1. World Memory (Constructions)
        val recentConstructions = constructionDao.getRecentByServer(serverId, 5).first()
        if (recentConstructions.isNotEmpty()) {
            contextBuilder.append("HISTÓRICO DE CONSTRUÇÕES RECENTES:\n")
            recentConstructions.forEach {
                contextBuilder.append("- ${it.name} ${if (it.location != null) "em ${it.location}" else ""}\n")
            }
            contextBuilder.append("\n")
        }

        // 2. Fetch Active Server Info
        val server = serverManager.getActiveServerEntity() ?: return@withContext contextBuilder.toString()
        val executionDir = serverManager.getExecutionDirectory(server.id)
        val rconConfig = ServerPropertiesHelper.getRconConfig(executionDir.absolutePath)
        
        // 3. Player Status (OPs & GameModes)
        val onlinePlayers = serverManager.getPlayerListFlow(server.id).value
        if (onlinePlayers.isNotEmpty()) {
            val ops = getOps(server.id, rootPath = server.uri ?: server.path)
            
            contextBuilder.append("JOGADORES ONLINE E STATUS:\n")
            onlinePlayers.forEach { playerName ->
                val isOp = ops.contains(playerName.lowercase())
                val mode = if (rconConfig.enabled) {
                    getPlayerGameMode(playerName, rconConfig)
                } else "unknown"
                
                contextBuilder.append("- $playerName: Mode=$mode, Admin=${if(isOp) "Sim" else "Não"}\n")
            }
        } else {
            contextBuilder.append("JOGADORES ONLINE: Nenhum\n")
        }

        contextBuilder.toString()
    }

    private suspend fun getPlayerGameMode(player: String, rcon: ServerPropertiesHelper.RconConfig): String {
        return try {
            // Paper/Spigot command: /data get entity <player> playerGameType
            // Vanilla fallback: parsing /list or guessing (RCON response is key)
            val response = rconClient.sendCommand(rcon.password, "data get entity $player playerGameType")
            // Expecting: "lzofseven has the following entity data: 0" (0=Survival, 1=Creative...)
            val modeIdMatch = Regex("data: (\\d+)").find(response)
            when (modeIdMatch?.groupValues?.get(1)) {
                "0" -> "Survival"
                "1" -> "Creative"
                "2" -> "Adventure"
                "3" -> "Spectator"
                else -> "Survival" // Default safety
            }
        } catch (e: Exception) {
            "Survival"
        }
    }

    private fun getOps(serverId: String, rootPath: String): Set<String> {
        val opsNames = mutableSetOf<String>()
        try {
            if (rootPath.startsWith("content://")) {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootPath))
                val opsJson = rootDoc?.findFile("ops.json")
                if (opsJson != null) {
                    context.contentResolver.openInputStream(opsJson.uri)?.bufferedReader()?.use {
                        parseOpsJson(it.readText(), opsNames)
                    }
                }
            } else {
                val opsFile = File(rootPath, "ops.json")
                if (opsFile.exists()) {
                    parseOpsJson(opsFile.readText(), opsNames)
                }
            }
        } catch (e: Exception) {
            Log.e("AiContextManager", "Error parsing ops", e)
        }
        return opsNames
    }

    private fun parseOpsJson(json: String, result: MutableSet<String>) {
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.getString("name").lowercase())
            }
        } catch (e: Exception) {}
    }
}
