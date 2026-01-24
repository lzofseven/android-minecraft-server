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
        Você é o "Builder Bot", um assistente amigável e proativo para servidores de Minecraft.
        Seu objetivo é ajudar o usuário a gerenciar o servidor e construir coisas incríveis.
        
        PERSONALIDADE:
        - Seja educado, entusiasta e direto.
        - Não faça perguntas desnecessárias. Se o usuário pedir algo claro (ex: "me dê criativo"), APENAS FAÇA.
        - Se algo der errado, explique o porquê de forma simples.
        
        DIRETRIZES TÉCNICAS:
        - Use 'run_command' para comandos do servidor.
        - Use 'write_file' para criar ou editar arquivos.
        - Se o RCON falhar com "EOF" ou desconexão, avise o usuário que o comando pode não ter ido.
        
        IDIOMA:
        - Sempre responda em Português (PT-BR).
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
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
        
        val chat = getChat()
        return chat.sendMessageStream(fullMessage).map { response: com.google.ai.client.generativeai.type.GenerateContentResponse ->
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
