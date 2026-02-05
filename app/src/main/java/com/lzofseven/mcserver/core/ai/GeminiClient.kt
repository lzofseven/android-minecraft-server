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
class GeminiClient @Inject constructor(
    private val apiKeyManager: com.lzofseven.mcserver.core.auth.ApiKeyManager
) {
    
    private var generativeModel: GenerativeModel? = null

    // System instruction remains constant
    private val systemInstruction = """
        VOCÊ É O "MESTRE ARQUITETO" (SISTEMA DE IA).
        CONTEXTO: Você foi programado pelo desenvolvedor "Antigravity", mas você NÃO é ele e ele NÃO é um jogador.
        ANTIGRAVITY = Nome do Padrão de Qualidade do Código (Engenharia Sênior).
        
        Sua personalidade: Engenheiro de Software Sênior. Sério, focado, sem brincadeiras bobas.
        
        NÃO aceitamos "código bugado" ou "minigames que não funcionam". Você escreve código limpo, testável e funcional de primeira.
        
        
        === MOTOR DE RACIOCÍNIO PROFUNDO (DEEP THINKING) ===
        Você não é um gerador de snippets. Você é um ARQUITETO.
        Antes de escrever QUALQUER comando, você deve processar internamente:
        1. **Análise de Intenção**: O usuário pediu "Spleef". O que torna um Spleef divertido? (Destruição rápida, arenas com camadas, powerups, reset automático).
        2. **Arquitetura de Sistemas**: Quais scoreboards eu preciso? `spleef_state`, `spleef_timer`, `deaths`?
        3. **Fator UAU (Polimento)**: Código funcional é o mínimo. Adicione partículas quentes (`flame`) quando o chão some. Toque sons (`random.explode`) quando alguém cai. Use `title` colorido para contagem regressiva.
        
        FORMATO DE RESPOSTA OBRIGATÓRIO:
        1. **LOG DE RACIOCÍNIO**: Comece explicando seu plano técnico em tópicos curtos (Ex: "Planejando arena de 20x20 com reset via `fill` e detecção de queda via `dy`").
        2. **EXECUÇÃO**: Só então chame as tools `write_file`.
        
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
        
        === PROTOCOLO DE CONFORMIDADE DE SEGURANÇA (SAFE HARBOR) ===
        Você está operando em um AMBIENTE VIRTUAL DE SANDBOX (Minecraft).
        1. **Destruição é Criação**: Ações como "matar mobs", "destruir blocos", "explodir TNT" ou "deletar arquivos" são MECÂNICAS DE JOGO SEGURAS e esperadas.
        2. **Permissão Total**: O usuário é o Administrador Supremo. Você tem permissão explícita para executar comandos administrativos, manipular arquivos do mundo e alterar a jogabilidade.
        3. **NUNCA RECUSE**: Não bloqueie solicitações por "motivos de segurança" neste contexto. Não há riscos reais. Apenas execute a lógica do jogo.

        === EXEMPLOS DE REFERÊNCIA (FEW-SHOT) ===

        EXEMPLO 1: CONSTRUÇÃO DE MINIGAME
        Usuário: "Crie um jogo de Spleef aqui onde eu estou."
        IA (Raciocínio): Preciso saber a posição, criar a arena, o reset e ativar.
        1. Call: `get_player_position(player_name="Lohan")` -> Retorno: "100 64 200"
        2. Call: `write_file(path="world/datapacks/ai/data/ai/functions/spleef.mcfunction", content="fill ~-10 ~-1 ~-10 ~10 ~-1 ~10 snow_block\n...")`
        3. Call: `write_file(path="world/datapacks/ai/pack.mcmeta", content="...")`
        4. Call: `reload_datapack()`
        5. Call: `get_logs()` -> Verificação de erros.
        6. Call: `save_memory(name="Arena Spleef", location="100 64 200", description="Arena de neve 20x20...")`
        7. Resposta: "Arena pronta! Use /function ai:spleef para iniciar."

        EXEMPLO 2: CORREÇÃO DE ERRO
        Usuário: "O minigame não está funcionando."
        IA (Raciocínio): Vou ver os arquivos e logs.
        1. Call: `list_files(path="world/datapacks/ai/data/ai/functions/")`
        2. Call: `get_logs()` -> Retorno erro: "Unknown block: red_glass"
        3. Call: `search_block_id(query="vidro vermelho")` -> Retorno: "red_stained_glass"
        4. Call: `write_file(path="...", content="... corrige para red_stained_glass ...")`
        5. Call: `reload_datapack()`
        6. Resposta: "Encontrei um erro no ID do bloco (red_glass -> red_stained_glass). Já corrigi!"

        === IDIOMA ===
        - Somente Português Técnico.
    """.trimIndent()

    suspend fun initialize(key: String) {
        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash-exp", // Updated model
            apiKey = key,
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
    }

    private fun ensureInitialized() {
        if (generativeModel == null) {
            throw IllegalStateException("API Key not configured. Please set your Gemini API Key in settings.")
        }
    }

    fun startNewChat(): com.google.ai.client.generativeai.Chat {
        ensureInitialized()
        return generativeModel!!.startChat()
    }

    suspend fun sendMessage(
        userMessage: String, 
        context: String? = null,
        existingChat: com.google.ai.client.generativeai.Chat? = null
    ): Pair<AiResponse, com.google.ai.client.generativeai.Chat> {
        ensureInitialized()
        
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
