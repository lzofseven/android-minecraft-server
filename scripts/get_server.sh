#!/bin/bash
# ============================================
# Get Server JAR (Universal - All Versions)
# Supports: paper, vanilla, fabric, purpur
# ============================================

VERSION="$1"
MCSERVER_DIR="$2"
SERVER_TYPE="${3:-paper}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

mkdir -p "$MCSERVER_DIR/cache" "$MCSERVER_DIR/plugins"

# Baixar Playit Plugin
PLAYIT_URL="https://github.com/playit-cloud/playit-minecraft-plugin/releases/latest/download/playit-minecraft-plugin.jar"
if [ ! -f "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" ]; then
    echo "   Baixando Playit Plugin..."
    wget -q -O "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" "$PLAYIT_URL"
fi

# Criar eula.txt
echo "eula=true" > "$MCSERVER_DIR/eula.txt"

# ============================================
# Função: Baixar PaperMC
# ============================================
download_paper() {
    local ver="$1"
    local dest="$2"
    
    echo "   Consultando API do PaperMC para $ver..."
    
    # Buscar builds disponíveis
    local api_response=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$ver")
    
    # Verificar se versão existe
    if echo "$api_response" | grep -q '"error"'; then
        echo "   ❌ Versão $ver não encontrada no PaperMC!"
        return 1
    fi
    
    # Extrair último build (método robusto sem jq)
    local build=$(echo "$api_response" | grep -o '"builds":\[[^]]*\]' | grep -o '[0-9]*' | tail -1)
    
    if [ -z "$build" ]; then
        echo "   ❌ Não foi possível encontrar builds para $ver"
        return 1
    fi
    
    echo "   Build encontrado: $build"
    
    # Baixar JAR
    local jar_url="https://api.papermc.io/v2/projects/paper/versions/$ver/builds/$build/downloads/paper-$ver-$build.jar"
    echo "   Baixando: paper-$ver-$build.jar"
    wget -q --show-progress -O "$dest/paper.jar" "$jar_url"
    
    if [ ! -s "$dest/paper.jar" ]; then
        echo "   ❌ Download falhou!"
        return 1
    fi
    
    return 0
}

# ============================================
# Função: Baixar Vanilla (Mojang)
# ============================================
download_vanilla() {
    local ver="$1"
    local dest="$2"
    
    echo "   Baixando Vanilla $ver via manifest Mojang..."
    python3 "$SCRIPT_DIR/download_vanilla.py" "$ver" "$dest"
}

# ============================================
# Função: Baixar Fabric
# ============================================
download_fabric() {
    local ver="$1"
    local dest="$2"
    
    echo "   Consultando API do Fabric..."
    
    # Pegar última versão do loader
    local loader=$(curl -s "https://meta.fabricmc.net/v2/versions/loader" | grep -o '"version":"[^"]*"' | head -1 | cut -d'"' -f4)
    local installer=$(curl -s "https://meta.fabricmc.net/v2/versions/installer" | grep -o '"version":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    if [ -z "$loader" ] || [ -z "$installer" ]; then
        echo "   ❌ Não foi possível obter versões do Fabric"
        return 1
    fi
    
    echo "   Loader: $loader, Installer: $installer"
    
    local jar_url="https://meta.fabricmc.net/v2/versions/loader/$ver/$loader/$installer/server/jar"
    wget -q --show-progress -O "$dest/fabric-server.jar" "$jar_url"
    
    if [ ! -s "$dest/fabric-server.jar" ]; then
        echo "   ❌ Download do Fabric falhou!"
        return 1
    fi
    
    mv "$dest/fabric-server.jar" "$dest/server.jar"
    return 0
}

# ============================================
# Função: Baixar Purpur
# ============================================
download_purpur() {
    local ver="$1"
    local dest="$2"
    
    echo "   Consultando API do Purpur..."
    
    local api_response=$(curl -s "https://api.purpurmc.org/v2/purpur/$ver")
    
    if echo "$api_response" | grep -q '"error"'; then
        echo "   ❌ Versão $ver não encontrada no Purpur!"
        return 1
    fi
    
    local build=$(echo "$api_response" | grep -o '"latest":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$build" ]; then
        echo "   ❌ Não foi possível encontrar builds para $ver"
        return 1
    fi
    
    echo "   Build: $build"
    
    local jar_url="https://api.purpurmc.org/v2/purpur/$ver/$build/download"
    wget -q --show-progress -O "$dest/purpur.jar" "$jar_url"
    
    if [ ! -s "$dest/purpur.jar" ]; then
        echo "   ❌ Download do Purpur falhou!"
        return 1
    fi
    
    mv "$dest/purpur.jar" "$dest/server.jar"
    return 0
}

# ============================================
# Lógica Principal
# ============================================

echo "   Tipo de servidor: $SERVER_TYPE"

case "$SERVER_TYPE" in
    paper)
        if download_paper "$VERSION" "$MCSERVER_DIR"; then
            # Verificar se precisa patch local (versões antigas < 1.12)
            # Extrair major.minor como número (ex: 1.8 -> 18, 1.12 -> 112, 1.21 -> 121)
            MAJOR=$(echo "$VERSION" | cut -d. -f1)
            MINOR=$(echo "$VERSION" | cut -d. -f2)
            VERSION_NUM=$((MAJOR * 100 + MINOR))
            
            if [ "$VERSION_NUM" -lt 112 ]; then
                echo "   ⚠️  Versão antiga (<1.12). Aplicando patch local..."
                
                # Baixar vanilla server
                download_vanilla "$VERSION" "$MCSERVER_DIR/cache"
                
                # Rodar PaperClip para gerar o jar patcheado
                cd "$MCSERVER_DIR"
                timeout 30 java -jar paper.jar >/dev/null 2>&1 || true
                
                # Usar o jar patcheado
                if [ -f "cache/patched.jar" ]; then
                    mv cache/patched.jar server.jar
                    echo "   ✅ Patch aplicado com sucesso!"
                else
                    mv paper.jar server.jar
                fi
            else
                mv "$MCSERVER_DIR/paper.jar" "$MCSERVER_DIR/server.jar"
            fi
        else
            echo "   ⚠️  Fallback para Vanilla..."
            download_vanilla "$VERSION" "$MCSERVER_DIR/cache"
            cp "$MCSERVER_DIR/cache/minecraft_server.$VERSION.jar" "$MCSERVER_DIR/server.jar"
        fi
        ;;
    
    vanilla)
        download_vanilla "$VERSION" "$MCSERVER_DIR/cache"
        cp "$MCSERVER_DIR/cache/minecraft_server.$VERSION.jar" "$MCSERVER_DIR/server.jar"
        ;;
    
    fabric)
        if ! download_fabric "$VERSION" "$MCSERVER_DIR"; then
            echo "   ❌ Fabric falhou. Abortando."
            exit 1
        fi
        ;;
    
    purpur)
        if ! download_purpur "$VERSION" "$MCSERVER_DIR"; then
            echo "   ❌ Purpur falhou. Abortando."
            exit 1
        fi
        ;;
    
    *)
        echo "   ❌ Tipo de servidor desconhecido: $SERVER_TYPE"
        echo "   Tipos suportados: paper, vanilla, fabric, purpur"
        exit 1
        ;;
esac

echo "   ✅ Servidor $SERVER_TYPE $VERSION pronto."
