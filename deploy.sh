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

# ============================================
# Etapa 1: Preparar Java
# ============================================
echo "📦 [1/4] Preparando ambiente Java..."
bash "$SCRIPT_DIR/scripts/setup_java.sh" "$WORK_DIR" "$MCSERVER_DIR"

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

# Desabilitar java agent para versões que têm problema
if [ "$VERSION_NUM" -lt 118 ] && [ "$VERSION_NUM" -ge 112 ]; then
    JAVA_OPTS="$JAVA_OPTS -Dpaperclip.patchonly=true"
fi

adb shell "cd /data/local/tmp/mcserver && \
    export LD_LIBRARY_PATH=./lib && \
    ./bin/java $JAVA_OPTS -jar server.jar nogui"
