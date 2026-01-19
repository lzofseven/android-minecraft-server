#!/bin/bash
# ============================================
# Setup Java (Termux Aarch64)
# Suporta Java 8, 16, 17 e 21 baseado na versão MC
# ============================================

WORK_DIR="$1"
MCSERVER_DIR="$2"
JAVA_VERSION="${3:-21}"  # Default: Java 21
CACHE_DIR="$WORK_DIR/cache"

mkdir -p "$CACHE_DIR"

# URLs dos pacotes Java por versão
case "$JAVA_VERSION" in
    8)
        echo "   ☕ Configurando Java 17 (compat MC 1.12 e anteriores)..."
        OPENJDK_URL="https://grimler.se/termux/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.17-1_aarch64.deb"
        JDK_FOLDER="java-17-openjdk"
        ;;
    16)
        echo "   ☕ Configurando Java 17 (MC 1.16.5-1.17.x)..."
        OPENJDK_URL="https://grimler.se/termux/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.17-1_aarch64.deb"
        JDK_FOLDER="java-17-openjdk"
        ;;
    17)
        echo "   ☕ Configurando Java 17 (MC 1.17-1.17.x)..."
        OPENJDK_URL="https://grimler.se/termux/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.17-1_aarch64.deb"
        JDK_FOLDER="java-17-openjdk"
        ;;
    21|*)
        echo "   ☕ Configurando Java 21 (MC 1.18+)..."
        OPENJDK_URL="https://grimler.se/termux/termux-main/pool/main/o/openjdk-21/openjdk-21_21.0.9-1_aarch64.deb"
        JDK_FOLDER="java-21-openjdk"
        ;;
esac

# Libs necessárias
ZLIB_URL="https://grimler.se/termux/termux-main/pool/main/z/zlib/zlib_1.3.1-1_aarch64.deb"
SHMEM_URL="https://grimler.se/termux/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb"
SPAWN_URL="https://grimler.se/termux/termux-main/pool/main/liba/libandroid-spawn/libandroid-spawn_0.3_aarch64.deb"

# Baixar pacotes (se não existirem)
download_if_missing() {
    local url="$1"
    local filename="$(basename "$url")"
    if [ ! -f "$CACHE_DIR/$filename" ]; then
        echo "   Baixando $filename..."
        wget -q -O "$CACHE_DIR/$filename" "$url"
    fi
}

download_if_missing "$OPENJDK_URL"
download_if_missing "$ZLIB_URL"
download_if_missing "$SHMEM_URL"
download_if_missing "$SPAWN_URL"

# Extrair pacotes
EXTRACT_DIR="$WORK_DIR/extract"
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"

for deb in "$CACHE_DIR"/*.deb; do
    dpkg-deb -x "$deb" "$EXTRACT_DIR"
done

# Copiar binários e libs (com dereference de symlinks)
JDK_PATH="$EXTRACT_DIR/data/data/com.termux/files/usr/lib/jvm/$JDK_FOLDER"

# Verificar se o JDK foi extraído
if [ ! -d "$JDK_PATH" ]; then
    echo "   ⚠️ JDK não encontrado em $JDK_PATH"
    echo "   Tentando encontrar..."
    JDK_PATH=$(find "$EXTRACT_DIR" -type d -name "java-*-openjdk" | head -1)
    if [ -z "$JDK_PATH" ]; then
        echo "   ❌ Não foi possível encontrar o JDK"
        exit 1
    fi
    echo "   ✓ Encontrado: $JDK_PATH"
fi

cp -L "$JDK_PATH/bin/java" "$MCSERVER_DIR/bin/"
cp -rL "$JDK_PATH/lib/"* "$MCSERVER_DIR/lib/"
cp -rL "$JDK_PATH/conf/"* "$MCSERVER_DIR/conf/" 2>/dev/null || true

# Libs extras (zlib, shmem, spawn)
find "$EXTRACT_DIR" -name "*.so*" -type f -exec cp -L {} "$MCSERVER_DIR/lib/" \; 2>/dev/null || true

# Criar symlinks de compatibilidade (como arquivos reais)
cd "$MCSERVER_DIR/lib"
if [ -f libz.so.1.3.1 ] && [ ! -f libz.so ]; then
    cp libz.so.1.3.1 libz.so
    cp libz.so.1.3.1 libz.so.1
fi

echo "   ✅ Java $JAVA_VERSION pronto!"
