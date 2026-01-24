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
    val isOrchestrationLog: Boolean = false,
    val actionStatuses: Map<Int, ActionStatus> = emptyMap()
)

enum class ActionStatus {
    PENDING, EXECUTING, SUCCESS, ERROR
}

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val geminiClient: GeminiClient,
    private val orchestrator: com.lzofseven.mcserver.core.ai.AiOrchestrator,
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
                val activeServer = serverManager.getActiveServerEntity()
                if (activeServer == null) {
                    _errorMessage.value = "Nenhum servidor ativo selecionado."
                    return@launch
                }

                // Fix: Decode URL characters (e.g., %20 -> space)
                val effectivePath = if (activeServer.path.startsWith("content://")) {
                    serverManager.getRealPathFromSaf(activeServer.path) ?: activeServer.path
                } else {
                    activeServer.path
                }
                val decodedPath = java.net.URLDecoder.decode(effectivePath, "UTF-8")

                // Get Comprehensive Context (Memory + OPs + GameModes)
                var contextStr: String? = null
                if (activeServer != null) {
                    contextStr = contextManager.getComprehensiveContext(activeServer.id)
                }

                orchestrator.processUserRequest(text, contextStr, activeServer!!.id, decodedPath).collect { step ->
                    when (step) {
                        is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.LogMessage -> {
                            // Add a system log message or update status
                            val current = _messages.value.toMutableList()
                            current.add(ChatMessage("system", step.message, isOrchestrationLog = true))
                            _messages.value = current
                        }
                        is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.ToolExecuting -> {
                            // Show tool execution in chat
                            val current = _messages.value.toMutableList()
                            current.add(ChatMessage("system", "🛠 Executando ${step.toolName}: ${step.args}", isOrchestrationLog = true))
                            _messages.value = current
                            
                            // FORCE CONTEXT REFRESH AFTER TOOLS (e.g. OP command)
                            if (step.toolName == "run_command") {
                                kotlinx.coroutines.delay(1000) // Give server time to update ops.json
             
                                // Re-inject updated context for next turn if still in loop (though orchestrator loop handles one turn)
                                // Ideally, we update the ViewModel state or just rely on the next user message to pick it up.
                                // But for multi-turn inside Orchestrator, Orchestrator itself needs to update context?
                                // Actually, processUserRequest takes context *once*. 
                                // For immediate effect *within* the same reasoning loop, Orchestrator needs to re-fetch?
                                // Our current architecture passes context initially. 
                                // If the AI Ops someone in step 1, step 2 won't know it yet unless we pass a callback or mutable context provider.
                                // IMPORTANT: User said "after I typed /op, it didn't know".
                                // For now, let's just ensure the NEXT user message gets fresh context.
                                contextManager.getComprehensiveContext(activeServer!!.id) // Trigger refresh cache if any
                            }
                        }
                        is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.FinalResponse -> {
                            // Add AI response to chat
                            val current = _messages.value.toMutableList()
                            current.add(ChatMessage("model", step.text))
                            _messages.value = current
                        }
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
