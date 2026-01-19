# 🐀 Rataria MC Deploy

Deploy ultrarrápido de servidor Minecraft (PaperMC) em dispositivos Android via ADB.

## 📋 Pré-requisitos
- Linux com `adb`, `wget`, `python3`, `dpkg-deb` instalados.
- Android com **Depuração Wireless** ativada.

## 🚀 Uso Rápido

```bash
# 1. Clone o repositório
git clone https://github.com/SEU_USER/rataria-mc-deploy.git
cd rataria-mc-deploy

# 2. Execute o script principal
./deploy.sh <VERSAO> <IP:PORTA_ADB>

# Exemplo:
./deploy.sh 1.21 192.168.100.6:44339
```

## 📁 Estrutura
```
rataria-mc-deploy/
├── deploy.sh           # Script principal (orquestrador)
├── scripts/
│   ├── setup_java.sh   # Baixa e prepara o ambiente Java (Termux)
│   ├── get_paper.sh    # Baixa o PaperMC da versão solicitada
│   └── download_vanilla.py  # Fallback para versões antigas
└── README.md
```

## ⚙️ Como Funciona
1.  **`setup_java.sh`**: Baixa OpenJDK 21 + libs do Termux e prepara a pasta `mcserver/`.
2.  **`get_paper.sh`**: Baixa o PaperMC + Playit Plugin. Se for versão antiga, usa o script Python para pegar o jar vanilla e patchear localmente.
3.  **`deploy.sh`**: Conecta no ADB, envia os arquivos e inicia o servidor.

## 📜 Licença
MIT - Use como quiser.
