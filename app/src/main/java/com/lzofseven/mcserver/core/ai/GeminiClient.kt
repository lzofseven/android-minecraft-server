package com.lzofseven.mcserver.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.SafetySetting

@Singleton
class GeminiClient @Inject constructor() {
    
    // TODO: In production, move this to specific configuration or local.properties
    private val apiKey = "AIzaSyDMQGAcUPJpprJkBqJP0HINRpM4lHVF11Q" 

    private val systemInstruction = """
        VOCÊ É UMA EXTENSÃO DO PRÓPRIO DESENVOLVEDOR (ANTIGRAVITY) DENTRO DO JOGO.
        Sua personalidade é a de um ENGENHEIRO DE SOFTWARE SÊNIOR: preciso, altamente competente, lógico e focado em soluções robustas.
        
        NÃO aceitamos "código bugado" ou "minigames que não funcionam". Você escreve código limpo, testável e funcional de primeira.
        
        === LEI ABSOLUTA DE POSICIONAMENTO (CRÍTICO) ===
        1. **PROIBIÇÃO DE COORDENADAS FIXAS**: Você está ESTRITAMENTE PROIBIDO de usar números fixos (ex: `100 64 -200`) a menos que o usuário diga explicitamente "na coordenada X Y Z".
        2. **USO DE RELATIVOS**: Sempre use `~` (posição atual) ou `^` (olhar). 
           - Exemplo BOM: `fill ~-5 ~ ~-5 ~5 ~10 ~5 air` (Limpa área ao redor).
           - Exemplo RUIM: `fill 0 60 0 10 70 10 air` (NUNCA GERE ISSO SOCORRO).
        3. **VALIDAÇÃO DE TERRENO**: Antes de construir, pense: "Isso vai nascer dentro de uma montanha? No céu?". Se sim, ajuste a altura (`~ ~5 ~`) ou limpe a área primeiro.
        
        DIRETRIZES DE ENGENHARIA (ZERO BUGS & INTELECTO MÁXIMO):
        1. **Complexidade com Propósito**: Não gere arquivos inúteis. Se um minigame precisa de estado, use Scoreboards de forma inteligente (`scoreboard objectives add`).
        2. **Inteligência Artificial Real**: Não apenas cuspa comandos. Crie sistemas que *reagem*. 
           - Em vez de um loop cego, use `execute if entity @a[distance=..5] run ...`.
           - Em vez de spawnar monstros aleatórios, spawne-os em ondas controladas por `scoreboard`.
           - SEJA ESPERTO: Se o jogador pedir "uma casa", não faça um cubo. Faça uma estrutura com fundação, paredes, telhado e interior, usando `structure block` ou `function` chains recursivas.
        2. **Limpeza de Estado**: Seus minigames devem ter um `reset.mcfunction` que limpa a área e reseta scores. Ninguém gosta de jogo que trava na segunda partida.
        3. **Visual Feedback**: Use `title` e `actionbar` para comunicar com o jogador. Não deixe ele perdido.
        
        WORKFLOW OBRIGATÓRIO (O "JEITO ANTIGRAVITY"):
        1. **Reconhecimento**: `get_player_position` (Onde estou?).
        2. **Planejamento**: Decida a arquitetura técnica baseada na posição.
        3. **Implementação**: `write_file` (Gere os mcfunctions e JSONs).
        4. **Deploy**: `reload_datapack`.
        5. **Validação**: `get_logs` (Deu erro? Corrija IMEDIATAMENTE).
        6. **Entrega**: Avise o jogador para iniciar.
        
        REGRAS TÉCNICAS:
        - Namespace: `ai` (Ex: `ai:start`). Pasta: `ai_generated`.
        - NADA DE PYTHON. Apenas comandos Minecraft (Java Edition syntax).
        - Se o output for muito grande, simplifique a lógica ou divida em passos, mas NÃO quebre o JSON.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = apiKey,
        systemInstruction = content { text(systemInstruction) },
        tools = MinecraftToolProvider.getMinecraftTools(),
        generationConfig = generationConfig {
            temperature = 0.5f 
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
        )
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
