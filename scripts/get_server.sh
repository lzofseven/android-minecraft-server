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
# Baixar Playit Plugin
# ============================================
PLAYIT_URL="https://github.com/playit-cloud/playit-minecraft-plugin/releases/latest/download/playit-minecraft-plugin.jar"
if [ ! -f "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" ]; then
    echo "   Baixando Playit Plugin..."
    wget -q -O "$MCSERVER_DIR/plugins/playit-minecraft-plugin.jar" "$PLAYIT_URL"
fi

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
# Lógica Principal
# ============================================

# Verificar versão numérica
MAJOR=$(echo "$VERSION" | cut -d. -f1)
MINOR=$(echo "$VERSION" | cut -d. -f2)
VERSION_NUM=$((MAJOR * 100 + MINOR))

case "$SERVER_TYPE" in
    paper)
        if try_paper_versions "$VERSION" "$MCSERVER_DIR"; then
            ACTUAL_VERSION=$(cat "$MCSERVER_DIR/.paper_version" 2>/dev/null || echo "$VERSION")
            
            # Patch local para versões antes de 1.18 (Java agent não funciona no Android)
            if [ "$VERSION_NUM" -lt 118 ]; then
                echo "   ⚠️ Versão pré-1.18. Aplicando patch local (Android não suporta Java Agent)..."
                
                # Criar diretório cache se necessário
                mkdir -p "$MCSERVER_DIR/cache"
                
                # Para versões muito antigas (< 1.12), baixar vanilla primeiro (URL quebrada no paperclip)
                if [ "$VERSION_NUM" -lt 112 ]; then
                    echo "   ⬇️ Baixando vanilla server (necessário para 1.8.x-1.11.x)..."
                    python3 "$SCRIPT_DIR/download_vanilla.py" "$ACTUAL_VERSION" "$MCSERVER_DIR/cache"
                    
                    # Renomear para o formato esperado pelo paperclip
                    if [ -f "$MCSERVER_DIR/cache/minecraft_server.$ACTUAL_VERSION.jar" ]; then
                        mv "$MCSERVER_DIR/cache/minecraft_server.$ACTUAL_VERSION.jar" "$MCSERVER_DIR/cache/minecraft_server.jar"
                    fi
                fi
                
                # Rodar PaperClip localmente para gerar o jar patcheado
                cd "$MCSERVER_DIR"
                echo "   Executando PaperClip (pode demorar ~30s)..."
                timeout 120 java -jar paper.jar >/dev/null 2>&1 || true
                
                # Verificar se gerou o patched jar (diferentes versões usam nomes diferentes)
                if [ -f "cache/patched.jar" ]; then
                    mv cache/patched.jar server.jar
                    rm -f paper.jar
                    echo "   ✓ Patch aplicado (cache/patched.jar)"
                elif ls cache/patched_*.jar 1>/dev/null 2>&1; then
                    # Paper 1.8.x gera patched_X.X.X.jar
                    mv cache/patched_*.jar server.jar
                    rm -f paper.jar
                    echo "   ✓ Patch aplicado (patched_*.jar)"
                elif ls cache/mojang_*.jar 1>/dev/null 2>&1; then
                    # Algumas versões geram mojang_*.jar (o vanilla baixado)
                    # Neste caso usamos o paper.jar que já foi patcheado
                    mv paper.jar server.jar
                    echo "   ✓ Patch aplicado (paper.jar patcheado)"
                else
                    # Se não gerou nada reconhecível, mover o paper.jar
                    mv paper.jar server.jar
                    echo "   ⚠️ Usando paper.jar direto (patch pode falhar)"
                fi
            else
                mv "$MCSERVER_DIR/paper.jar" "$MCSERVER_DIR/server.jar"
            fi
        else
            echo "   ⚠️ Paper indisponível. Fallback para Vanilla..."
            download_vanilla "$VERSION" "$MCSERVER_DIR/cache"
        fi
        ;;
    
    vanilla)
        download_vanilla "$VERSION" "$MCSERVER_DIR/cache" || exit 1
        ;;
    
    fabric)
        if ! download_fabric "$VERSION" "$MCSERVER_DIR"; then
            echo "   ❌ Fabric não disponível para $VERSION"
            exit 1
        fi
        ;;
    
    purpur)
        if ! download_purpur "$VERSION" "$MCSERVER_DIR"; then
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
