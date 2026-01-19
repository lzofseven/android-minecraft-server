#!/bin/bash
# ============================================
# Rataria MC Deploy - Script Principal
# ============================================

set -e

VERSION="${1:-1.21}"
ADB_TARGET="${2:-192.168.100.6:44339}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$SCRIPT_DIR/build"
MCSERVER_DIR="$WORK_DIR/mcserver"

echo "🐀 Rataria MC Deploy v1.0"
echo "   Versão: $VERSION"
echo "   Alvo:   $ADB_TARGET"
echo ""

# Criar diretórios
mkdir -p "$MCSERVER_DIR"/{bin,lib,conf,plugins,tmp}

# Etapa 1: Preparar Java
echo "📦 [1/4] Preparando ambiente Java..."
bash "$SCRIPT_DIR/scripts/setup_java.sh" "$WORK_DIR" "$MCSERVER_DIR"

# Etapa 2: Baixar PaperMC
echo "📥 [2/4] Baixando PaperMC $VERSION..."
bash "$SCRIPT_DIR/scripts/get_paper.sh" "$VERSION" "$MCSERVER_DIR"

# Etapa 3: Conectar e enviar via ADB
echo "📲 [3/4] Enviando para o Android..."
adb connect "$ADB_TARGET"
adb shell "rm -rf /data/local/tmp/mcserver" || true
adb push "$MCSERVER_DIR" /data/local/tmp/
adb shell "chmod -R 755 /data/local/tmp/mcserver/bin /data/local/tmp/mcserver/lib"

# Etapa 4: Iniciar servidor
echo "🚀 [4/4] Iniciando servidor..."
echo "   (Pressione Ctrl+C para parar)"
echo ""
adb shell "cd /data/local/tmp/mcserver && \
    export LD_LIBRARY_PATH=./lib && \
    ./bin/java -Xmx1G -Djava.io.tmpdir=./tmp -jar server.jar nogui"
