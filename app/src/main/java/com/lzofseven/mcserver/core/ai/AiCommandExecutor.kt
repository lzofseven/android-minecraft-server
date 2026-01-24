package com.lzofseven.mcserver.core.ai

import android.util.Log
import com.lzofseven.mcserver.core.execution.RealServerManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.documentfile.provider.DocumentFile
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AiCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
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
        if (worldPath.startsWith("content://")) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(worldPath))
            val worldDoc = findWorldFolderDoc(rootDoc)
            if (worldDoc != null) {
                writeFunctionToSaf(worldDoc, name, content)
            }
        } else {
            val worldFolder = if (File(worldPath, "world").exists()) {
                File(worldPath, "world")
            } else if (File(worldPath, "level.dat").exists()) {
                File(worldPath)
            } else {
                File(worldPath, "world") // Fallback
            }
            writeFunctionToFolder(worldFolder, name, content)
        }
        
        // 2. If valid serverId, ALSO write to EXECUTION directory (Live Server)
        if (serverId != null) {
            try {
                val executionDir = serverManager.getExecutionDirectory(serverId)
                // Execution dir is ALWAYS internal storage, so we use standard File API
                val execWorldFolder = File(executionDir, "world")
                if (execWorldFolder.exists()) {
                    writeFunctionToFolder(execWorldFolder, name, content)
                }
            } catch (e: Exception) {
                Log.w("AiExecutor", "Failed to write to execution dir", e)
            }
        }

        // 4. Reload and Execute via RCON
        try {
            rconClient.sendCommand(rconConfig.password, "reload")
            kotlinx.coroutines.delay(500)
            rconClient.sendCommand(rconConfig.password, "function gemini:$name")
        } catch (e: Exception) {
             throw Exception("Falha ao executar RCON: ${e.message}")
        }
    }

    private fun findWorldFolderDoc(rootDoc: DocumentFile?): DocumentFile? {
        if (rootDoc == null) return null
        if (rootDoc.findFile("level.dat") != null) return rootDoc
        val worldDir = rootDoc.findFile("world")
        if (worldDir != null && worldDir.isDirectory) return worldDir
        return null
    }

    private fun writeFunctionToSaf(worldDoc: DocumentFile, name: String, content: String) {
        val datapackRoot = worldDoc.findFile("datapacks")?.findFile("gemini_bot") 
            ?: worldDoc.findFile("datapacks")?.createDirectory("gemini_bot")
            ?: worldDoc.createDirectory("datapacks")?.createDirectory("gemini_bot")

        val functionsDir = datapackRoot?.findFile("data")?.findFile("gemini")?.findFile("functions")
            ?: datapackRoot?.findFile("data")?.findFile("gemini")?.createDirectory("functions")
            ?: datapackRoot?.findFile("data")?.createDirectory("gemini")?.createDirectory("functions")
            ?: datapackRoot?.createDirectory("data")?.createDirectory("gemini")?.createDirectory("functions")

        val packMcmeta = datapackRoot?.findFile("pack.mcmeta") ?: datapackRoot?.createFile("application/json", "pack.mcmeta")
        packMcmeta?.let { doc ->
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                out.write("""{"pack":{"pack_format":6,"description":"Gemini Bot"}}""".toByteArray())
            }
        }

        val safeName = name.replace(Regex("[^a-zA-Z0-9_]"), "")
        val functionDoc = functionsDir?.findFile("$safeName.mcfunction") 
            ?: functionsDir?.createFile("text/plain", "$safeName.mcfunction")

        functionDoc?.let { doc ->
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                out.write(content.toByteArray())
            }
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
