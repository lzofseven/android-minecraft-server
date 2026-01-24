package com.lzofseven.mcserver.core.ai

import android.util.Log
import com.lzofseven.mcserver.core.execution.RealServerManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCommandExecutor @Inject constructor(
    private val serverManager: RealServerManager,
    private val rconClient: com.lzofseven.mcserver.core.network.RconClient
) {

    suspend fun executeAction(action: AiAction, worldPath: String, serverId: String?, rconConfig: com.lzofseven.mcserver.util.ServerPropertiesHelper.RconConfig) {
        // Configure injected client for this specific run (if needed, but usually 127.0.0.1:25575 is correct)
        // RconClient should probably have a 'connect' or 'config' method if host/port vary.
        // For now, it defaults to localhost:25575.
        
        when (action) {
            is AiAction.ExecuteCommands -> {
                action.commands.forEach { rawCmd ->
                    // RCON standard suggests no slash. Some servers crash or disconnect if '/' is present.
                    val cmd = rawCmd.removePrefix("/")
                    
                    val response = rconClient.sendCommand(rconConfig.password, cmd)
                    // Only log if response is meaningful (not empty/success) to avoid visual noise
                    if (response.isNotBlank() && response != "Command sent (No output)") {
                         Log.d("AiExecutor", "RCON Output: $response")
                    }
                }
            }
            is AiAction.CreateFunction -> {
                createAndRunFunction(action.name, action.content, worldPath, serverId, rconConfig, rconClient)
            }
        }
    }

    private suspend fun createAndRunFunction(
        name: String, 
        content: String, 
        worldPath: String, 
        serverId: String?, 
        rconConfig: com.lzofseven.mcserver.util.ServerPropertiesHelper.RconConfig,
        rconClient: com.lzofseven.mcserver.core.network.RconClient
    ) {
        // 1. Setup Datapack Directory (Source/Persistence)
        // TODO: Read level-name from server.properties for robustness.
        val worldFolder = if (File(worldPath, "world").exists()) {
            File(worldPath, "world")
        } else if (File(worldPath, "level.dat").exists()) {
            File(worldPath)
        } else {
            Log.e("AiExecutor", "Could not find world folder in $worldPath")
            throw Exception("Pasta do mundo não encontrada em: $worldPath")
        }

        // We write to SOURCE first (Persistence)
        writeFunctionToFolder(worldFolder, name, content)
        
        // 2. If valid serverId, ALSO write to EXECUTION directory (Live Server)
        if (serverId != null) {
            try {
                val executionDir = serverManager.getExecutionDirectory(serverId)
                if (executionDir.exists()) {
                     // The execution dir mimics the server root. So we look for "world" or "level.dat"
                     // Assuming standard "world" folder in execution dir
                     val execWorldFolder = File(executionDir, "world")
                     // Or check level-name... for now assume world
                     if (execWorldFolder.exists()) {
                         Log.d("AiExecutor", "Writing to Live ExecutionDir: ${execWorldFolder.absolutePath}")
                         writeFunctionToFolder(execWorldFolder, name, content)
                     }
                }
            } catch (e: Exception) {
                Log.w("AiExecutor", "Failed to write to execution dir", e)
                // Don't fail the whole operation if just live sync fails, but maybe RCON will fail...
            }
        }

        // 4. Reload and Execute via RCON
        try {
            rconClient.sendCommand(rconConfig.password, "reload")
            // Give a tiny beat for reload to process?
            kotlinx.coroutines.delay(500)
            rconClient.sendCommand(rconConfig.password, "function gemini:$name")
        } catch (e: Exception) {
             throw Exception("Falha ao executar RCON: ${e.message}")
        }
    }

    private fun writeFunctionToFolder(worldFolder: File, name: String, content: String) {
        // Target: <world>/datapacks/gemini_bot/data/gemini/functions/
        val datapackRoot = File(worldFolder, "datapacks/gemini_bot")
        val functionsDir = File(datapackRoot, "data/gemini/functions")
        
        if (!functionsDir.exists()) {
            functionsDir.mkdirs()
        }

        // Create pack.mcmeta if not exists
        val mcmetaFile = File(datapackRoot, "pack.mcmeta")
        if (!mcmetaFile.exists()) {
            mcmetaFile.writeText("""
                {
                    "pack": {
                        "pack_format": 6,
                        "description": "Datapack for Gemini Bot generated functions"
                    }
                }
            """.trimIndent())
        }

        // Write the function file
        // SECURITY: Sanitize name to prevent directory traversal and ensure .mcfunction extension
        val safeName = name.replace(Regex("[^a-zA-Z0-9_]"), "")
        if (safeName.isBlank()) {
             throw Exception("Nome da função inválido: $name")
        }
        
        val functionFile = File(functionsDir, "$safeName.mcfunction")
        
        // Double check we are writing to the correct directory
        if (!functionFile.canonicalPath.startsWith(functionsDir.canonicalPath)) {
            throw Exception("Tentativa de acesso ilegal detectada: $name")
        }

        try {
            functionFile.writeText(content)
            Log.d("AiExecutor", "Created mcfunction: ${functionFile.absolutePath}")
        } catch (e: Exception) {
            throw Exception("Falha ao escrever arquivo da função: ${e.message}")
        }
    }
}
