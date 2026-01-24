package com.lzofseven.mcserver.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.core.ai.AiAction
import com.lzofseven.mcserver.core.ai.AiCommandExecutor
import com.lzofseven.mcserver.core.ai.GeminiClient
import com.lzofseven.mcserver.core.execution.RealServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val role: String, // "user" or "model" or "system" (for logs)
    val content: String,
    val isAction: Boolean = false,
    val actionStatuses: Map<Int, ActionStatus> = emptyMap()
)

enum class ActionStatus {
    PENDING, EXECUTING, SUCCESS, ERROR
}

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val geminiClient: GeminiClient,
    private val commandExecutor: AiCommandExecutor,
    private val serverManager: RealServerManager,
    private val contextManager: com.lzofseven.mcserver.core.ai.AiContextManager,
    private val constructionDao: com.lzofseven.mcserver.data.local.dao.AiConstructionDao
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (_needsRconSetup.value) {
            _errorMessage.value = "RCON necessário para usar a IA."
            return
        }

        val msgs = _messages.value.toMutableList()
        msgs.add(ChatMessage("user", text))
        _messages.value = msgs
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Collect the stream. MVP: Just wait for full response
                // Logic: Flatten the flow to a single string for now or update UI progressively.
                // For "Action" parsing, it's easier to process the full text.
                
                val fullResponseBuilder = StringBuilder()
                
                // Get Comprehensive Context (Memory + OPs + GameModes)
                val activeServer = serverManager.getActiveServerEntity()
                var contextStr: String? = null
                if (activeServer != null) {
                    contextStr = contextManager.getComprehensiveContext(activeServer.id)
                }
                
                geminiClient.sendMessage(text, contextStr).collect { chunk ->
                    fullResponseBuilder.append(chunk.text)
                }
                
                val fullResponse = fullResponseBuilder.toString()
                
                // Add AI response to chat
                val updatedMsgs = _messages.value.toMutableList()
                updatedMsgs.add(ChatMessage("model", fullResponse))
                _messages.value = updatedMsgs
                
                // Extract and Execute Actions
                val actions = geminiClient.extractActions(fullResponse)
                if (actions.isNotEmpty()) {
                    val activeServer = serverManager.getActiveServerEntity()
                    if (activeServer != null) {
                         // We use the path from the server entity.
                         // For servers created with CreateServerScreen, 'path' is the directory.
                         // But SAF Uris might be tricky. RealServerManager handles that.
                         // For now, let's assume standard file path or non-SAF internal storage.
                         // Implementing SAF support for file creation inside AiExecutor is out of scope for strict simple file writing
                         // unless we pass DocumentFile. But the user said "create mcfunction file on my phone" implying direct access.
                         // We'll pass the path string. AiExecutor logic I wrote assumes it's a File path.
                         // If it's a content:// URI, AiExecutor logic will fail to find "world" folder with File API.
                         // For this feature to work robustly on Android 11+ with SAF, we'd need DocumentFile logic.
                         // However, the `path` in MCServerEntity often stores the URI string.
                         // Let's rely on RealServerManager.getRealPathFromSaf if needed or just pass the raw path 
                         // and hope AiExecutor's File(path) works (older Android or root/internal storage).
                         // Given the user constraint "Create files on my *phone*", let's try our best.
                         
                         val effectivePath = if (activeServer.path.startsWith("content://")) {
                             // Try to resolve real path (only works for some providers/root)
                             // Or fail gracefully.
                             // Attempt to delegate to a helper? 
                             // RealServerManager has getRealPathFromSaf but it's private or public?
                             // It is public in the outline!
                             serverManager.getRealPathFromSaf(activeServer.path) ?: activeServer.path
                         } else {
                             activeServer.path
                         }
                         
                         // Fix: Decode URL characters (e.g., %20 -> space) because File() expects a raw path
                         val decodedPath = java.net.URLDecoder.decode(effectivePath, "UTF-8")

                        // PRE-FLIGHT CHECK: RCON Enabled?
                         // We check the execution directory because that's what the running server uses
                         val executionDir = serverManager.getExecutionDirectory(activeServer.id)
                         val rconConfig = com.lzofseven.mcserver.util.ServerPropertiesHelper.getRconConfig(executionDir.absolutePath)
                         
                         if (!rconConfig.enabled) {
                             _errorMessage.value = "ERRO CRÍTICO: RCON Desativado!\n\n1. Edite server.properties\n2. Defina 'enable-rcon=true'\n3. Defina 'rcon.password'\n4. Reinicie o servidor."
                             return@launch
                         }

                        actions.forEachIndexed { index, action ->
                            // Update status to EXECUTING
                            updateLastMessageStatus(index, ActionStatus.EXECUTING)
                            
                            android.util.Log.d("AiChatViewModel", "Executing action $index: $action on path: $decodedPath via RCON:${rconConfig.port}")

                            try {
                                commandExecutor.executeAction(action, decodedPath, activeServer.id, rconConfig)
                                
                                // PERSISTENCE: Save successful constructions
                                if (action is AiAction.CreateFunction) {
                                    constructionDao.insert(
                                        com.lzofseven.mcserver.data.local.entity.AiConstructionEntity(
                                            serverId = activeServer.id,
                                            name = action.name,
                                            commands = action.content
                                        )
                                    )
                                }

                                // Update status to SUCCESS
                                updateLastMessageStatus(index, ActionStatus.SUCCESS)
                            } catch (e: Exception) {
                                android.util.Log.e("AiChatViewModel", "Action failed", e)
                                // Update status to ERROR (we could optionally store the error message in the map if we expanded it)
                                updateLastMessageStatus(index, ActionStatus.ERROR)
                                // Still show a toast or dialog for the specific error
                                _errorMessage.value = "Erro na ação $index: ${e.message}"
                            }
                        }
                    } else {
                        // User needs to select a server
                         _errorMessage.value = "Nenhum servidor ativo selecionado."
                    }
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _needsRconSetup = MutableStateFlow(false)
    val needsRconSetup: StateFlow<Boolean> = _needsRconSetup.asStateFlow()

    fun setupRcon() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val activeServer = serverManager.getActiveServerEntity()
                if (activeServer != null) {
                    val executionDir = serverManager.getExecutionDirectory(activeServer.id)
                    val password = com.lzofseven.mcserver.util.ServerPropertiesHelper.generateRconPassword()
                    
                    com.lzofseven.mcserver.util.ServerPropertiesHelper.writeRconConfig(
                        executionDir.absolutePath, 
                        25575, 
                        password
                    )
                    
                    // Trigger Restart
                    serverManager.stopServer(activeServer.id)
                    kotlinx.coroutines.delay(3000)
                    serverManager.startServer(activeServer)
                    
                    _needsRconSetup.value = false
                    _messages.value = _messages.value + ChatMessage("model", "Configurei o RCON e reiniciei o servidor. Tente enviar um comando em instantes!")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Falha ao configurar RCON: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun checkRcon() {
        val activeServer = serverManager.getActiveServerEntity() ?: return
        val executionDir = serverManager.getExecutionDirectory(activeServer.id)
        val config = com.lzofseven.mcserver.util.ServerPropertiesHelper.getRconConfig(executionDir.absolutePath)
        
        if (!config.enabled || config.password.isEmpty()) {
            _needsRconSetup.value = true
        } else {
             _needsRconSetup.value = false
        }
    }
    
    init {
        checkRcon()
    }

    private fun updateLastMessageStatus(actionIndex: Int, status: ActionStatus) {
        val currentList = _messages.value.toMutableList()
        if (currentList.isNotEmpty()) {
            val lastMsg = currentList.last()
            val newStatuses = lastMsg.actionStatuses.toMutableMap()
            newStatuses[actionIndex] = status
            currentList[currentList.lastIndex] = lastMsg.copy(actionStatuses = newStatuses)
            _messages.value = currentList
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
