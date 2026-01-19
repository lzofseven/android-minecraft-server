#!/usr/bin/env python3
"""
Download Vanilla Minecraft Server JAR (Bypass para versões antigas)
Uso: python3 download_vanilla.py <versao> <pasta_destino>
"""

import sys
import os
import requests

def main():
    if len(sys.argv) < 3:
        print("Uso: python3 download_vanilla.py <versao> <pasta_destino>")
        sys.exit(1)

    version = sys.argv[1]
    dest_dir = sys.argv[2]
    os.makedirs(dest_dir, exist_ok=True)

    try:
        # Buscar manifest
        manifest = requests.get(
            "https://launchermeta.mojang.com/mc/game/version_manifest.json"
        ).json()

        # Encontrar URL da versão
        version_url = next(
            (v["url"] for v in manifest["versions"] if v["id"] == version), None
        )
        
        if not version_url:
            print(f"Versão {version} não encontrada no manifest.")
            sys.exit(1)

        # Buscar detalhes da versão
        version_data = requests.get(version_url).json()
        server_url = version_data["downloads"]["server"]["url"]

        # Baixar server.jar
        print(f"   Baixando minecraft_server.{version}.jar...")
        response = requests.get(server_url)
        
        jar_path = os.path.join(dest_dir, f"minecraft_server.{version}.jar")
        with open(jar_path, "wb") as f:
            f.write(response.content)

        print(f"   ✅ Salvo em: {jar_path}")

    except Exception as e:
        print(f"Erro: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
