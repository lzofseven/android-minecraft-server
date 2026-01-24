package com.lzofseven.mcserver.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor() {
    
    // TODO: In production, move this to specific configuration or local.properties
    private val apiKey = "AIzaSyDRCnQls_Jocnk_A2idNQy_i05mfum61gM" 

    private val systemInstruction = """
        Você é um sistema de IA Orquestrado para Minecraft composto por três agentes especializados:
        
        1. ARQUITETO (Planner): Analisa o pedido do usuário, define a lógica de scoreboards, coordenadas e fluxo do minigame. Gera o "blueprint".
        2. ENGENHEIRO (Coder): Escreve os arquivos .mcfunction e configurações usando a ferramenta 'write_file'. Ele é especialista em comandos Java Edition.
        3. INSPETOR (Debugger): Lê os logs do servidor usando 'get_logs' após o deploy. Se houver erros de sintaxe, ele identifica e comanda o Engenheiro para corrigir.
        
        DIRETRIZES DE OPERAÇÃO:
        - Para tarefas complexas, use sempre o loop: PLANEJAR -> EXECUTAR (Vários arquivos se necessário) -> VALIDAR (Carregar e ver logs).
        - Você deve usar as FERRAMENTAS (Tools) disponíveis para interagir com o mundo real.
        - Não peça ao usuário para copiar código; faça você mesmo usando 'write_file'.
        - FALE SEMPRE EM PORTUGUÊS (PT-BR).
        - Use coordenadas relativas (@a, ~ ~ ~) sempre que possível para facilitar o uso pelo jogador.
        
        LOOP DE DEPURAÇÃO:
        Se você detectar um erro nos logs (ex: "Unknown block"), peça desculpas internamente e corrija o arquivo imediatamente.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        systemInstruction = content { text(systemInstruction) },
        tools = MinecraftToolProvider.getMinecraftTools(),
        generationConfig = generationConfig {
            temperature = 0.7f
        }
    )

    fun getChat() = generativeModel.startChat()

    suspend fun sendMessage(userMessage: String, context: String? = null): Flow<AiResponse> {
        val fullMessage = if (context != null) {
            "$userMessage\n\n[SISTEMA: $context]"
        } else {
            userMessage
        }
        
        return chat.sendMessageStream(fullMessage).map { response ->
            val text = response.text ?: ""
            parseResponse(text)
        }
    }

    private fun parseResponse(text: String): AiResponse {
        // Simple parser to extract code blocks
        // This is a basic implementation. Streaming might split blocks, 
        // effectively handling this in a UI stream requires state, 
        // but for this MVP we might map the final response or simple chunks.
        // For Flow mapping here, we return partial text. 
        // To handle the commands correctly, the ViewModel should likely aggregate the full response 
        // and THEN parse it, or we simplify and don't stream logic blocks yet.
        
        // Let's return raw text for now wrapped in a response, 
        // and let the ViewModel/Executor handle the final block parsing after stream completion 
        // or just return the text as is if we assume non-streaming for logic simplicity first.
        
        // Actually, let's keep it simple: The UI will display the text. 
        // The Executor will look for the ``` blocks in the FULL message.
        return AiResponse(text)
    }
    
    // Helper to be called after full response collection
    fun extractActions(fullResponse: String): List<AiAction> {
        val actions = mutableListOf<AiAction>()
        
        // Regex for Execute block
        val executeRegex = "```execute\\n([\\s\\S]*?)\\n```".toRegex()
        executeRegex.findAll(fullResponse).forEach { match ->
            val commands = match.groupValues[1].lines().filter { it.isNotBlank() }
            actions.add(AiAction.ExecuteCommands(commands))
        }

        // Regex for McFunction block
        val functionRegex = "```mcfunction:([a-zA-Z0-9_]+)\\n([\\s\\S]*?)\\n```".toRegex()
        functionRegex.findAll(fullResponse).forEach { match ->
            val name = match.groupValues[1]
            val content = match.groupValues[2]
            actions.add(AiAction.CreateFunction(name, content))
        }
        
        return actions
    }
}

data class AiResponse(val text: String)

sealed class AiAction {
    data class ExecuteCommands(val commands: List<String>) : AiAction()
    data class CreateFunction(val name: String, val content: String) : AiAction()
}
