# Android Minecraft Server Manager

Aplicativo Android nativo para gerenciar servidores Minecraft localmente no seu dispositivo.

## 🚀 Features

- ✅ Interface moderna com Material Design 3
- 🎮 Suporte para Paper, Fabric, Vanilla e Forge
- 📦 Biblioteca integrada (Modrinth API) para mods/plugins/packs
- ⌨️ Console interativo com input de comandos
- 🌐 Integração automática com Playit.gg
- 🔄 **Persistência de Status**: Recuperação automática de crash/reboot
- 🛡️ **Gerenciamento de Jogadores**: Sistema de OP, Whitelist e Ban
- 🔍 **Busca de Players**: Filtro rápido de jogadores online
- ⚙️ Configurações avançadas (CPU cores, frequência, RAM)
- 📁 Caminho personalizável para o mundo

## 📋 Requisitos

- Android 7.0+ (API 24+)
- 2GB+ RAM (recomendado 4GB)
- Espaço de armazenamento suficiente

## 🛠️ Como Compilar

### Android Studio
1. Clone o repositório
2. Abra o projeto no Android Studio
3. Sincronize o Gradle
4. Build → Build APK

### Linha de Comando
```bash
./gradlew assembleDebug
```

APK gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## 📸 Screenshots

> TODO: Adicionar screenshots

## 🔧 Configurações Disponíveis

- **Tipo de Servidor**: Paper, Fabric, Vanilla, Forge
- **Versão do Minecraft**: 1.8.9 - 1.21+
- **Memória RAM**: 512MB - 4GB
- **Núcleos de CPU**: Configurável (1 - max disponível)
- **Forçar Frequência Máxima**: Aumenta performance
- **Caminho do Mundo**: Personalizável

## 📚 Bibliotecas Utilizadas

- Jetpack Compose
- Hilt (DI)
- Retrofit (API)
- Room (Database)
- DataStore (Preferences)

## 📝 License

MIT License

## 👤 Author

Lohan Santos (@lzofseven)
