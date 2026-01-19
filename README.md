# 🐀 Android Minecraft Server

Deploy ultrarrápido de servidor Minecraft em Android via ADB.

## ✨ Novidades v2.0
- Suporte a **todas as versões** (1.8.x até 1.21+)
- Múltiplos tipos: **Paper, Vanilla, Fabric, Purpur**
- Ajuste automático de RAM por versão
- Patch automático para versões antigas

## 🚀 Uso Rápido

```bash
git clone https://github.com/lzofseven/android-minecraft-server.git
cd android-minecraft-server

# Uso básico
./deploy.sh <VERSAO> <IP:PORTA> [TIPO]

# Exemplos
./deploy.sh 1.21 192.168.100.6:44339           # Paper (padrão)
./deploy.sh 1.8.9 192.168.100.6:44339 paper    # Paper 1.8.9
./deploy.sh 1.20.4 192.168.100.6:44339 fabric  # Fabric modado
./deploy.sh 1.16.5 192.168.100.6:44339 purpur  # Purpur otimizado
```

## 📁 Estrutura
```
android-minecraft-server/
├── deploy.sh              # Script principal
├── scripts/
│   ├── setup_java.sh      # Ambiente Java (Termux)
│   ├── get_server.sh      # Download universal
│   └── download_vanilla.py
├── docs/
│   ├── GUIA_ADB.md        # Configurar ADB
│   ├── MANUAL_IA.md       # Instruções para IA
│   └── MANUAL_IA_BACKGROUND.md
└── README.md
```

## 📖 Documentação
- [Guia ADB (USB/WiFi)](docs/GUIA_ADB.md)
- [Manual para IA](docs/MANUAL_IA.md)

## 📜 Licença
MIT
