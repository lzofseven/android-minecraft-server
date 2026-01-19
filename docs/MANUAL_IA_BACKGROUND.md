# 🤖 Manual para IA: Servidor Persistente (Background)

Este documento instrui a IA a iniciar o servidor Minecraft de forma que ele continue rodando mesmo após fechar o terminal ADB.

---

## 🎯 Objetivo

Iniciar o servidor no Android de forma **desacoplada** do terminal, para que:
- O usuário possa fechar o terminal do PC
- O servidor continue rodando no celular
- O processo sobreviva à desconexão do ADB

---

## 📋 Comando de Execução Persistente

Use este comando em vez do comando padrão:

```bash
adb shell "cd /data/local/tmp/mcserver && \
    nohup sh -c 'export LD_LIBRARY_PATH=./lib && ./bin/java -Xmx1G -Djava.io.tmpdir=./tmp -jar server.jar nogui' \
    > server.log 2>&1 &"
```

### Explicação:
- `nohup`: Impede que o processo seja encerrado quando o terminal fecha
- `&`: Envia o processo para background
- `> server.log 2>&1`: Redireciona toda saída para um arquivo de log

---

## 📊 Verificar se o Servidor está Rodando

```bash
adb shell "ps aux | grep java"
```

Ou verifique o log:
```bash
adb shell "tail -50 /data/local/tmp/mcserver/server.log"
```

---

## 🔍 Obter Link do Playit.gg

Após iniciar em background, aguarde ~30 segundos e leia o log:

```bash
adb shell "grep -i 'playit.gg\|joinmc.link' /data/local/tmp/mcserver/server.log"
```

---

## 🛑 Parar o Servidor

```bash
adb shell "pkill -f 'java.*server.jar'"
```

Ou de forma mais agressiva:
```bash
adb shell "killall java"
```

---

## 🔄 Script Alternativo (Método Completo)

Se `nohup` não estiver disponível no seu Android, use:

```bash
adb shell "cd /data/local/tmp/mcserver && \
    setsid sh -c 'export LD_LIBRARY_PATH=./lib && ./bin/java -Xmx1G -Djava.io.tmpdir=./tmp -jar server.jar nogui' \
    > server.log 2>&1 < /dev/null &"
```

---

## ⚠️ Notas Importantes

1. **Memória:** Certifique-se de que o celular tem RAM suficiente (recomendado 4GB+ de RAM total)
2. **Bateria:** Conecte o celular no carregador para evitar que ele desligue
3. **Tela:** Mantenha a tela ligada ou desative otimização de bateria para processos em background
4. **Rede:** Se usar 4G, verifique se a operadora não bloqueia portas de servidor
