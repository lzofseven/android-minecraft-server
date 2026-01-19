# 🤖 Manual Completo para IA - Servidor Minecraft no Android

Este documento contém todas as instruções necessárias para uma IA executar o deploy de um servidor Minecraft em qualquer versão no Android.

---

## 📋 Sumário

1. [Execução Rápida](#execução-rápida)
2. [Versões Suportadas](#versões-suportadas)
3. [Tipos de Servidor](#tipos-de-servidor)
4. [Execução em Background](#execução-em-background)
5. [Troubleshooting](#troubleshooting)

---

## 🚀 Execução Rápida

```bash
# Clone o repositório
git clone https://github.com/lzofseven/android-minecraft-server.git
cd android-minecraft-server

# Deploy padrão (Paper, última versão)
./deploy.sh 1.21 IP:PORTA

# Com tipo específico
./deploy.sh 1.20.4 192.168.100.6:44339 paper
./deploy.sh 1.20.4 192.168.100.6:44339 fabric
./deploy.sh 1.20.4 192.168.100.6:44339 purpur
./deploy.sh 1.8.9 192.168.100.6:44339 vanilla
```

---

## 📊 Versões Suportadas

| Faixa | Exemplos | RAM Recomendada | Notas |
|-------|----------|-----------------|-------|
| 1.21+ | 1.21, 1.21.1 | 1.5GB+ | Java 21 obrigatório |
| 1.18-1.20 | 1.18.2, 1.19.4, 1.20.4 | 1GB+ | Java 17+ |
| 1.12-1.17 | 1.12.2, 1.16.5 | 1GB | Java 8-16 |
| 1.8-1.11 | 1.8.8, 1.8.9, 1.11.2 | 768MB | Requer patch local |

### ⚠️ Versões Antigas (< 1.12)
Para versões antigas, o script automaticamente:
1. Baixa o PaperMC (que falharia no download do vanilla)
2. Baixa o vanilla server manualmente via API Mojang
3. Aplica o patch localmente antes de enviar ao Android

---

## 🎮 Tipos de Servidor

### Paper (Padrão)
```bash
./deploy.sh 1.20.4 IP:PORTA paper
```
- Melhor performance
- Plugins Bukkit/Spigot compatíveis
- Recomendado para a maioria dos casos

### Vanilla
```bash
./deploy.sh 1.20.4 IP:PORTA vanilla
```
- Servidor oficial Mojang
- Sem modificações
- Para puristas

### Fabric
```bash
./deploy.sh 1.20.4 IP:PORTA fabric
```
- Suporte a mods Fabric
- Requer mods no lado do cliente também
- Coloque mods na pasta `mods/` antes do deploy

### Purpur
```bash
./deploy.sh 1.20.4 IP:PORTA purpur
```
- Fork do Paper com mais otimizações
- Configurações extras de gameplay
- Compatível com plugins Paper

---

## 🔄 Execução em Background

Para manter o servidor rodando após fechar o terminal:

```bash
adb shell "cd /data/local/tmp/mcserver && \
    nohup sh -c 'export LD_LIBRARY_PATH=./lib && ./bin/java -Xmx1G -Djava.io.tmpdir=./tmp -jar server.jar nogui' \
    > server.log 2>&1 &"
```

### Verificar Status
```bash
adb shell "ps aux | grep java"
```

### Ver Logs
```bash
adb shell "tail -100 /data/local/tmp/mcserver/server.log"
```

### Obter Link Playit.gg
```bash
adb shell "grep -i 'playit.gg\|joinmc.link' /data/local/tmp/mcserver/server.log | tail -1"
```

### Parar Servidor
```bash
adb shell "pkill -f 'java.*server.jar'"
```

---

## 🔧 Troubleshooting

### Erro: "Versão não encontrada"
- Verifique se a versão existe no PaperMC: https://papermc.io/downloads
- Use `vanilla` como fallback: `./deploy.sh 1.X.X IP:PORTA vanilla`

### Erro: "ADB connection failed"
- Verifique se a depuração wireless está ativa
- Confirme o IP:Porta nas configurações do Android
- Tente: `adb kill-server && adb start-server`

### Erro: "Java not found" ou "libjli.so"
- O script `setup_java.sh` pode ter falhado
- Verifique conexão com internet
- Delete `build/` e tente novamente

### Servidor fecha sozinho
- Aumente a RAM: edite `deploy.sh` linha `MEMORY=`
- Verifique se há espaço no Android: `adb shell df -h`

### Playit não gera link
- Aguarde 30-60 segundos após "Done!"
- Verifique se o plugin foi copiado: `ls build/mcserver/plugins/`
- Veja logs: `adb shell "grep playit /data/local/tmp/mcserver/logs/latest.log"`

---

## 💡 Dicas para IA

1. **Sempre verifique a conexão ADB antes de tudo**
2. **Para versões antigas, confirme que o patch foi aplicado**
3. **Se falhar, tente com `vanilla` antes de reportar erro**
4. **O link do Playit aparece ~30s após "Done!" no log**
5. **Use background mode se o usuário quiser fechar o terminal**
