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

import com.lzofseven.mcserver.core.ai.ChatMessage
import com.lzofseven.mcserver.core.ai.ActionStatus

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val geminiClient: GeminiClient,
    private val orchestrator: com.lzofseven.mcserver.core.ai.AiOrchestrator,
    private val commandExecutor: AiCommandExecutor,
    private val serverManager: RealServerManager,
    private val contextManager: com.lzofseven.mcserver.core.ai.AiContextManager,
    private val apiKeyManager: com.lzofseven.mcserver.core.auth.ApiKeyManager,
    private val constructionDao: com.lzofseven.mcserver.data.local.dao.AiConstructionDao
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<com.lzofseven.mcserver.core.ai.AiSuggestion>>(emptyList())
    val suggestions: StateFlow<List<com.lzofseven.mcserver.core.ai.AiSuggestion>> = _suggestions.asStateFlow()

    // Track current server ID to swap histories
    private var currentServerId: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // API Key State
    val hasApiKey = MutableStateFlow(false)

    init {
        checkRcon()
        observeHistory()
        refreshSuggestions()
        observeApiKey()
    }
    
    private fun observeApiKey() {
        viewModelScope.launch {
            apiKeyManager.apiKeyFlow.collect { key ->
                if (!key.isNullOrBlank()) {
                    try {
                        geminiClient.initialize(key)
                        hasApiKey.value = true
                    } catch (e: Exception) {
                        hasApiKey.value = false
                        _errorMessage.value = "Erro ao inicializar IA: ${e.message}"
                    }
                } else {
                    hasApiKey.value = false
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (!hasApiKey.value) {
             _errorMessage.value = "Configure sua Chave API nas configurações."
             return
        }
        if (_needsRconSetup.value) {
            _errorMessage.value = "RCON necessário para usar a IA."
            return
        }

        val msgs = _messages.value.toMutableList()
        msgs.add(ChatMessage("user", text))
        _messages.value = msgs
        
        viewModelScope.launch {
            val serverId = serverManager.getActiveServerEntity()?.id ?: return@launch
            orchestrator.addMessageToHistory(serverId, ChatMessage("user", text))
            
            // Refresh suggestions after sending
            refreshSuggestions()
            
            _isLoading.value = true
            processRequest(text = text)
        }
    }

    private suspend fun processRequest(text: String) {
        try {
            val activeServer = serverManager.getActiveServerEntity()
            if (activeServer == null) {
                _errorMessage.value = "Nenhum servidor ativo selecionado."
                return
            }

            if (!serverManager.isServerRunning(activeServer.id)) {
                _messages.value = _messages.value + ChatMessage("model", "O servidor parece estar offline. Preciso que ele esteja rodando para poder executar comandos ou verificar o status.")
                return
            }

            val effectivePath = if (activeServer.path.startsWith("content://")) {
                serverManager.getRealPathFromSaf(activeServer.path) ?: activeServer.path
            } else {
                activeServer.path
            }
            val decodedPath = java.net.URLDecoder.decode(effectivePath, "UTF-8")

            val contextStr = contextManager.getComprehensiveContext(activeServer.id)

            orchestrator.processUserRequest(text, contextStr, activeServer.id, decodedPath).collect { step ->
                handleOrchestrationStep(step, activeServer.id)
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun handleOrchestrationStep(step: com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep, serverId: String) {
        when (step) {
            is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.LogMessage -> {
                val msg = ChatMessage("system", step.message, isOrchestrationLog = true)
                orchestrator.addMessageToHistory(serverId, msg)
            }
            is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.ToolExecuting -> {
                val msg = ChatMessage("system", "🛠 Executando ${step.toolName}: ${step.args}", isOrchestrationLog = true)
                orchestrator.addMessageToHistory(serverId, msg)
                
                if (step.toolName == "run_command") {
                    kotlinx.coroutines.delay(1000)
                    contextManager.getComprehensiveContext(serverId)
                }
            }
            is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.FinalResponse -> {
                val msg = ChatMessage("model", step.text)
                orchestrator.addMessageToHistory(serverId, msg)
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
        observeHistory()
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        _suggestions.value = com.lzofseven.mcserver.core.ai.AiSuggestionProvider.getRandomSuggestions(6)
    }

    private fun observeHistory() {
        viewModelScope.launch {
            val server = serverManager.getActiveServerEntity() ?: return@launch
            currentServerId = server.id
            orchestrator.getChatHistoryFlow(server.id).collect {
                _messages.value = it
            }
        }
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
