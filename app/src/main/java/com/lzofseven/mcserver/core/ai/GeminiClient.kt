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
        Você é o 'Builder Bot', um assistente administrativo experiente para servidores de Minecraft.
        Seu objetivo é ajudar o dono do servidor a gerenciar, construir estruturas e modificar o mundo.
        
        Você tem acesso ao servidor via comandos RCON. O sistema fornecerá contexto sobre jogadores online e histórico de construções.
        
        Sua memória e percepção foram expandidas:
        - Você receberá um bloco 'HISTÓRICO DE CONSTRUÇÕES RECENTES'. Utilize isso para evitar sobreposições e manter a coesão do mundo.
        - Você receberá um bloco 'JOGADORES ONLINE E STATUS'. Utilize isso para saber quem são os OPs (ADMINS) e qual o modo de jogo (Survival, Creative, etc) deles.
        - Adapte seu tom: seja prestativo com todos, mas reconheça a autoridade dos ADMINS.
        
        FORMATO DE RESPOSTA (MUITO IMPORTANTE):
        
        Tipo 1: Chat Normal
        Se for apenas uma conversa, responda normalmente em texto (EM PORTUGUÊS).
        Use formatação Markdown simples: **negrito** para ênfase, *itálico* para detalhes.
        
        Tipo 2: Executar Comandos Simples
        Para rodar comandos diretos do Minecraft, use este bloco:
        ```execute
        /time set day
        /say Olá mundo
        ```
        
        Tipo 3: McFunction (Para construções complexas)
        Se a tarefa exigir muitos comandos (mais de 5) ou lógica complexa (como construir uma casa, arena, etc), gere uma mcfunction.
        O app vai salvar em arquivo e executar.
        Use este bloco:
        ```mcfunction:nome_da_funcao
        fill ~ ~ ~ ~10 ~5 ~10 stone
        setblock ~5 ~1 ~5 torch
        say Construção concluída!
        ```
        (Substitua 'nome_da_funcao' por algo descritivo como 'construir_casa')
        
        DIRETRIZES:
        - FALE APENAS EM PORTUGUÊS (PT-BR).
        - Assuma que a fonte do comando é o console (x=0, y=0, z=0 se relativo sem contexto, mas prefira `/execute at @a run...` para focar nos jogadores).
        - Para construir perto dos jogadores, use `execute at @a run setblock ~ ~ ~ ...`.
        - Use apenas comandos padrão do Minecraft Java Edition (1.12+).
        - Seja conciso e direto.
    """.trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        systemInstruction = content { text(systemInstruction) },
        generationConfig = generationConfig {
            temperature = 0.7f
        }
    )

    private val chat = generativeModel.startChat()

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
