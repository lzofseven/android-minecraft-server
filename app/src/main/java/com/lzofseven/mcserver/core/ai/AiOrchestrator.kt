package com.lzofseven.mcserver.core.ai

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.Part
import com.google.ai.client.generativeai.type.content
import com.lzofseven.mcserver.core.execution.RealServerManager
import com.lzofseven.mcserver.core.network.RconClient
import com.lzofseven.mcserver.util.ServerPropertiesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: RealServerManager,
    private val rconClient: RconClient,
    private val geminiClient: GeminiClient // We can still use its API Key and system prompt
) {

    // Orchestration state to be shared with UI
    sealed class OrchestrationStep {
        data class ToolExecuting(val toolName: String, val args: Map<String, Any?>) : OrchestrationStep()
        data class LogMessage(val message: String) : OrchestrationStep()
        data class FinalResponse(val text: String) : OrchestrationStep()
    }

    suspend fun processUserRequest(
        text: String, 
        context: String?,
        serverId: String, 
        worldPath: String
    ): Flow<OrchestrationStep> = flow {
        
        val chat = geminiClient.getChat()
        
        emit(OrchestrationStep.LogMessage("Arquiteto planejando o blueprint..."))
        
        val fullMessage = if (context != null) {
            "$text\n\n[SISTEMA: $context]"
        } else {
            text
        }
        
        var response = chat.sendMessage(fullMessage)
        var iterations = 0
        val maxIterations = 10
        
        // Loop for Function Calling
        while (iterations < maxIterations && response.candidates.firstOrNull()?.content?.parts?.any { it is com.google.ai.client.generativeai.type.FunctionCallPart } == true) {
            iterations++
            val toolResults = mutableListOf<Part>()
            
            val functionCalls = response.candidates.first().content.parts.filterIsInstance<com.google.ai.client.generativeai.type.FunctionCallPart>()
            
            for (call in functionCalls) {
                emit(OrchestrationStep.ToolExecuting(call.name, call.args))
                android.util.Log.d("AiOrchestrator", "======== log de depuração ======== : Starting Tool ${call.name} with args: ${call.args}")
                
                val result = executeTool(call.name, call.args, serverId, worldPath)
                android.util.Log.d("AiOrchestrator", "======== log de depuração ======== : Tool Result for ${call.name}: $result")
                
                toolResults.add(com.google.ai.client.generativeai.type.FunctionResponsePart(call.name, org.json.JSONObject(mapOf("result" to result))))
            }
            
            // Log for the user
            if (iterations > 1) {
                emit(OrchestrationStep.LogMessage("Inspetor validando e refinando (${iterations})..."))
            }

            // Send back the results
            // Send back the results (as "user" or "function" role, SDK 0.9.0 handles function responses usually as part of conversation history)
            // We use direct Content constructor to avoid DSL issues
            response = chat.sendMessage(com.google.ai.client.generativeai.type.Content(role = "user", parts = toolResults))
        }
        
        if (iterations >= maxIterations) {
            emit(OrchestrationStep.LogMessage("Limite de iterações atingido. Finalizando..."))
        }

        val finalResult = response.text ?: "O robô concluiu as tarefas."
        emit(OrchestrationStep.FinalResponse(finalResult))
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
            // SECURITY: Only allow .mcfunction files
            if (!relPath.endsWith(".mcfunction")) {
                return "ERRO DE SEGURANÇA: A IA só tem permissão para modificar arquivos .mcfunction. Modificações em '${File(relPath).name}' foram bloqueadas."
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
}
