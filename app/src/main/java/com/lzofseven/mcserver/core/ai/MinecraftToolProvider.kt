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
                    )
                )
            )
        )
    }
}
