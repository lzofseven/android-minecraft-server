# 📱 Guia de Configuração do ADB

Este guia ensina como conectar seu Android ao PC para o deploy do servidor Minecraft.

---

## 🔌 Opção 1: ADB via USB (Mais Simples)

### Passo 1: Ativar Opções do Desenvolvedor
1. Vá em **Configurações > Sobre o telefone**
2. Toque **7x** em "Número da versão" (ou "Número de compilação")
3. Uma mensagem aparecerá: *"Você agora é um desenvolvedor!"*

### Passo 2: Ativar Depuração USB
1. Vá em **Configurações > Sistema > Opções do desenvolvedor**
2. Ative **"Depuração USB"**

### Passo 3: Conectar o Cabo
1. Conecte o celular ao PC via cabo USB
2. No celular, aceite o popup **"Permitir depuração USB?"**
3. Marque **"Sempre permitir deste computador"**

### Passo 4: Verificar Conexão
```bash
adb devices
```
Deve aparecer algo como:
```
List of devices attached
XXXXXXXX    device
```

---

## 📶 Opção 2: ADB via Wi-Fi (Sem Cabo)

> ⚠️ **Requisito:** Android 11+ OU estar na mesma rede Wi-Fi que o PC.

### Método A: Android 11+ (Pareamento Wireless)

1. Vá em **Opções do desenvolvedor > Depuração sem fio**
2. Ative e toque em **"Parear dispositivo com código de pareamento"**
3. Anote o **IP:Porta** e o **Código** exibidos
4. No terminal do PC:
   ```bash
   adb pair IP:PORTA
   # Digite o código quando solicitado
   ```
5. Depois de pareado, conecte:
   ```bash
   adb connect IP:PORTA_CONEXAO
   ```
   > A porta de conexão é diferente da porta de pareamento!

### Método B: Android 10 ou inferior (Via USB primeiro)

1. Conecte via USB e execute:
   ```bash
   adb tcpip 5555
   ```
2. Descubra o IP do celular: **Configurações > Wi-Fi > [Sua rede] > IP**
3. Desconecte o cabo e execute:
   ```bash
   adb connect SEU_IP:5555
   ```

---

## 🔧 Instalação do ADB no PC

### Linux (Ubuntu/Debian)
```bash
sudo apt install adb
```

### Arch Linux
```bash
sudo pacman -S android-tools
```

### Windows
1. Baixe o [Platform Tools](https://developer.android.com/tools/releases/platform-tools)
2. Extraia e adicione a pasta ao PATH do sistema

### macOS
```bash
brew install android-platform-tools
```

---

## ❓ Problemas Comuns

| Problema | Solução |
|----------|---------|
| `no devices` | Verifique se o cabo suporta dados (não apenas carga) |
| `unauthorized` | Aceite o popup no celular ou revogue autorizações e tente novamente |
| `offline` | Reinicie o servidor ADB: `adb kill-server && adb start-server` |
| Conexão Wi-Fi cai | Mantenha a tela do celular ligada durante o processo |
