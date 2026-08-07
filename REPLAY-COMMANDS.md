# Comandos de Replay - Guia de Uso

## ✅ Comandos Corretos (Usar esses!)

### Dry-Run (Inspeção sem enviar mensagens)
```bash
# Inspecionar as 10 partidas mais recentes
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.dryRun=true"

# Inspecionar partida específica
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matchId=944922107030449 --app.replay.dryRun=true"

# Inspecionar 10 partidas excluindo uma específica
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=944922107030449 --app.replay.dryRun=true"

# Inspecionar 10 partidas excluindo várias
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=944922107030449,944922107030450,944922107030451 --app.replay.dryRun=true"
```

### Replay Real (Envia mensagens ao Discord)
```bash
# Reenviar as 10 partidas mais recentes
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10"

# Reenviar partida específica
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matchId=944922107030449"

# Reenviar 10 partidas excluindo uma específica
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=944922107030449"

# Reenviar 10 partidas excluindo várias
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=944922107030449,944922107030450,944922107030451"
```

## ⚠️ Comportamento Quando Replay Está Ativo

Quando `--app.replay.enabled=true` é especificado:

1. ✅ **ReplayRecentMatchesRunner** é ativado
2. ❌ **MatchPollingScheduler** é desabilitado (não faz polling automático)
3. ❌ **DashboardAutoLauncher** é desabilitado (não abre browser)
4. ❌ **Aquisição automática** não acontece
5. ✅ **Processo encerra** após replay via `System.exit()`

## 📋 Parâmetros Disponíveis

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `app.replay.enabled` | boolean | Sim | Ativa o modo replay |
| `app.replay.matches` | int | Não* | Número de partidas a reenviar |
| `app.replay.matchId` | string | Não* | ID específico de uma partida |
| `app.replay.dryRun` | boolean | Não | Modo inspeção (não envia) |
| `app.replay.excludeMatchIds` | string | Não | IDs para excluir (separados por vírgula) |

\* Você deve especificar **OU** `matches` **OU** `matchId`, nunca os dois.

## 🔍 Exemplo de Output do Dry-Run

```
=====================================
REPLAY RECENT MATCHES - ADMIN TOOL
Mode: RECENT MATCHES
Limit: 10 matches
DRY RUN: Enabled (no messages will be sent)
=====================================
Found 10 match(es) to process
-------------------------------------
--------------------------------------------------------
[1/10]

MatchId:
944922107030449

Data:
07/08/2026 01:47

Tipo:
Playoff

Associação BF 4 x 2 Cabuloso

Craque:
R. Nazario

Bagre:
D.Prima

Narrativa LLM:
SIM

Título do Embed:
🏆 Associação BF 4 × 2 Cabuloso

Embeds:
1

Imagem:
NÃO

Webhook:
https://discord.com/api/webhooks/...

WOULD SEND
--------------------------------------------------------
[...]
=====================================
DRY RUN COMPLETED
Total: 10 match(es)
Analyzed: 10 ✅
Render failures: 0 ❌

No messages were sent to Discord.
=====================================
```

## 🚀 Fluxo de Uso Recomendado

### Passo 1: Inspeção com Dry-Run
```bash
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=20 --app.replay.dryRun=true"
```

### Passo 2: Identificar partidas a reenviar
- Anote os `matchId` que faltam no Discord

### Passo 3: Replay seletivo
```bash
# Reenviar partidas específicas uma por uma
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matchId=944922107030449"
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matchId=944922107030450"
```

## ⚠️ Rate Limit do Discord

- **Limite**: 30 mensagens por 60 segundos
- **Delay implementado**: 2 segundos entre envios
- **Máximo seguro por execução**: 30 partidas

Para mais de 30 partidas:
```bash
# Lote 1
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=30"

# Aguardar 2 minutos

# Lote 2  
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=30"
```

## 🔒 Garantias de Segurança

O replay mode:
- ✅ **NÃO grava** no PublishedMatchStore
- ✅ **NÃO altera** PublicationState
- ✅ **NÃO interfere** com deduplicação
- ✅ **NÃO interfere** com reconciliação
- ✅ **Isola completamente** do fluxo normal

## ❌ Comandos Antigos (NÃO usar)

Estes comandos **NÃO funcionam**:
```bash
# ❌ NÃO FUNCIONA - system properties não são lidas
./gradlew bootRun -Dreplay.matches=10 -Dreplay.dryRun=true

# ❌ NÃO FUNCIONA - sintaxe incorreta
./gradlew bootRun --replay.matches=10
```

## 🧪 Teste de Validação

Execute este comando para validar que tudo está funcionando:
```bash
./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=1 --app.replay.dryRun=true"
```

**Resultado esperado:**
- ✅ Mostra detalhes de 1 partida
- ✅ Exibe "WOULD SEND"
- ✅ Mostra "DRY RUN COMPLETED"
- ✅ Mostra "No messages were sent to Discord."
- ✅ Processo encerra automaticamente
- ✅ Dashboard NÃO abre
- ✅ Scheduler NÃO executa

Se algo diferente acontecer, **reporte imediatamente**.

