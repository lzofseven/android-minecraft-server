package com.lzofseven.mcserver.core.ai

import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool

object MinecraftToolProvider {

    fun getMinecraftTools(): List<Tool> {
        return listOf(
            Tool(
                functionDeclarations = listOf(
                    FunctionDeclaration(
                        name = "write_file",
                        description = "Cria ou sobrescreve um arquivo (ex: .mcfunction, pack.mcmeta) no diretório do servidor ou datapack.",
                        parameters = listOf(
                            Schema.str("path", "Caminho relativo ao mundo do servidor ou absoluto."),
                            Schema.str("content", "Conteúdo completo do arquivo.")
                        ),
                        requiredParameters = listOf("path", "content")
                    ),
                    FunctionDeclaration(
                        name = "read_file",
                        description = "Lê o conteúdo de um arquivo existente para entender a lógica atual ou ler logs.",
                        parameters = listOf(
                            Schema.str("path", "Caminho do arquivo a ser lido.")
                        ),
                        requiredParameters = listOf("path")
                    ),
                    FunctionDeclaration(
                        name = "list_files",
                        description = "Lista arquivos em um diretório para entender a estrutura de pastas do servidor ou datapack.",
                        parameters = listOf(
                            Schema.str("path", "Diretório a ser listado.")
                        ),
                        requiredParameters = listOf("path")
                    ),
                    FunctionDeclaration(
                        name = "run_command",
                        description = "Executa um comando de console no Minecraft via RCON.",
                        parameters = listOf(
                            Schema.str("command", "Comando sem a barra inicial.")
                        ),
                        requiredParameters = listOf("command")
                    ),
                    FunctionDeclaration(
                        name = "get_logs",
                        description = "Recupera as últimas linhas do log do servidor (latest.log).",
                        parameters = emptyList(),
                        requiredParameters = emptyList()
                    ),
                    FunctionDeclaration(
                        name = "get_server_status",
                        description = "Retorna informações sobre o status atual do servidor.",
                        parameters = emptyList(),
                        requiredParameters = emptyList()
                    ),
                    FunctionDeclaration(
                        name = "extract_archive",
                        description = "Extrai um arquivo comprimido (.tar.gz, .zip) para uma pasta de destino.",
                        parameters = listOf(
                            Schema.str("path", "Caminho do arquivo comprimido."),
                            Schema.str("destination", "Pasta de destino para a extração.")
                        ),
                        requiredParameters = listOf("path", "destination")
                    ),
                    FunctionDeclaration(
                        name = "get_player_position",
                        description = "Retorna a posição (X, Y, Z) de um jogador online. Use para saber onde construir.",
                        parameters = listOf(
                            Schema.str("player_name", "Nome do jogador.")
                        ),
                        requiredParameters = listOf("player_name")
                    ),
                    FunctionDeclaration(
                        name = "reload_datapack",
                        description = "Recarrega todos os datapacks e retorna se houve erros de sintaxe nos logs. SEMPRE use após criar/editar arquivos .mcfunction.",
                        parameters = emptyList(),
                        requiredParameters = emptyList()
                    ),
                    FunctionDeclaration(
                        name = "search_block_id",
                        description = "Busca o ID correto de um bloco no Minecraft. Use quando não tiver certeza do nome exato (ex: 'vidro vermelho' -> 'red_stained_glass').",
                        parameters = listOf(
                            Schema.str("query", "Nome aproximado do bloco em português ou inglês.")
                        ),
                        requiredParameters = listOf("query")
                    ),
                    FunctionDeclaration(
                        name = "save_memory",
                        description = "Salva na memória uma construção ou informação importante para lembrar depois. Use SEMPRE após criar algo significativo (casas, minigames, estruturas).",
                        parameters = listOf(
                            Schema.str("name", "Nome descritivo da construção/memória (ex: 'Casa do jogador', 'Arena Color Run')."),
                            Schema.str("location", "Coordenadas X, Y, Z da construção (ex: '100, 64, -200'). Opcional."),
                            Schema.str("description", "Descrição ou comandos usados para criar a construção.")
                        ),
                        requiredParameters = listOf("name", "description")
                    ),
                    FunctionDeclaration(
                        name = "recall_memory",
                        description = "Recupera as memórias salvas sobre construções anteriores. Use para lembrar o que já foi feito no mundo.",
                        parameters = listOf(
                            Schema.int("limit", "Quantas memórias recentes retornar (padrão: 5).")
                        ),
                        requiredParameters = emptyList()
                    ),
                    FunctionDeclaration(
                        name = "get_server_info",
                        description = "Retorna detalhes técnicos do servidor (versão, dificuldade, gamemode, motd) lendo o arquivo server.properties.",
                        parameters = emptyList(),
                        requiredParameters = emptyList()
                    )
                )
            )
        )
    }
}
