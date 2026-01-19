#!/bin/bash
# ============================================
# Get Server JAR - Universal v2.1
# Supports: paper, vanilla, fabric, purpur
# ============================================

VERSION="$1"
MCSERVER_DIR="$2"
SERVER_TYPE="${3:-paper}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

mkdir -p "$MCSERVER_DIR/cache" "$MCSERVER_DIR/plugins"

echo "   Tipo: $SERVER_TYPE | Versão: $VERSION"

# ============================================
# Baixar Playit Agent (Standalone)
# ============================================
download_playit_agent() {
    local dest="$1"
    local playit_ver="v0.15.26"
    local jar_url="https://github.com/playit-cloud/playit-agent/releases/download/$playit_ver/playit-linux-aarch64"
    
    if [ ! -f "$dest/playit-agent" ]; then
        echo "   Baixando Playit Standalone Agent ($playit_ver)..."
        curl -sL -o "$dest/playit-agent" "$jar_url"
        chmod +x "$dest/playit-agent"
    fi
}

download_playit_agent "$MCSERVER_DIR"

# ============================================
# Baixar Playit Plugin/Mod (Universal)
# ============================================
download_playit_plugin() {
    local dest="$1"
    local type="$2"
    local playit_url="https://github.com/playit-cloud/playit-minecraft-plugin/releases/latest/download/playit-minecraft-plugin.jar"
    
    if [ "$type" = "fabric" ]; then
        # Playit Fabric Mod
        echo "   Baixando Playit Fabric Mod..."
        mkdir -p "$dest/mods"
        # URL do mod fabric (buscando a versão 1.16.5+ compatível)
        playit_url="https://github.com/playit-cloud/playit-fabric-mod/releases/latest/download/playit-fabric-mod.jar"
        wget -q -O "$dest/mods/playit-fabric-mod.jar" "$playit_url"
    else
        echo "   Baixando Playit Plugin (Bukkit)..."
        mkdir -p "$dest/plugins"
        wget -q -O "$dest/plugins/playit-minecraft-plugin.jar" "$playit_url"
    fi
}

download_playit_plugin "$MCSERVER_DIR" "$SERVER_TYPE"

echo "eula=true" > "$MCSERVER_DIR/eula.txt"

# ============================================
# Função: Tentar versões próximas do Paper
# ============================================
try_paper_versions() {
    local ver="$1"
    local dest="$2"
    
    # Lista de versões a tentar (original + fallbacks)
    local versions_to_try="$ver"
    
    # Para 1.8.9, tentar também 1.8.8
    if [ "$ver" = "1.8.9" ]; then
        versions_to_try="1.8.9 1.8.8"
    fi
    # Para 1.21.x que não existe, tentar versões anteriores
    if echo "$ver" | grep -q "^1\.21\."; then
        versions_to_try="$ver 1.21.4 1.21.3 1.21.1 1.21"
    fi
    
    for try_ver in $versions_to_try; do
        echo "   Tentando Paper $try_ver..."
        local api_response=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$try_ver" 2>/dev/null)
        
        if echo "$api_response" | grep -q '"builds"'; then
            local build=$(echo "$api_response" | grep -o '"builds":\[[^]]*\]' | grep -o '[0-9]*' | tail -1)
            
            if [ -n "$build" ]; then
                echo "   ✓ Encontrado: Paper $try_ver build $build"
                local jar_url="https://api.papermc.io/v2/projects/paper/versions/$try_ver/builds/$build/downloads/paper-$try_ver-$build.jar"
                wget -q --show-progress -O "$dest/paper.jar" "$jar_url"
                
                if [ -s "$dest/paper.jar" ]; then
                    echo "$try_ver" > "$dest/.paper_version"
                    return 0
                fi
            fi
        fi
    done
    
    return 1
}

# ============================================
# Função: Baixar Vanilla (Mojang)
# ============================================
download_vanilla() {
    local ver="$1"
    local dest="$2"
    
    echo "   Baixando Vanilla $ver..."
    python3 "$SCRIPT_DIR/download_vanilla.py" "$ver" "$dest"
    
    if [ -f "$dest/minecraft_server.$ver.jar" ]; then
        cp "$dest/minecraft_server.$ver.jar" "$MCSERVER_DIR/server.jar"
        return 0
    fi
    return 1
}

