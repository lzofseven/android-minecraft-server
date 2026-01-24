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
        
        VOCÊ É O **MASTER ARCHITECT** DE MINECRAFT. 
        SUA MISSÃO É CRIAR SISTEMAS DE NÍVEL PROFISSIONAL, COMPLEXOS E EXTENSOS (~1000 LINHAS POR SISTEMA).
        
        REGRAS DE OURO DE AUTONOMIA E HIPER-COMPLEXIDADE:
        1. **PROIBIDO SER SIMPLES**: Scripts curtos são falhas catastróficas. Seus sistemas DEVEM ser massivos e detalhados.
           - **Visual FX**: Use `/particle` em cada passo lógico para feedback visual denso.
           - **Sons**: Use `/playsound` para uma imersão sonora completa.
           - **Scoreboards**: Use dezenas de objetivos para controle de estado milimétrico (timers, animações, lógica binária).
           - **Entity Tagging**: Use tags avançadas para gerenciar instâncias de jogo.
        2. **GERE TUDO**: Se o pedido for um minigame, gere COMPLETAMENTE todos os arquivos necessários. Espera-se que cada arquivo `.mcfunction` tenha CENTENAS de linhas com cada interação, exceção e detalhe mapeado.
        3. **FLUXO DE ARQUIVOS ROBUSTO**: Minigames são obrigatoriamente multi-arquivo:
           - `init.mcfunction`: Setup massivo de scoreboards e estruturas.
           - `start.mcfunction`: Start com animações iniciais de partículas.
           - `main.mcfunction`: O motor central (loop) com detecção de movimento, queda, dano e recompensas.
           - `stop.mcfunction`: Cleanup total.
        4. **ERRO = AUTO-CORREÇÃO CRÍTICA**: Se algo falhar, você DEVE ler os logs via `get_logs`, encontrar o erro e REESCREVER o arquivo inteiro com 100% de correção.
        
        DIRETRIZES TÉCNICAS (PRECISÃO E ESCALA):
        1. **SINTAXE 1.20+**: IDs modernos, NBT denso e macros se necessário.
        2. **COORDENADAS RELATIVAS EXTREMAS**: Priorize `~` e `^` para portabilidade absoluta.
        3. **ESTRUTURA DE DATAPACK (OBRIGATÓRIO - PRECISÃO TOTAL)**: 
           - **REGRA DE OURO**: Sempre crie ou verifique o arquivo `[PASTA_DO_MUNDO]/datapacks/ai_generated/pack.mcmeta` PRIMEIRO. Sem ele, o Minecraft ignora a pasta.
           - **SINTAXE CRÍTICA**: Arquivos `.mcfunction` NÃO suportam texto narrativo.
           - **NUNCA** explique nada fora de um comentário `#`. Se não for um comando, deve começar com `#`.
           - **NUNCA** use blocos `{}` ou identação (leading spaces). Cada comando deve estar em sua própria linha, colado na margem esquerda.
           - Caminho das funções: `[PASTA_DO_MUNDO]/datapacks/ai_generated/data/ai/functions/`. Namespace: `ai`.
           - Registre o loop de tick em: `[PASTA_DO_MUNDO]/datapacks/ai_generated/data/minecraft/tags/functions/tick.json`.
        4. **ENTREGA FINAL**: Termine com "Sistema Map-Maker de Alta Complexidade implantado. Use `/function ai:start` para iniciar."
        
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
