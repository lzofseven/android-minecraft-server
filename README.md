# 🐀 Android Minecraft Server

Deploy ultrarrápido de servidor Minecraft (PaperMC) em dispositivos Android via ADB.

## 📋 Pré-requisitos
- Linux com `adb`, `wget`, `python3`, `dpkg-deb`, `curl` instalados
- Android com **Depuração Wireless/USB** ativada ([Guia de configuração](docs/GUIA_ADB.md))

## 🚀 Uso Rápido

```bash
# 1. Clone o repositório
git clone https://github.com/LoohanZinho/android-minecraft-server.git
cd android-minecraft-server

# 2. Execute o script principal
./deploy.sh <VERSAO> <IP:PORTA_ADB>

# Exemplos:
./deploy.sh 1.21 192.168.100.6:44339
./deploy.sh 1.8.9 192.168.100.6:44339
./deploy.sh 1.20.4 10.0.0.5:5555
```

> 💡 **Dica:** O IP:Porta é o do seu celular no ADB. Veja o [Guia de ADB](docs/GUIA_ADB.md) para configurar.

## 📁 Estrutura
```
android-minecraft-server/
├── deploy.sh              # Script principal (orquestrador)
├── scripts/
│   ├── setup_java.sh      # Baixa e prepara o ambiente Java (Termux)
│   ├── get_paper.sh       # Baixa o PaperMC da versão solicitada
│   └── download_vanilla.py # Fallback para versões antigas
├── docs/
│   ├── GUIA_ADB.md        # Como configurar ADB (USB e Wi-Fi)
│   └── MANUAL_IA_BACKGROUND.md  # Rodar servidor em background
└── README.md
```

## ⚙️ Como Funciona
1. **`setup_java.sh`**: Baixa OpenJDK 21 + libs do Termux e prepara a pasta `mcserver/`
2. **`get_paper.sh`**: Baixa o PaperMC + Playit Plugin. Se for versão antiga, usa o script Python para o bypass
3. **`deploy.sh`**: Conecta no ADB, envia os arquivos e inicia o servidor

## 🔄 Rodar em Background

Para manter o servidor rodando mesmo após fechar o terminal, veja o [Manual de Background](docs/MANUAL_IA_BACKGROUND.md).

## 📜 Licença
MIT - Use como quiser.