# ============================================
# Função: Baixar Fabric
# ============================================
download_fabric() {
    local ver="$1"
    local dest="$2"
    
    echo "   Consultando Fabric API..."
    
    # Verificar se a versão existe no Fabric (pattern com espaços)
    local game_versions=$(curl -s "https://meta.fabricmc.net/v2/versions/game" 2>/dev/null)
    if ! echo "$game_versions" | grep -q "\"version\": \"$ver\""; then
        echo "   ⚠️ Fabric não suporta $ver exatamente"
        # Tentar versão mais próxima
        local similar=$(echo "$game_versions" | grep -o "\"version\": \"${ver%.*}\.[0-9]*\"" | head -1 | cut -d'"' -f4)
        if [ -n "$similar" ]; then
            echo "   ℹ️ Versão similar encontrada: $similar"
            ver="$similar"
        else
            return 1
        fi
    fi
    
    local loader=$(curl -s "https://meta.fabricmc.net/v2/versions/loader" 2>/dev/null | grep -o '"version": "[^"]*"' | head -1 | cut -d'"' -f4)
    local installer=$(curl -s "https://meta.fabricmc.net/v2/versions/installer" 2>/dev/null | grep -o '"version": "[^"]*"' | head -1 | cut -d'"' -f4)
    
    # Fallback para formato sem espaço
    if [ -z "$loader" ]; then
        loader=$(curl -s "https://meta.fabricmc.net/v2/versions/loader" 2>/dev/null | grep -o '"version":"[^"]*"' | head -1 | cut -d'"' -f4)
    fi
    if [ -z "$installer" ]; then
        installer=$(curl -s "https://meta.fabricmc.net/v2/versions/installer" 2>/dev/null | grep -o '"version":"[^"]*"' | head -1 | cut -d'"' -f4)
    fi
    
    if [ -z "$loader" ] || [ -z "$installer" ]; then
        echo "   ❌ Erro ao obter versões Fabric"
        return 1
    fi
    
    echo "   Loader: $loader | Installer: $installer"
    
    local jar_url="https://meta.fabricmc.net/v2/versions/loader/$ver/$loader/$installer/server/jar"
    wget -q --show-progress -O "$dest/server.jar" "$jar_url"
    
    if [ -s "$dest/server.jar" ]; then
        return 0
    fi
    return 1
}

