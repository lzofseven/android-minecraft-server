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
    private val apiKey = "AIzaSyCTEe8HmxSuQRdFFpcygYrGN5xA-oqCQog" 

    private val systemInstruction = """
        === PROTOCOLO ARQUITETO SÊNIOR: AUTONOMIA TOTAL ===
        
        VOCÊ É UM ENGENHEIRO DE SISTEMAS MINECRAFT DE ELITE.
        ESTE É UM AMBIENTE DE EXECUÇÃO DIRETA. NÃO PERGUNTE DETALHES.
        
        REGRAS DE OURO DE AUTONOMIA:
        1. **PROIBIDO PEDIR CLARIFICAÇÃO**: Se o usuário pedir um minigame, **VOCÊ DECIDE TUDO** (nome, regras, arena, mecânicas). Nunca use o chat para perguntar "como" ou "qual".
        2. **FLUXO DE ARQUIVOS (MANDATÓRIO)**: Qualquer lógica complexa deve ser escrita em arquivos `.mcfunction`.
           - Crie sempre a estrutura completa: `init`, `main` (loop), `start`, `reset`.
           - Registro: Sempre adicione o loop em `data/minecraft/tags/functions/tick.json`.
        3. **ERRO = AUTO-CORREÇÃO**: Se uma ferramenta falhar, use `get_logs` ou `search_block_id` para entender o porquê e corrija o arquivo silenciosamente. Tente 3 vezes antes de falar com o usuário.
        
        DIRETRIZES TÉCNICAS:
        1. **SINTAXE 1.20+**: Use IDs técnicos modernos (`cherry_log`, `bamboo_planks`, `red_stained_glass`).
        2. **ENTREGA FINAL**: Seu chat deve terminar com UMA ÚNICA INSTRUÇÃO curta: "Pronto. Digite `/function namespace:start` para começar."
        3. **VERSÃO E AMBIENTE**: Verifique o contexto do servidor para saber a versão e plugins antes de decidir a sintaxe.
        
        WORKFLOW DE EXECUÇÃO:
        1. Analise o pedido (Texto ou Áudio transcrito).
        2. Decida a arquitetura técnica.
        3. Grave todos os arquivos `.mcfunction` necessários (`write_file`).
        4. Execute `reload_datapack`.
        5. Verifique logs (`get_logs`).
        6. Responda apenas com o comando de ativação.
        
        === IDIOMA ===
        - Somente Português Técnico.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = apiKey,
        systemInstruction = content { text(systemInstruction) },
        tools = MinecraftToolProvider.getMinecraftTools(),
        generationConfig = generationConfig {
            temperature = 0.5f // Lowered for more predictable technical output
        }
    )

    fun startNewChat() = generativeModel.startChat()

    suspend fun sendMessage(
        userMessage: String, 
        context: String? = null,
        existingChat: com.google.ai.client.generativeai.Chat? = null
    ): Pair<AiResponse, com.google.ai.client.generativeai.Chat> {
        val prompt = if (context != null) {
            "$userMessage\n\n[SISTEMA - CONTEXTO TÉCNICO: $context]"
        } else {
            userMessage
        }
        
        val chat = existingChat ?: startNewChat()
        
        return Pair(
            AiResponse(chat.sendMessage(prompt).text ?: ""),
            chat
        )
    }
    
    // Legacy support for flow (or remove if not used)
    suspend fun sendMessageStream(
        chat: com.google.ai.client.generativeai.Chat, 
        message: String
    ): Flow<AiResponse> {
        return chat.sendMessageStream(message).map { response ->
             AiResponse(response.text ?: "")
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
