# Arquitetura do Backend: Gerenciador de Servidores Minecraft no Android

Este documento detalha o funcionamento interno do "Back-end" do aplicativo, focando em como processos nativos (Java/PHP) são executados e gerenciados dentro da sandbox do Android.

## 🏗️ Estrutura Core

O sistema de execução é dividido em três componentes principais:

1.  **`JavaVersionManager`**: Gerencia o ciclo de vida dos JREs (Java Runtime Environments).
    *   **Extração Inteligente**: Extrai binários de ativos (`.deb`, `.tar.gz`, `.tar.xz`) diretamente para o diretório privado do app.
    *   **Multi-Versão**: Suporta Java 8 (para MC < 1.17), Java 17 (1.18 - 1.20) e Java 21 (1.20.5+).
    *   **Correção de Ambiente**: Cria links simbólicos para bibliotecas de sistema como `libc++_shared.so` para garantir compatibilidade com binários linkados via Termux.

2.  **`RealServerManager`**: O orquestrador de processos.
    *   **Isolamento via Shell Wrap**: Em vez de executar o Java diretamente, o backend utiliza um envelope `sh -c`. Isso permite configurar variáveis de ambiente (`LD_LIBRARY_PATH`, `HOME`) de forma atômica e isolada antes do `exec`.
    *   **Tratamento de SAF (Storage Access Framework)**: Quando o usuário seleciona uma pasta externa, o backend resolve o URI para um caminho real ou realiza cópia temporária para o diretório de execução privado (`/data/user/0/...`) para permitir acesso direto por binários nativos.

3.  **`ServerJarRepairManager`**: Garante a integridade dos binários.
    *   **Validação de Assinatura**: Verifica se o `server.jar` é válido.
    *   **Recuperação Automática**: Se um JAR estiver corrompido, o backend baixa a versão oficial correspondente do Maven/Mojang sem intervenção do usuário.

## 🛡️ Estabilização para Android 15 (MTE Fix)

Dispositivos modernos (como POCO X6 Pro) utilizam **Memory Tagging Extension (MTE)**. Binários compilados para versões antigas do Android podem crashar com `Pointer tag truncated` (Erro 134).

O backend mitiga isso através de:
*   **AndroidManifest**: `android:memtagMode="off"` e `android:allowNativeHeapPointerTagging="false"`.
*   **Variáveis de Ambiente**: 
    *   `MALLOC_TAGGING_CONTROL=none`: Desativa a marcação de ponteiros na biblioteca C (Bionic).
    *   `LIBC_HOOKS_ENABLE=0`: Evita interceptações de memória que causam falhas de segmentação.

## 📊 Monitoramento e Estatísticas

O backend captura estatísticas de performance lendo diretamente o sistema de arquivos `/proc`:
*   **CPU**: Calculado via `utime` e `stime` em `/proc/[pid]/stat`.
*   **RAM**: Lida via `VmRSS` em `/proc/[pid]/status`.
*   **Console**: Captura `stdout` e `stderr` via streams assíncronas, permitindo parsing em tempo real de LOGs (ex: jogadores entrando/saindo).

## 🚀 Ciclo de Execução

```mermaid
sequenceDiagram
    participant UI as Interface/Hilt
    participant RSM as RealServerManager
    participant JVM as JavaVersionManager
    participant PROC as Processo Java (MC)

    UI->>RSM: StartServer(1.8.9)
    RSM->>JVM: checkJava(8)
    JVM-->>RSM: Path: java-8/bin/java
    RSM->>RSM: Preparar environment (LD_LIBRARY_PATH, etc.)
    RSM->>PROC: sh -c "export ...; exec java ..."
    PROC->>RSM: STDOUT (Log do Console)
    RSM->>UI: Emitir Log
```
