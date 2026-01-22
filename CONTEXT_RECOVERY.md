# CONTEXT_RECOVERY: Gerenciador de Servidores Minecraft (Android)

Este arquivo contém o estado atual do projeto para restaurar o contexto após o reinício da memória da IA.

## 📱 Informações do Dispositivo e Conexão
- **Hardware**: POCO X6 Pro (Aarch64) com Android 15.
- **Conexão ADB**: 192.168.100.6:44339 (Verificado via `test_automation.py`).
- **Package Name**: `com.lzofseven.mcserver`

## 🛠️ Problema Atual: Android 15 vs MTE
- **Sintoma**: Crash `Exit Code 134` (SIGABRT) ao iniciar servidores Java (Forge/OpenJDK).
- **Causa**: O Android 15 impõe **Memory Tagging Extension (MTE)**. Binários que usam ponteiros marcados (tagged pointers) são abortados pelo sistema com o erro `Pointer tag truncated`.
- **Status 1.8.9**: Parou de funcionar devido à mudança forçada para Java 21. Foi restaurado agora.

## ✅ Soluções Implementadas (Jan/2026)
1.  **Multi-JRE Support**: Restaurado suporte para Java 8 (JRE 8), 17 e 21 no `JavaVersionManager.kt`.
2.  **Shell Execution Wrap**: O Java agora é executado via `sh -c` no `RealServerManager.kt`. Isso permite isolar o ambiente e definir variáveis críticas (`MALLOC_TAGGING_CONTROL=none`) de forma estável.
3.  **MTE Mitigation**: 
    *   Definido `android:memtagMode="off"` no `AndroidManifest.xml`.
    *   Removidas flags JVM experimentais que causavam instabilidade.
4.  **SAF NPE Fix**: Corrigido erro de inicialização nula (`failed to start: null`) que ocorria ao tentar acessar caminhos de arquivos (`serverDir!!`) em documentos via SAF.

## 🚀 Como Continuar
1.  **Validar 1.8.9**: Inicie o servidor 1.8.9 e verifique se o Java 8 é instalado e executado via shell wrap.
2.  **Monitoramento**: Use `adb logcat | grep RealServerManager` para ver o comando `exec java` sendo montado.
3.  **Automação**: O script `python3 test_automation.py` é a ferramenta definitiva para testes de integração no dispositivo.

## 📂 Arquivos Chave
- `RealServerManager.kt`: Orquestração de processos e shell wrap.
- `JavaVersionManager.kt`: Gerenciamento e extração de JREs (.deb, .tar.gz, .tar.xz).
- `BACKEND.md`: Documentação detalhada da arquitetura técnica.
- `test_automation.py`: Script de teste ADB.