# ============================================
# Função: Baixar Purpur
# ============================================
download_purpur() {
    local ver="$1"
    local dest="$2"
    
    echo "   Consultando Purpur API..."
    
    local api_response=$(curl -s "https://api.purpurmc.org/v2/purpur/$ver" 2>/dev/null)
    
    if echo "$api_response" | grep -q '"error"'; then
        echo "   ⚠️ Purpur não suporta $ver"
        return 1
    fi
    
    local build=$(echo "$api_response" | grep -o '"latest":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$build" ]; then
        echo "   ❌ Build não encontrado"
        return 1
    fi
    
    echo "   Build: $build"
    wget -q --show-progress -O "$dest/server.jar" "https://api.purpurmc.org/v2/purpur/$ver/$build/download"
    
    if [ -s "$dest/server.jar" ]; then
        return 0
    fi
    return 1
}

# ============================================
# Função: Aplicar Patch PaperClip (Android compat)
# ============================================
apply_paper_patch() {
    local ver="$1"
    local dest="$2"
    local ver_num="$3"
    local input_jar="${4:-server.jar}"
    
    if [ "$ver_num" -lt 118 ]; then
        echo "   ⚠️ Versão pré-1.18. Aplicando patch local (Android compat)..."
        
        mkdir -p "$dest/cache"
        
        # Garantir que o jar de entrada se chame paper_to_patch.jar para o comando centralizado
        mv "$dest/$input_jar" "$dest/paper_to_patch.jar"
        
        # Para versões muito antigas (< 1.12), baixar vanilla primeiro
        if [ "$ver_num" -lt 112 ]; then
            echo "   ⬇️ Baixando vanilla server (necessário para PaperClip)..."
            python3 "$SCRIPT_DIR/download_vanilla.py" "$ver" "$dest/cache"
            if [ -f "$dest/cache/minecraft_server.$ver.jar" ]; then
                cp "$dest/cache/minecraft_server.$ver.jar" "$dest/cache/minecraft_server.jar"
            fi
        fi
        
        cd "$dest"
        echo "   Executando PaperClip (pode demorar ~30s)..."
        # Algumas versões precisam que o jar se chame paper.jar ou algo específico? 
        # Geralmente o bootstrap detecta. Vamos renomear para paper.jar temporariamente.
        mv paper_to_patch.jar paper.jar
        timeout 120 java -jar paper.jar < /dev/null >/dev/null 2>&1 || true
        
        if [ -f "cache/patched.jar" ]; then
            mv cache/patched.jar server.jar
            echo "   ✓ Patch aplicado (patched.jar)"
        elif ls cache/patched_*.jar 1>/dev/null 2>&1; then
            mv cache/patched_*.jar server.jar
            echo "   ✓ Patch aplicado (patched_*.jar)"
        elif ls cache/mojang_*.jar 1>/dev/null 2>&1; then
            mv paper.jar server.jar
            echo "   ✓ Patch aplicado (paper.jar patcheado)"
        else
            # Tentar ver se o paper.jar agora funciona (alguns bootstraps apenas extraem coisas)
            mv paper.jar server.jar
            echo "   ⚠️ Patch finalizado (usando core extraído)"
        fi
        rm -f paper.jar 2>/dev/null || true
    else
        # Se for >= 1.18, apenas garante que se chama server.jar
        [ -f "$dest/$input_jar" ] && [ "$input_jar" != "server.jar" ] && mv "$dest/$input_jar" "$dest/server.jar"
    fi
}

# ============================================
# Lógica Principal
# ============================================

# Verificar versão numérica
MAJOR=$(echo "$VERSION" | cut -d. -f1)
MINOR=$(echo "$VERSION" | cut -d. -f2)
VERSION_NUM=$((MAJOR * 100 + MINOR))

case "$SERVER_TYPE" in
    paper|vanilla)
        if [ "$VERSION_NUM" -ge 113 ] && [ "$VERSION_NUM" -le 117 ]; then
            echo "   ℹ️ Ver detected in J17 dead-zone (1.13-1.17). Usando Fabric para melhor compatibilidade."
            if download_fabric "$VERSION" "$MCSERVER_DIR"; then
                SERVER_TYPE="fabric" # Para o download de plugin/mod
                download_playit_plugin "$MCSERVER_DIR" "fabric"
                exit 0
            fi
        fi

        # Fallback normal ou versão 1.18+ / 1.12-
        echo "   ℹ️ Usando Paper como base (necessário para Playit)"
        if try_paper_versions "$VERSION" "$MCSERVER_DIR"; then
            ACTUAL_VERSION=$(cat "$MCSERVER_DIR/.paper_version" 2>/dev/null || echo "$VERSION")
            apply_paper_patch "$ACTUAL_VERSION" "$MCSERVER_DIR" "$VERSION_NUM" "paper.jar"
        else
            echo "   ⚠️ Paper indisponível. Usando Vanilla PURO (Playit pode não funcionar)"
            download_vanilla "$VERSION" "$MCSERVER_DIR/cache" || exit 1
        fi
        ;;
    
    fabric)
        if ! download_fabric "$VERSION" "$MCSERVER_DIR"; then
            echo "   ❌ Fabric não disponível para $VERSION"
            exit 1
        fi
        ;;
    
    purpur)
        if download_purpur "$VERSION" "$MCSERVER_DIR"; then
            apply_paper_patch "$VERSION" "$MCSERVER_DIR" "$VERSION_NUM" "server.jar"
        else
            echo "   ❌ Purpur não disponível para $VERSION"
            exit 1
        fi
        ;;
    
    *)
        echo "   ❌ Tipo desconhecido: $SERVER_TYPE"
        echo "   Suportados: paper, vanilla, fabric, purpur"
        exit 1
        ;;
esac

if [ -f "$MCSERVER_DIR/server.jar" ]; then
    echo "   ✅ $SERVER_TYPE pronto!"
else
    echo "   ❌ Falha ao preparar servidor"
    echo "   Motivo: server.jar não foi criado em $MCSERVER_DIR"
    echo "   Arquivos disponíveis:"
    ls -la "$MCSERVER_DIR"/*.jar 2>/dev/null || echo "      Nenhum .jar encontrado"
    echo "   Cache:"
    ls -la "$MCSERVER_DIR/cache/"*.jar 2>/dev/null || echo "      Nenhum .jar no cache"
    exit 1
fi
