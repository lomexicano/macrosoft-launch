# 🟣 Macrosoft Launcher

> Launcher de Minecraft customizado com foco em **segurança**, **privacidade** e suporte a **modpacks gerenciados**.

---

## Índice

- [Sobre](#sobre)
- [Funcionalidades](#funcionalidades)
- [Segurança](#segurança)
  - [Criptografia de credenciais](#1-criptografia-de-credenciais--aes-256-gcm)
  - [Autenticação Yggdrasil](#2-autenticação-yggdrasil--tokens-sem-senha-em-disco)
  - [Integridade dos arquivos](#3-integridade-dos-arquivos--sha-1)
  - [Proxy SOCKS5](#4-proxy-socks5-autenticado--por-perfil)
  - [Permissões de arquivo](#5-permissões-de-arquivo--posix)
  - [HTTPS obrigatório](#6-https-obrigatório-em-todas-as-conexões)
- [Proxy SOCKS5 — Guia de Uso](#proxy-socks5--guia-de-uso)
- [Modpacks](#modpacks)
- [Build](#build)
- [Requisitos](#requisitos)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Licença](#licença)

---

## Sobre

O **Macrosoft Launcher** é um launcher de Minecraft construído sobre a base oficial da Mojang
(`com.mojang.launcher`), estendido com:

- Interface gráfica em Swing com tema escuro personalizado
- Suporte a múltiplos **modpacks gerenciados** via API
- Proxy **SOCKS5 autenticado** configurável por perfil
- Criptografia robusta de credenciais sensíveis armazenadas em disco

---

## Funcionalidades

| Recurso | Descrição |
|---|---|
| 🎮 **Multi-modpack** | Navega, baixa e lança diferentes modpacks a partir de uma API centralizada |
| 👤 **Múltiplos perfis** | Cada perfil tem versão, diretório, JVM, resolução e proxy independentes |
| 🔌 **Proxy SOCKS5** | Configuração por perfil com credenciais e botão de teste integrado |
| 🔐 **Credenciais criptografadas** | Senhas e tokens protegidos com AES-256-GCM vinculado à máquina |
| 📋 **Console de logs** | Saída do jogo em tempo real por aba, com histórico |
| ⬇️ **Downloader com progresso** | Download de modpacks com barra de progresso e cancelamento |
| ☕ **Java customizável** | Caminho e argumentos JVM configuráveis por perfil |
| 🖥️ **Multi-plataforma** | Linux, Windows e macOS |

---

## Segurança

Esta seção descreve as camadas de segurança implementadas. O objetivo é garantir que
**credenciais nunca fiquem expostas em texto plano** e que o jogador possa operar com
**privacidade de rede** quando necessário.

---

### 1. Criptografia de Credenciais — AES-256-GCM

**Arquivo:** `src/.../utils/CryptoUtils.java`

Toda credencial sensível armazenada em disco (senha do proxy, tokens de sessão) é
protegida com a stack criptográfica mais robusta disponível na JVM padrão:

| Parâmetro | Valor |
|---|---|
| Algoritmo | `AES/GCM/NoPadding` |
| Tamanho da chave | **256 bits** |
| Tag de autenticação (GCM) | **128 bits** |
| IV | 12 bytes — gerado com `SecureRandom` por operação |
| Salt | 16 bytes — gerado com `SecureRandom` por operação |
| Derivação de chave | `PBKDF2WithHmacSHA256` |
| Iterações PBKDF2 | **65.536** |

#### Por que AES-GCM?

O modo **GCM (Galois/Counter Mode)** é um modo de cifragem **autenticada** (AEAD).
Além de cifrar os dados, ele gera uma **tag de autenticação de 128 bits** que detecta
qualquer adulteração do ciphertext em disco. Se o arquivo for modificado por um processo
externo, a descriptografia **falha explicitamente** — nunca silenciosamente.

#### Chave vinculada à máquina

A chave AES é derivada de um **segredo aleatório gerado na primeira execução**, armazenado em:

```
~/.macrosoft/.launcher_secret                              (Linux/Windows)
~/Library/Application Support/macrosoft/.launcher_secret  (macOS)
```

Esse arquivo é criado com `SecureRandom` (UUID duplo = ~72 caracteres de entropia) e tem
**permissões restritas ao dono** (`chmod 600`) via `PosixFilePermissions` em sistemas Unix.

**Consequência prática:** mesmo que o arquivo de perfis (`launcher_profiles.json`) seja
copiado para outra máquina, as senhas armazenadas **serão ilegíveis** — a chave
está vinculada ao dispositivo original.

---

### 2. Autenticação Yggdrasil — Tokens sem senha em disco

**Arquivos:** `YggdrasilUserAuthentication.java`, `AuthenticationDatabase.java`

O launcher implementa o protocolo **Yggdrasil** da Mojang:

1. O usuário digita a senha **apenas uma vez** no campo de login
2. A Mojang retorna um `accessToken` + `clientToken` via HTTPS
3. Apenas os **tokens** são persistidos em disco — **a senha nunca é salva**
4. Nas sessões seguintes, o `accessToken` é usado para autenticação automática

```
Senha digitada ──► Mojang API (HTTPS) ──► accessToken + clientToken
                                                    │
                                         Salvo em launcher_profiles.json
                                         (apenas tokens, nunca a senha)
```

A assinatura digital do servidor de autenticação é verificada usando a chave pública
oficial da Mojang incluída no launcher (`yggdrasil_session_pubkey.der`).

---

### 3. Integridade dos Arquivos — SHA-1

**Arquivos:** `ChecksummedDownloadable.java`, `PreHashedDownloadable.java`

Todos os arquivos do jogo (JAR principal, bibliotecas, assets de textura e som) são
verificados após cada download:

- O manifesto de versão da Mojang contém o hash SHA-1 esperado de cada arquivo
- Após o download, o launcher **recalcula o SHA-1** e compara byte a byte
- Arquivos corrompidos ou adulterados são **rejeitados e rebaixados automaticamente**

Isso protege contra:
- Corrupção silenciosa durante o download
- Ataques de intermediário (MITM) que substituam arquivos por versões maliciosas
- Modificação acidental de arquivos locais do jogo

---

### 4. Proxy SOCKS5 Autenticado — por Perfil

**Arquivos:** `ProfileProxyPanel.java`, `MinecraftGameRunner.java`

O suporte a proxy cobre **todo o tráfego de rede do jogo** através de duas fases:

#### Fase 1 — Propriedades JVM (antes do `main` class)

Argumentos injetados no processo filho **antes** do nome da classe principal, garantindo
que sejam interpretados pela JVM como propriedades do sistema:

```
java  [...]
      -DsocksProxyHost=<host>          ← lido por java.net.Socket
      -DsocksProxyPort=<porta>
      -Djava.net.socks.username=<user>
      -Djava.net.socks.password=<pass>
      net.minecraft.client.main.Main   ← main class
      [game args...]
```

Cobrem: autenticação com a Mojang, downloads do launcher, sockets Java nativos.

#### Fase 2 — Argumentos do jogo (após `main` class)

```
--proxyHost <host>    ← Minecraft cria Proxy.Type.SOCKS internamente
--proxyPort <porta>
--proxyUser <usuário>
--proxyPass <senha>
```

O Minecraft (1.6.4+) lê esses argumentos e cria um `Proxy.Type.SOCKS` que é passado ao
`NetworkManager` / **Netty**. Isso cobre as conexões TCP a servidores de Minecraft —
que usam NIO e **não** herdam as propriedades do sistema da Fase 1.

#### Authenticator global

O `java.net.Authenticator` é registrado no processo do launcher para que todas as
requisições `HttpURLConnection` também possam se autenticar com o proxy SOCKS5.

#### Senha do proxy — nunca em texto plano no disco

```
Profile.getProxyPassword()               ← ciphertext AES-256-GCM em disco
        │
        ▼  CryptoUtils.decrypt()
        │
        ▼  plaintext em memória (temporário)
        │
        ▼
-Djava.net.socks.password=<plain>        ← passado ao processo filho
--proxyPass <plain>                      ← passado ao processo filho
```

A versão encriptada **nunca** é repassada ao processo filho.

#### Sem fallback para conexão direta

Quando o proxy está configurado e indisponível, as tentativas de conexão **falham com
exceção** — não existe código de fallback para conexão direta. O tráfego não vaza
acidentalmente pela interface de rede real.

---

### 5. Permissões de Arquivo — POSIX

Em Linux e macOS, o segredo criptográfico é protegido por permissões de sistema:

```java
// CryptoUtils.java
Set<PosixFilePermission> perms = EnumSet.of(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE   // chmod 600
);
Files.setPosixFilePermissions(secretFilePath, perms);
```

Outros usuários do mesmo sistema **não conseguem ler** o arquivo `.launcher_secret`,
impedindo que um processo ou usuário não-privilegiado derive a chave AES.

---

### 6. HTTPS Obrigatório em Todas as Conexões

Todas as URLs hardcoded no launcher usam HTTPS:

| Serviço | URL |
|---|---|
| Manifesto de versões | `https://launchermeta.mojang.com/mc/game/version_manifest.json` |
| Autenticação Yggdrasil | `https://authserver.mojang.com` |
| Status dos serviços Mojang | `https://status.mojang.com/check` |
| Download de bibliotecas | `https://libraries.minecraft.net/` |
| Download de assets | `https://resources.download.minecraft.net/` |
| API de modpacks Macrosoft | `https://www.macrosoft.website/launcher/info` |

---

## Proxy SOCKS5 — Guia de Uso

1. Abra o **editor de perfil** (botão ⚙ ao lado do modpack)
2. Vá à seção **Configurações de Proxy**
3. Marque **"Habilitar Proxy para este Perfil"**
4. Preencha os campos:

```
┌─ Configurações de Proxy ──────────────────────────────────────┐
│ ☑ Habilitar Proxy para este Perfil                             │
│ Tipo de Proxy:      SOCKS5                                     │
│ Endereço (Host):    [ 147.93.x.x                           ]   │
│ Porta:              [ 8888                                 ]   │
│ Usuário (Opcional): [ meuusuario                           ]   │
│ Senha (Opcional):   [ ••••••••••••                         ]   │
│ [Testar Conexão]    ✔ Conectado! IP de saída: 147.93.x.x      │
└────────────────────────────────────────────────────────────────┘
```

5. Clique em **"Testar Conexão"** — o launcher testa via `https://ifconfig.me` e exibe o
   IP de saída do proxy
6. Clique em **Salvar Perfil**

> **Dica:** o botão de teste usa exatamente o mesmo mecanismo (`Proxy.Type.SOCKS` +
> `Authenticator`) que será utilizado no lançamento do jogo. Se o teste passar, o jogo
> também vai funcionar através do proxy.

---

## Modpacks

Os modpacks são carregados da API `https://www.macrosoft.website/launcher/info`.
Cada entrada contém nome, autor, ícone e URL de download.

O launcher instala cada modpack em um diretório **completamente isolado**:

```
.macrosoft/
├── modpack-a/
│   ├── versions/
│   ├── libraries/
│   ├── assets/
│   └── mods/
└── modpack-b/
    └── ...
```

Saves, configurações e versões de um modpack **nunca interferem** com os demais.

---

## Build

### Compilar

```bash
./gradlew shadowJar
```

O JAR executável (`mclaunch-all.jar`) será gerado em `build/libs/`.

### Executar

```bash
java -jar build/libs/mclaunch-all.jar
```

### Executar com proxy global (via CLI)

```bash
java -jar build/libs/mclaunch-all.jar \
  --proxyHost 127.0.0.1 \
  --proxyPort 1080 \
  --proxyUser usuario \
  --proxyPass senha
```

> O proxy via CLI é global (afeta o launcher inteiro). Para isolamento por modpack,
> use a configuração de proxy por perfil dentro do launcher.

---

## Requisitos

| Componente | Versão mínima |
|---|---|
| Java (runtime) | 8 (recomendado: 17 ou 21) |
| Java (compilação) | 8+ (`source/target 1.8`) |
| Sistema Operacional | Linux, Windows 10+, macOS |
| RAM | 512 MB (launcher) + RAM do modpack |
| Disco | ~500 MB por modpack instalado |

---

## Estrutura do Projeto

```
src/main/java/
├── com/mojang/
│   ├── authlib/           # Protocolo Yggdrasil (autenticação Mojang)
│   └── launcher/          # Core: download, versões, processo do jogo
└── net/minecraft/
    ├── launcher/
    │   ├── Macrosoft/     # Browser de modpacks, downloader, bootstrapper
    │   ├── game/
    │   │   └── MinecraftGameRunner.java  # Orquestra o lançamento + proxy
    │   ├── profile/       # Perfis e banco de autenticação
    │   ├── ui/
    │   │   └── popups/profile/
    │   │       └── ProfileProxyPanel.java  # UI de configuração SOCKS5
    │   └── utils/
    │       └── CryptoUtils.java  # AES-256-GCM + PBKDF2WithHmacSHA256
    └── hopper/            # Relatório de crashes (Hopper Service)
```

---

## Licença

Este projeto é derivado do launcher oficial da Mojang.  
Consulte o arquivo [LICENSE](LICENSE) para os termos completos.

---

<p align="center">
  <sub>Macrosoft Launcher — Seguro por design, privado por configuração.</sub>
</p>

