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
    private val constructionDao: com.lzofseven.mcserver.data.local.dao.AiConstructionDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val audioRecorder = com.lzofseven.mcserver.util.AudioRecorder(context)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun startRecording() {
        if (_isLoading.value) return
        _isRecording.value = true
        audioRecorder.startRecording()
    }

    fun stopAndSendAudio() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val audioBytes = audioRecorder.stopRecording()
        
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            sendAudioRequest(audioBytes)
        }
    }

    private fun sendAudioRequest(audioBytes: ByteArray) {
        val msgs = _messages.value.toMutableList()
        msgs.add(ChatMessage("user", "🎤 Mensagem de áudio", isOrchestrationLog = false))
        _messages.value = msgs
        _isLoading.value = true

        viewModelScope.launch {
            processRequest(text = "O usuário enviou um áudio. Processe o comando contido nele.", audioBytes = audioBytes)
        }
    }

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
            processRequest(text = text, audioBytes = null)
        }
    }

    private suspend fun processRequest(text: String, audioBytes: ByteArray?) {
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

            orchestrator.processUserRequest(text, contextStr, activeServer.id, decodedPath, audioBytes).collect { step ->
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
                val current = _messages.value.toMutableList()
                current.add(ChatMessage("system", step.message, isOrchestrationLog = true))
                _messages.value = current
            }
            is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.ToolExecuting -> {
                val current = _messages.value.toMutableList()
                current.add(ChatMessage("system", "🛠 Executando ${step.toolName}: ${step.args}", isOrchestrationLog = true))
                _messages.value = current
                
                if (step.toolName == "run_command") {
                    kotlinx.coroutines.delay(1000)
                    contextManager.getComprehensiveContext(serverId)
                }
            }
            is com.lzofseven.mcserver.core.ai.AiOrchestrator.OrchestrationStep.FinalResponse -> {
                val current = _messages.value.toMutableList()
                current.add(ChatMessage("model", step.text))
                _messages.value = current
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
