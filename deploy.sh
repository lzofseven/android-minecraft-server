#!/bin/bash
# ============================================
# Rataria MC Deploy v2.0 - Universal Version
# ============================================

set -e

VERSION="${1:-1.21}"
ADB_TARGET="${2:-192.168.100.6:44339}"
SERVER_TYPE="${3:-paper}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$SCRIPT_DIR/build"
MCSERVER_DIR="$WORK_DIR/mcserver"

echo "============================================"
echo "🐀 Rataria MC Deploy v2.0"
echo "============================================"
echo "   Versão:  $VERSION"
echo "   Tipo:    $SERVER_TYPE"
echo "   Alvo:    $ADB_TARGET"
echo "============================================"
echo ""

# Limpar build anterior
rm -rf "$MCSERVER_DIR"
mkdir -p "$MCSERVER_DIR"/{bin,lib,conf,plugins,tmp,cache}

# Calcular versão do Java baseado na versão MC
MAJOR=$(echo "$VERSION" | cut -d. -f1)
MINOR=$(echo "$VERSION" | cut -d. -f2)
VERSION_NUM=$((MAJOR * 100 + MINOR))

# Java version selection:
# - MC 1.18+  = Java 21
# - MC 1.17   = Java 17
# - MC 1.16.5 e anteriores = Java 17 (melhor compatibilidade)
if [ "$VERSION_NUM" -ge 118 ]; then
    JAVA_VERSION=21
elif [ "$VERSION_NUM" -ge 117 ]; then
    JAVA_VERSION=17
else
    JAVA_VERSION=17
fi

# ============================================
# Etapa 1: Preparar Java
# ============================================
echo "📦 [1/4] Preparando ambiente Java $JAVA_VERSION..."
bash "$SCRIPT_DIR/scripts/setup_java.sh" "$WORK_DIR" "$MCSERVER_DIR" "$JAVA_VERSION"

# ============================================
# Etapa 2: Baixar Server
# ============================================
echo ""
echo "📥 [2/4] Baixando $SERVER_TYPE $VERSION..."
bash "$SCRIPT_DIR/scripts/get_server.sh" "$VERSION" "$MCSERVER_DIR" "$SERVER_TYPE"

# ============================================
# Etapa 3: Conectar e enviar via ADB
# ============================================
echo ""
echo "📲 [3/4] Enviando para o Android..."
adb connect "$ADB_TARGET" 2>/dev/null || true
sleep 1

# Verificar conexão
if ! adb devices | grep -q "$ADB_TARGET"; then
    echo "   ❌ Não foi possível conectar em $ADB_TARGET"
    echo "   Verifique se a depuração wireless está ativa."
    exit 1
fi

adb shell "rm -rf /data/local/tmp/mcserver" 2>/dev/null || true
adb push "$MCSERVER_DIR" /data/local/tmp/
adb shell "chmod -R 755 /data/local/tmp/mcserver/bin /data/local/tmp/mcserver/lib"

# ============================================
# Etapa 4: Iniciar servidor
# ============================================
echo ""
echo "🚀 [4/4] Iniciando servidor..."

# Calcular memória baseado na versão
MAJOR=$(echo "$VERSION" | cut -d. -f1)
MINOR=$(echo "$VERSION" | cut -d. -f2)
VERSION_NUM=$((MAJOR * 100 + MINOR))

if [ "$VERSION_NUM" -ge 118 ]; then
    MEMORY="1500M"
    echo "   (Versão moderna - usando 1.5GB RAM)"
elif [ "$VERSION_NUM" -ge 112 ]; then
    MEMORY="1G"
    echo "   (Versão intermediária - usando 1GB RAM)"
else
    MEMORY="768M"
    echo "   (Versão antiga - usando 768MB RAM)"
fi

echo "   Pressione Ctrl+C para parar"
echo ""
echo "============================================"

# Criar tmp directory no device
adb shell "mkdir -p /data/local/tmp/mcserver/tmp"

# Java agent pode falhar em versões intermediárias
JAVA_OPTS="-Xmx$MEMORY -Djava.io.tmpdir=./tmp"

# Bypass de versão Java para todas as versões pré-1.18
if [ "$VERSION_NUM" -lt 118 ]; then
    JAVA_OPTS="$JAVA_OPTS -Dpaperclip.bypass-java-check=true"
fi

# Iniciar servidor Java e Agente Playit
adb shell "cd /data/local/tmp/mcserver && \
    chmod +x playit-agent && \
    (./playit-agent > playit.log 2>&1 &) && \
    export LD_LIBRARY_PATH=./lib && \
    ./bin/java $JAVA_OPTS -jar server.jar nogui"
