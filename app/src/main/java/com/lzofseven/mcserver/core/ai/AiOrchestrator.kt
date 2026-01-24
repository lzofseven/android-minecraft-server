package com.lzofseven.mcserver.core.ai

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.Part
import com.google.ai.client.generativeai.type.content
import com.lzofseven.mcserver.core.execution.RealServerManager
import com.lzofseven.mcserver.core.network.RconClient
import com.lzofseven.mcserver.data.local.dao.AiConstructionDao
import com.lzofseven.mcserver.data.local.entity.AiConstructionEntity
import com.lzofseven.mcserver.util.ServerPropertiesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val role: String, // "user" or "model" or "system" (for logs)
    val content: String,
    val isAction: Boolean = false,
    val isOrchestrationLog: Boolean = false,
    val actionStatuses: Map<Int, ActionStatus> = emptyMap()
)

enum class ActionStatus {
    PENDING, EXECUTING, SUCCESS, ERROR
}

@Singleton
class AiOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: RealServerManager,
    private val rconClient: RconClient,
    private val geminiClient: GeminiClient,
    private val constructionDao: AiConstructionDao // Memory persistence
) {

    // Orchestration state to be shared with UI
    sealed class OrchestrationStep {
        data class ToolExecuting(val toolName: String, val args: Map<String, Any?>) : OrchestrationStep()
        data class LogMessage(val message: String) : OrchestrationStep()
        data class FinalResponse(val text: String) : OrchestrationStep()
    }

    // Map to hold persistent chat sessions and message history per server
    private val chatSessions = java.util.concurrent.ConcurrentHashMap<String, com.google.ai.client.generativeai.Chat>()
    private val _chatHistories = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.MutableStateFlow<List<ChatMessage>>>()

    fun getChatHistoryFlow(serverId: String): kotlinx.coroutines.flow.StateFlow<List<ChatMessage>> {
        return _chatHistories.getOrPut(serverId) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }.asStateFlow()
    }

    fun addMessageToHistory(serverId: String, message: ChatMessage) {
        val flow = _chatHistories.getOrPut(serverId) { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }
        flow.value = flow.value + message
    }

    fun updateLastMessageStatus(serverId: String, actionIndex: Int, status: ActionStatus) {
        val flow = _chatHistories[serverId] ?: return
        val currentList = flow.value.toMutableList()
        if (currentList.isNotEmpty()) {
            val lastMsg = currentList.last()
            val newStatuses = lastMsg.actionStatuses.toMutableMap()
            newStatuses[actionIndex] = status
            currentList[currentList.lastIndex] = lastMsg.copy(actionStatuses = newStatuses)
            flow.value = currentList
        }
    }

    suspend fun processUserRequest(
        text: String, 
        context: String?,
        serverId: String, 
        worldPath: String
    ): Flow<OrchestrationStep> = flow {
        
        try {
            kotlinx.coroutines.withTimeout(180_000) { // Increased to 3 min for complex multi-file tasks
                // Retrieve existing chat or start new one
                val existingChat = chatSessions[serverId]
                
                val fullMessage = if (existingChat == null && context != null) {
                     "[CONTEXTO TÉCNICO INICIAL]: $context\n\n$text"
                } else if (context != null) {
                     "$text"
                } else {
                    text
                }

                // Send message logic manually to keep Control and emit progress
                var currentChat = existingChat ?: geminiClient.startNewChat()
                chatSessions[serverId] = currentChat
                
                // We use raw sendMessage here to manually handle the function calling loop
                // this allows us to emit OrchestrationStep.ToolExecuting for UI feedback
                var apiResponse = currentChat.sendMessage(fullMessage)
                
                var iterations = 0
                val maxIterations = 15 // Increased for complex autonomous architecture
                
                // Loop for Function Calling
                while (iterations < maxIterations && apiResponse.candidates.firstOrNull()?.content?.parts?.any { it is com.google.ai.client.generativeai.type.FunctionCallPart } == true) {
                    iterations++
                    val toolResults = mutableListOf<Part>()
                    
                    val functionCalls = apiResponse.candidates.first().content.parts.filterIsInstance<com.google.ai.client.generativeai.type.FunctionCallPart>()
                    
                    for (call in functionCalls) {
                        emit(OrchestrationStep.ToolExecuting(call.name, call.args))
                        val result = executeTool(call.name, call.args, serverId, worldPath)
                        toolResults.add(com.google.ai.client.generativeai.type.FunctionResponsePart(call.name, org.json.JSONObject(mapOf("result" to result))))
                    }
                    
                    // Send back the results to continue reasoning
                    apiResponse = currentChat.sendMessage(com.google.ai.client.generativeai.type.Content(role = "user", parts = toolResults))
                }
                
                if (iterations >= maxIterations) {
                     emit(OrchestrationStep.LogMessage("Parei para evitar loop infinito. Verifique o progresso."))
                }

                val finalResult = try { apiResponse.text ?: "O robô concluiu as tarefas." } catch (e: Exception) { "Processamento finalizado." }
                emit(OrchestrationStep.FinalResponse(finalResult))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            emit(OrchestrationStep.FinalResponse("⚠️ Tempo esgotado! A operação demorou muito e foi cancelada para evitar travamentos."))
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMessage = if (e.message?.contains("deserialize", ignoreCase = true) == true || e.message?.contains("parts", ignoreCase = true) == true) {
                "⚠️ Erro de Resposta: O Arquiteto tentou gerar algo que foi bloqueado pelos filtros de segurança (ex: uso de TNT/Lava) ou o formato do modelo falhou. Tente simplificar o pedido."
            } else {
                "⚠️ Ocorreu um erro interno: ${e.message}. Tente novamente."
            }
            emit(OrchestrationStep.FinalResponse(errorMessage))
        }
    }

    private suspend fun executeTool(name: String, args: Map<String, Any?>, serverId: String, worldPath: String): String {
        return try {
            when (name) {
                "write_file" -> {
                    val path = args["path"] as? String ?: return "Erro: path ausente"
                    val content = args["content"] as? String ?: return "Erro: content ausente"
                    writeFile(path, content, serverId, worldPath)
                }
                "read_file" -> {
                    val path = args["path"] as? String ?: return "Erro: path ausente"
                    readFile(path, serverId, worldPath)
                }
                "list_files" -> {
                    val path = args["path"] as? String ?: return "Erro: path ausente"
                    listFiles(path, serverId, worldPath)
                }
                "run_command" -> {
                    val cmd = args["command"] as? String ?: return "Erro: command ausente"
                    runCommand(cmd, serverId)
                }
                "get_logs" -> {
                    getLogs(serverId)
                }
                "get_server_status" -> {
                    getServerStatus(serverId)
                }
                "extract_archive" -> {
                    val path = args["path"] as? String ?: return "Erro: path ausente"
                    val destination = args["destination"] as? String ?: return "Erro: destination ausente"
                    extractArchive(path, destination)
                }
                "get_player_position" -> {
                    val playerName = args["player_name"] as? String ?: return "Erro: player_name ausente"
                    getPlayerPosition(playerName, serverId)
                }
                "reload_datapack" -> {
                    reloadDatapack(serverId)
                }
                "search_block_id" -> {
                    val query = args["query"] as? String ?: return "Erro: query ausente"
                    searchBlockId(query)
                }
                "save_memory" -> {
                    val name = args["name"] as? String ?: return "Erro: name ausente"
                    val location = args["location"] as? String
                    val description = args["description"] as? String ?: return "Erro: description ausente"
                    saveMemory(name, location, description, serverId)
                }
                "recall_memory" -> {
                    val limit = (args["limit"] as? Number)?.toInt() ?: 5
                    recallMemory(serverId, limit)
                }
                else -> "Tool não implementada: $name"
            }
        } catch (e: Exception) {
            "Erro ao executar $name: ${e.message}"
        }
    }

    private fun extractArchive(archivePath: String, destPath: String): String {
        return try {
            val archiveFile = File(archivePath)
            if (!archiveFile.exists()) return "Erro: Arquivo comprimido não encontrado em $archivePath"
            
            val destDir = File(destPath)
            if (!destDir.exists()) destDir.mkdirs()

            if (archivePath.endsWith(".tar.gz") || archivePath.endsWith(".tgz")) {
                extractTarGz(archiveFile, destDir)
            } else if (archivePath.endsWith(".zip")) {
                extractZip(archiveFile, destDir)
            } else {
                return "Erro: Formato de arquivo não suportado. Use .tar.gz ou .zip"
            }
            
            "Sucesso: Arquivo extraído para ${destDir.absolutePath}. Use 'list_files' para ver o conteúdo."
        } catch (e: Exception) {
            "Erro na extração: ${e.message}"
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        archive.inputStream().use { fi ->
            org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(fi).use { bi ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bi).use { ti ->
                    var entry: org.apache.commons.compress.archivers.tar.TarArchiveEntry? = ti.nextTarEntry
                    while (entry != null) {
                        val newFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            newFile.outputStream().use { out ->
                                ti.copyTo(out)
                            }
                        }
                        entry = ti.nextTarEntry
                    }
                }
            }
        }
    }

    private fun extractZip(archive: File, destDir: File) {
        archive.inputStream().use { fi ->
            org.apache.commons.compress.archivers.zip.ZipArchiveInputStream(fi).use { zi ->
                var entry: org.apache.commons.compress.archivers.zip.ZipArchiveEntry? = zi.nextZipEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        newFile.outputStream().use { out ->
                            zi.copyTo(out)
                        }
                    }
                    entry = zi.nextZipEntry
                }
            }
        }
    }

    private fun writeFile(relPath: String, content: String, serverId: String, worldPath: String): String {
        return try {
            // SECURITY: Allow .mcfunction, .json and .mcmeta files
            if (!relPath.endsWith(".mcfunction") && !relPath.endsWith(".json") && !relPath.endsWith(".mcmeta")) {
                return "ERRO DE SEGURANÇA: A IA só tem permissão para modificar arquivos .mcfunction, .json e .mcmeta. Modificações em '${File(relPath).name}' foram bloqueadas."
            }

            // Write to SOURCE (Persistence)
            if (worldPath.startsWith("content://")) {
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(worldPath))
                writeToSaf(rootDoc, relPath, content)
                ensureDatapackStructureSaf(rootDoc, relPath)
            } else {
                val sourceFile = if (relPath.startsWith("/")) File(relPath) else File(worldPath, relPath)
                if (!sourceFile.parentFile.exists()) sourceFile.parentFile.mkdirs()
                sourceFile.writeText(content)
                ensureDatapackStructureFile(sourceFile)
            }

            // Write to EXECUTION (Live)
            val executionDir = serverManager.getExecutionDirectory(serverId)
            val execFile = if (relPath.startsWith("/")) File(relPath) else File(executionDir, relPath)
            if (!execFile.parentFile.exists()) execFile.parentFile.mkdirs()
            execFile.writeText(content)
            
            "Sucesso: Arquivo '$relPath' gravado e sincronizado."
        } catch (e: Exception) {
            "Erro ao gravar: ${e.message}"
        }
    }

    private fun ensureDatapackStructureFile(mcfunctionFile: File) {
        // Check if we are inside a datapack and missing pack.mcmeta
        // Path: .../datapacks/<name>/data/<namespace>/functions/...
        // We look for 'datapacks' in the path
        var current = mcfunctionFile.parentFile
        while (current != null) {
            if (current.parentFile?.name == "datapacks") {
                // 'current' is the datapack root folder
                val meta = File(current, "pack.mcmeta")
                if (!meta.exists()) {
                    meta.writeText("""{"pack":{"pack_format":6,"description":"AI Generated"}}""")
                }
                break
            }
            current = current.parentFile
        }
    }

    private fun ensureDatapackStructureSaf(root: androidx.documentfile.provider.DocumentFile?, relPath: String) {
        if (root == null) return
        // Basic check: is "datapacks" in the path?
        val parts = relPath.split("/")
        val datapacksIndex = parts.indexOf("datapacks")
        if (datapacksIndex != -1 && datapacksIndex + 1 < parts.size) {
            val packName = parts[datapacksIndex + 1]
            
            // Navigate to datapack root
            var current = root
            for (i in 0..datapacksIndex + 1) {
                current = current?.findFile(parts[i]) ?: return 
            }
            
            // Create pack.mcmeta if missing
            if (current?.findFile("pack.mcmeta") == null) {
                current?.createFile("application/json", "pack.mcmeta")?.let { doc ->
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { 
                        it.write("""{"pack":{"pack_format":6,"description":"AI Generated"}}""".toByteArray()) 
                    }
                }
            }
        }
    }

    private fun writeToSaf(root: androidx.documentfile.provider.DocumentFile?, relPath: String, content: String) {
        if (root == null) return
        
        var current: androidx.documentfile.provider.DocumentFile = root
        val parts = relPath.split("/")
        
        // Navigate through folders, creating them if necessary
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            if (part.isEmpty() || part == ".") continue
            current = current.findFile(part) ?: current.createDirectory(part) ?: throw Exception("Falha ao criar pasta SAF: $part")
        }
        
        val fileName = parts.last()
        val fileDoc = current.findFile(fileName) ?: current.createFile("text/plain", fileName)
        fileDoc?.let { doc ->
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                out.write(content.toByteArray())
            }
        } ?: throw Exception("Falha ao criar arquivo SAF: $fileName")
    }

    private fun readFile(path: String, serverId: String, worldPath: String): String {
        return try {
            if (path.startsWith("content://") || worldPath.startsWith("content://")) {
                // Simplified: if path is relative, assume it's in worldPath
                val effectivePath = if (!path.startsWith("content://") && !path.startsWith("/")) {
                   // Navigate root doc
                   val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(worldPath))
                   readFromSaf(rootDoc, path)
                } else if (path.startsWith("content://")) {
                   context.contentResolver.openInputStream(android.net.Uri.parse(path))?.bufferedReader()?.use { it.readText() } ?: "Vazio"
                } else {
                   File(path).readText()
                }
                effectivePath.toString()
            } else {
                val executionDir = serverManager.getExecutionDirectory(serverId)
                val file = if (path.startsWith("/")) File(path) else File(executionDir, path)
                if (file.exists()) file.readText() else "Erro: Arquivo não encontrado"
            }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private fun readFromSaf(root: androidx.documentfile.provider.DocumentFile?, relPath: String): String {
        if (root == null) return "Erro: SAF root nulo"
        var current: androidx.documentfile.provider.DocumentFile? = root
        val parts = relPath.split("/")
        for (part in parts) {
            current = current?.findFile(part)
        }
        return current?.let { doc ->
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.use { it.readText() }
        } ?: "Erro: Arquivo não encontrado no SAF"
    }

    private fun listFiles(path: String, serverId: String, worldPath: String): String {
        return try {
            val executionDir = serverManager.getExecutionDirectory(serverId)
            val dir = if (path.startsWith("/")) File(path) else File(executionDir, path)
            if (dir.exists() && dir.isDirectory) {
                dir.list()?.joinToString("\n") ?: "Vazio"
            } else "Erro: Diretório não encontrado"
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private suspend fun runCommand(command: String, serverId: String): String {
        return try {
            // STDIN Fallback: Inject command directly to server process
            // This bypasses RCON entirely, which has been unreliable (EOF issues).
            android.util.Log.d("AiOrchestrator", "======== log de depuração ======== : Using STDIN to send: $command")
            serverManager.sendCommand(serverId, command)
            "Comando enviado via console."
        } catch (e: Exception) {
            "Erro ao enviar comando: ${e.message}"
        }
    }

    private fun getLogs(serverId: String): String {
        return try {
            val executionDir = serverManager.getExecutionDirectory(serverId)
            val logFile = File(executionDir, "logs/latest.log")
            if (logFile.exists()) {
                val lines = logFile.readLines()
                lines.takeLast(30).joinToString("\n")
            } else "Nenhum log encontrado"
        } catch (e: Exception) {
            "Erro ao ler logs: ${e.message}"
        }
    }

    private fun getServerStatus(serverId: String): String {
        val running = serverManager.isServerRunning(serverId)
        return "Status: ${if (running) "Online" else "Offline"}"
    }

    private suspend fun getPlayerPosition(playerName: String, serverId: String): String {
        return try {
            serverManager.sendCommand(serverId, "data get entity $playerName Pos")
            kotlinx.coroutines.delay(300)
            val logs = getLogs(serverId)
            // Parse: "Player has the following entity data: [0.0d, 100.0d, 0.0d]"
            val posMatch = Regex("\\[(-?\\d+\\.\\d+)d, (-?\\d+\\.\\d+)d, (-?\\d+\\.\\d+)d\\]").find(logs)
            posMatch?.let {
                val x = it.groupValues[1].toDouble().toInt()
                val y = it.groupValues[2].toDouble().toInt()
                val z = it.groupValues[3].toDouble().toInt()
                "Posição de $playerName: X=$x, Y=$y, Z=$z"
            } ?: "Jogador '$playerName' não encontrado ou offline."
        } catch (e: Exception) {
            "Erro ao obter posição: ${e.message}"
        }
    }

    private suspend fun reloadDatapack(serverId: String): String {
        return try {
            serverManager.sendCommand(serverId, "reload confirm")
            kotlinx.coroutines.delay(500)
            val logs = getLogs(serverId)
            
            // Check for common errors in logs
            val errorPatterns = listOf("Unknown block", "Unknown item", "Failed to load function", "Error", "Whilst parsing")
            val errors = logs.lines().filter { line -> errorPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) } }
            
            if (errors.isNotEmpty()) {
                "⚠️ ERROS DETECTADOS APÓS RELOAD:\n${errors.takeLast(5).joinToString("\n")}"
            } else {
                "✅ Reload OK. Nenhum erro de sintaxe detectado."
            }
        } catch (e: Exception) {
            "Erro ao recarregar: ${e.message}"
        }
    }

    // Block ID mapping for RAG-like functionality (Portuguese -> Minecraft ID)
    private val blockIdMap = mapOf(
        // Vidros coloridos
        "vidro vermelho" to "red_stained_glass",
        "vidro azul" to "blue_stained_glass",
        "vidro verde" to "green_stained_glass",
        "vidro amarelo" to "yellow_stained_glass",
        "vidro branco" to "white_stained_glass",
        "vidro preto" to "black_stained_glass",
        "vidro laranja" to "orange_stained_glass",
        "vidro rosa" to "pink_stained_glass",
        "vidro roxo" to "purple_stained_glass",
        "vidro ciano" to "cyan_stained_glass",
        "vidro marrom" to "brown_stained_glass",
        "vidro cinza" to "gray_stained_glass",
        "vidro lime" to "lime_stained_glass",
        "vidro magenta" to "magenta_stained_glass",
        
        // Concretos
        "concreto branco" to "white_concrete",
        "concreto vermelho" to "red_concrete",
        "concreto azul" to "blue_concrete",
        "concreto verde" to "green_concrete",
        "concreto amarelo" to "yellow_concrete",
        "concreto preto" to "black_concrete",
        "concreto laranja" to "orange_concrete",
        "concreto rosa" to "pink_concrete",
        "concreto roxo" to "purple_concrete",
        "concreto ciano" to "cyan_concrete",
        "concreto marrom" to "brown_concrete",
        "concreto cinza" to "gray_concrete",
        "concreto lime" to "lime_concrete",
        "concreto magenta" to "magenta_concrete",
        
        // Lãs
        "lã branca" to "white_wool",
        "lã vermelha" to "red_wool",
        "lã azul" to "blue_wool",
        "lã verde" to "green_wool",
        "lã amarela" to "yellow_wool",
        "lã preta" to "black_wool",
        
        // Blocos comuns
        "pedra" to "stone",
        "terra" to "dirt",
        "grama" to "grass_block",
        "areia" to "sand",
        "cascalho" to "gravel",
        "madeira carvalho" to "oak_planks",
        "madeira" to "oak_planks",
        "tijolo" to "bricks",
        "tijolos" to "bricks",
        "obsidiana" to "obsidian",
        "diamante" to "diamond_block",
        "ouro" to "gold_block",
        "ferro" to "iron_block",
        "esmeralda" to "emerald_block",
        "lapis" to "lapis_block",
        "redstone" to "redstone_block",
        "quartzo" to "quartz_block",
        "glowstone" to "glowstone",
        "lanterna do mar" to "sea_lantern",
        "prismarinho" to "prismarine",
        "netherrack" to "netherrack",
        "pedra do end" to "end_stone",
        "bedrock" to "bedrock",
        "barreira" to "barrier",
        "ar" to "air",
        "água" to "water",
        "lava" to "lava",
        
        // Novos blocos solicitados e comuns
        "neve" to "snow_block",
        "bloco de neve" to "snow_block",
        "snow block" to "snow_block",
        "gelo" to "ice",
        "gelo compactado" to "packed_ice",
        "gelo azul" to "blue_ice",
        "tnt" to "tnt",
        "dinamite" to "tnt",
        "tocha" to "torch",
        "lanterna" to "lantern",
        "baú" to "chest",
        "bancada" to "crafting_table",
        "fornalha" to "furnace",
        "escada" to "ladder",
        "vidro" to "glass",
        "painel de vidro" to "glass_pane",
        "pedregulho" to "cobblestone",
        "musgo" to "moss_block",
        "folhas" to "oak_leaves",
        "tronco" to "oak_log",
        "tabua" to "oak_planks"
    )

    private fun searchBlockId(query: String): String {
        val normalized = query.lowercase().trim()
        
        // Direct match
        val directMatch = blockIdMap[normalized]
        if (directMatch != null) {
            return "✅ ID correto: $directMatch"
        }
        
        // Partial match
        val partialMatches = blockIdMap.entries.filter { it.key.contains(normalized) || normalized.contains(it.key) }
        if (partialMatches.isNotEmpty()) {
            val suggestions = partialMatches.take(3).joinToString(", ") { "${it.key} -> ${it.value}" }
            return "🔍 Sugestões encontradas: $suggestions"
        }
        
        // English fallback patterns
        val englishPatterns = mapOf(
            "glass" to "stained_glass (adicione cor_: red_stained_glass)",
            "concrete" to "concrete (adicione cor_: white_concrete)",
            "wool" to "wool (adicione cor_: blue_wool)",
            "wood" to "oak_planks, spruce_planks, birch_planks...",
            "stone" to "stone, cobblestone, smooth_stone..."
        )
        
        val englishMatch = englishPatterns.entries.find { normalized.contains(it.key) }
        if (englishMatch != null) {
            return "💡 Dica: ${englishMatch.value}"
        }
        
        return "❌ Bloco '$query' não encontrado. Tente: stone, dirt, oak_planks, white_concrete, red_stained_glass. Consulte: https://minecraft.wiki/w/Block"
    }
    private suspend fun saveMemory(name: String, location: String?, description: String, serverId: String): String {
        return try {
            val entity = AiConstructionEntity(
                serverId = serverId,
                name = name,
                location = location ?: "Desconhecida",
                commands = description
            )
            constructionDao.insert(entity)
            "Memória salva com sucesso: '$name' ${if (location != null) "em $location" else ""}."
        } catch (e: Exception) {
            "Erro ao salvar memória: ${e.message}"
        }
    }

    private suspend fun recallMemory(serverId: String, limit: Int): String {
        return try {
            val memories = constructionDao.getRecentByServer(serverId, limit).first()
            if (memories.isEmpty()) {
                "Nenhuma memória encontrada para este servidor."
            } else {
                val sb = StringBuilder("MEMÓRIAS RECENTES:\n")
                memories.forEach { mem ->
                    sb.append("- [${mem.name}] (${mem.location}): ${mem.commands.take(100)}${if (mem.commands.length > 100) "..." else ""}\n")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "Erro ao recuperar memórias: ${e.message}"
        }
    }

    fun clearSession(serverId: String) {
        chatSessions.remove(serverId)
    }
}
