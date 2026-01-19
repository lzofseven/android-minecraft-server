#!/bin/bash
# ============================================
# Get PaperMC + Playit Plugin
# ============================================

VERSION="$1"
MCSERVER_DIR="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Baixar Playit Plugin
PLAYIT_URL="https://github.com/playit-cloud/playit-minecraft-plugin/releases/latest/download/playit-minecraft-plugin.jar"
if [ ! -f "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" ]; then
    echo "   Baixando Playit Plugin..."
    wget -q -O "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" "$PLAYIT_URL"
fi

# Criar eula.txt
echo "eula=true" > "$MCSERVER_DIR/eula.txt"

# Determinar versão major (para decidir se precisa de patch local)
MAJOR_VERSION=$(echo "$VERSION" | cut -d. -f1,2 | sed 's/\.//')

# Baixar PaperMC
echo "   Baixando PaperMC $VERSION..."
PAPER_API="https://api.papermc.io/v2/projects/paper/versions/$VERSION"
BUILD=$(curl -s "$PAPER_API" | grep -oP '"builds":\[\K[^\]]+' | tr ',' '\n' | tail -1)

if [ -z "$BUILD" ]; then
    echo "   ⚠️  Versão $VERSION não encontrada no PaperMC. Tentando fallback..."
    # Tenta versões como 1.8.8, 1.8.9, etc.
    PAPER_API="https://api.papermc.io/v2/projects/paper/versions/$VERSION"
    BUILD=$(curl -s "$PAPER_API/builds" | grep -oP '"build":\s*\K\d+' | tail -1)
fi

PAPER_JAR_URL="https://api.papermc.io/v2/projects/paper/versions/$VERSION/builds/$BUILD/downloads/paper-$VERSION-$BUILD.jar"
wget -q -O "$MCSERVER_DIR/paper.jar" "$PAPER_JAR_URL" 2>/dev/null

# Verificar se precisa patch local (versões < 1.12 têm problema de download)
if [ "$MAJOR_VERSION" -lt 112 ]; then
    echo "   ⚠️  Versão antiga detectada. Aplicando patch local..."
    
    # Baixar vanilla server via Python
    python3 "$SCRIPT_DIR/download_vanilla.py" "$VERSION" "$MCSERVER_DIR/cache"
    
    # Rodar PaperClip para gerar o jar patcheado
    cd "$MCSERVER_DIR"
    java -jar paper.jar >/dev/null 2>&1 || true
    
    # Usar o jar patcheado
    if [ -f "cache/patched.jar" ]; then
        mv cache/patched.jar server.jar
    else
        mv paper.jar server.jar
    fi
else
    mv "$MCSERVER_DIR/paper.jar" "$MCSERVER_DIR/server.jar"
fi

echo "   ✅ PaperMC $VERSION pronto."
