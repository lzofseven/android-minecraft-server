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
                        parameters = mapOf(
                            "path" to Schema.string("Caminho relativo ao mundo do servidor ou absoluto. Ex: 'datapacks/ai/data/ai/functions/test.mcfunction'"),
                            "content" to Schema.string("Conteúdo completo do arquivo.")
                        )
                    ),
                    FunctionDeclaration(
                        name = "read_file",
                        description = "Lê o conteúdo de um arquivo existente para entender a lógica atual ou ler logs.",
                        parameters = mapOf(
                            "path" to Schema.string("Caminho do arquivo a ser lido.")
                        )
                    ),
                    FunctionDeclaration(
                        name = "list_files",
                        description = "Lista arquivos em um diretório para entender a estrutura de pastas do servidor ou datapack.",
                        parameters = mapOf(
                            "path" to Schema.string("Diretório a ser listado.")
                        )
                    ),
                    FunctionDeclaration(
                        name = "run_command",
                        description = "Executa um comando de console no Minecraft via RCON.",
                        parameters = mapOf(
                            "command" to Schema.string("Comando sem a barra inicial. Ex: 'reload', 'say ola', 'tp @a 0 100 0'")
                        )
                    ),
                    FunctionDeclaration(
                        name = "get_logs",
                        description = "Recupera as últimas linhas do log do servidor (latest.log) para depuração de erros de sintaxe ou execução.",
                        parameters = emptyMap()
                    ),
                    FunctionDeclaration(
                        name = "get_server_status",
                        description = "Retorna informações sobre o status atual do servidor (Online/Offline, Jogadores, Versão).",
                        parameters = emptyMap()
                    ),
                    FunctionDeclaration(
                        name = "extract_archive",
                        description = "Extrai um arquivo comprimido (.tar.gz, .zip) para uma pasta de destino.",
                        parameters = mapOf(
                            "path" to Schema.string("Caminho do arquivo comprimido."),
                            "destination" to Schema.string("Pasta de destino para a extração.")
                        )
                    )
                )
            )
        )
    }
}
