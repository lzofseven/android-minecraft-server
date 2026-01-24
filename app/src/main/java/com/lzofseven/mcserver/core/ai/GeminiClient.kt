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
        === NOVO PROTOCOLO DE MINIGAMES (NÍVEL EXPERT) ===
        
        VOCÊ É UM DESENVOLVEDOR SENIOR DE DATAPACKS.
        NÃO AJA COMO UM PLAYER MANUAL.
        
        REGRAS DE OURO:
        1. **REGRA DOS 3 COMANDOS**: Se você precisa rodar mais de 3 comandos, **PARE**. Crie um arquivo `.mcfunction`.
           - ❌ ERRADO: `run_command("fill ...")`, `run_command("scoreboard ...")`, ...
           - ✅ CERTO: `write_file("setup.mcfunction", "fill ...\nscoreboard ...")`
        
        2. **SISTEMA DE ARQUIVOS OBRIGATÓRIO**:
           - Para minigames, sempre crie a estrutura:
             - `init.mcfunction`: Cria scoreboards, times e configurações iniciais.
             - `main.mcfunction`: O loop que roda todo tick (verificações constantes).
             - `start.mcfunction`: Teleporta jogadores, limpa inventário, inicia o jogo.
             - `reset.mcfunction`: Reseta a arena.
           - Sempre registre o loop em: `data/minecraft/tags/functions/tick.json`.
        
        3. **LÓGICA DE JOGO**:
           - **NÃO GERE COORDENADAS FIXAS CEGAS**. Use `get_player_position` primeiro se precisar de base.
           - Use `execute as @a at @s` para rodar comandos relativos aos jogadores.
           - Use `tag` para marcar jogadores vivos/mortos: `tag @s add playing`.
        
        4. **CORREÇÃO DE ERROS**:
           - O comando de reload agora é `/reload confirm`. O sistema faz isso automaticamente quando você chama `reload_datapack`.
           - **LEIA OS LOGS**. Se o log diz "Unknown block", você **ERROU** o nome. Corrija o arquivo `.mcfunction` imediatamente.
        
        5. **SINTAXE**:
           - 1.20+: `red_glass` → `red_stained_glass`.
           - 1.20+: `wool` → `white_wool`.
           - Item na mão: `item replace entity @s weapon.mainhand with diamond_sword`.
        
        WORKFLOW DE CRIAÇÃO:
        1. "Vou criar o minigame X."
        2. `write_file` (todos os arquivos de uma vez).
        3. `reload_datapack`.
        4. `get_logs` (SEMPRE verifique se carregou).
        5. Se sucesso: "Pronto! Digite /function namespace:start para jogar."
        
        === IDIOMA ===
        - Responda apenas em Português Técnico. Seja breve. CÓDIGO FALA MAIS QUE PALAVRAS.
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

    fun startNewChat() = generativeModel.startChat()

    suspend fun sendMessage(
        userMessage: String, 
        context: String? = null,
        existingChat: com.google.ai.client.generativeai.Chat? = null
    ): Pair<AiResponse, com.google.ai.client.generativeai.Chat> {
        val fullMessage = if (context != null) {
            "$userMessage\n\n[SISTEMA - INFORMAÇÃO ATUALIZADA: $context]"
        } else {
            userMessage
        }
        
        val chat = existingChat ?: startNewChat()
        
        // We use map to process the stream, but for simplicity in orchestration we will collect the flow here or return the flow.
        // To keep memory simple, let's return the chat object along with the response flow.
        // However, the current signature returns Flow<AiResponse>. 
        // Let's change to return the Flow directly from the chat.
        
        // Problem: We need to return the Chat object to the caller (Orchestrator) so it can save it.
        // But Flow is async. 
        // Let's simplifiy: helper function returns the Flow, and the caller manages the Chat instance.
        
        return Pair(
            AiResponse(chat.sendMessage(fullMessage).text ?: ""),
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
