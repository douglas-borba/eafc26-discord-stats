# EA FC 26 Discord Stats

A Kotlin + Spring Boot service that monitors EA SPORTS FC 26 Pro Clubs matches and posts
a summary of each completed match to a Discord channel via a Discord Webhook.

> **Current status: Increment 1 — EA client and data validation only.**
> The Discord client, scheduler, and deduplication store have not been implemented yet.

---

## Background: EA API access

EA Sports does not provide an official, documented, or supported public API for FC 26
Clubs data. This project uses undocumented HTTP endpoints that power EA's own Pro Clubs
website at `proclubs.ea.com`. These endpoints are unauthenticated and return JSON; the
community has used them across multiple game generations (FC 24 → FC 25 → FC 26).

**These endpoints are inherently unreliable.** They have gone down without notice
multiple times (including a known outage from June 19, 2026). The application is designed
to handle unavailability gracefully — it logs and skips rather than crashing.

**EA's Terms of Service** prohibit automated data collection. This project is a personal
hobby tool. No enforcement against open-source hobby projects has been publicly reported,
but you use this at your own discretion.

---

## ⚠️ Fixture disclaimer

The JSON fixtures in `src/test/resources/fixtures/` are **synthetic** — they were
hand-authored based on verified field names extracted from real production parsing code
in community projects ([BryanAriza/proclubs26][1], [Maldini80/bot-torneos-pro][2]).

**Live payload compatibility has not yet been validated.**

The field names, types, and structure are well-supported by multiple independent
cross-references (TypeScript types, service layer field access, Python wrapper code), but
they have not been confirmed against a raw response captured directly from the live
`proclubs.ea.com` endpoint. Discrepancies are possible. The next validation step is to
capture a live response when the endpoint is available and compare it against the current
DTO definitions.

[1]: https://github.com/BryanAriza/proclubs26
[2]: https://github.com/Maldini80/bot-torneos-pro

---

## Endpoints used

```
Base: https://proclubs.ea.com/api/fc/

GET /allTimeLeaderboard/search?platform=common-gen5&clubName=<NAME>
    Search for clubs by name to find your club's numeric ID.

GET /clubs/matches?platform=common-gen5&clubIds=<ID>&matchType=<TYPE>&maxResultCount=20
    Retrieve the most recent matches for a club.
    matchType: friendlyMatch | leagueMatch | playoffMatch
```

**Note on User-Agent:** EA's endpoint returns HTTP 403 without a browser-like
`User-Agent` header. This is confirmed by multiple community projects. The header is
externalized in `application.yml` so it can be updated without code changes.

---

## Configuration

### Shared application access

The web application requires two different shared passwords supplied only by
environment variables:

```bash
export EAFC_VIEWER_PASSWORD='replace-with-a-long-viewer-password'
export EAFC_ADMIN_PASSWORD='replace-with-a-different-long-admin-password'
```

VIEWER can explore the sports pages. ADMIN can additionally access settings,
setup, monitoring and state-changing operations. Authentication uses an
HttpOnly, SameSite=Lax server session; no JWT or browser storage is used. See
[`docs/security.md`](docs/security.md) for the permission matrix, session/CSRF
behavior and password rotation procedure.

Discord delivery supports reproducible server configuration through one optional
environment variable:

```bash
export EAFC_DISCORD_MATCH_WEBHOOK_URL='https://discord.com/api/webhooks/<id>/<token>'
```

When present, these values take precedence over the local configuration saved by
the administrative interface. Empty values retain the Java Preferences fallback.
Webhook values are never returned by the configuration APIs or written to logs.
See [`docs/discord-webhooks.md`](docs/discord-webhooks.md) for local setup,
precedence, validation and the optional future deployment configuration.

When PostgreSQL club administration is enabled, configure
`EAFC_DISCORD_SECRET_ENCRYPTION_KEY` as a Base64-encoded 32-byte AES-256 key.
It encrypts durable per-club Discord webhook URLs in PostgreSQL and must remain
stable between deployments.

Administrative access in the Dashboard uses Supabase Auth and a server-side
internal token between the Next.js BFF and Spring. See
[`docs/admin-security.md`](docs/admin-security.md) for the required deployment
variables and access matrix.

Edit `src/main/resources/application.yml`:

```yaml
app:
  ea:
    platform: common-gen5
    club-id: ""         # Your club's numeric ID
    club-name: ""       # Human-readable name (for search)
    match-type: leagueMatch
    max-result-count: 20
    gateway-base-url: http://127.0.0.1:8081
    gateway-internal-token: ${EA_GATEWAY_INTERNAL_TOKEN:}
```

To find your `club-id`, use the `search-club` CLI command and note
the `clubId` in the response.

### Canonical capture and backfill

The EA endpoint is requested with `maxResultCount=20`, but currently returns at
most ten recent league matches. Every successful acquisition stores
the complete returned window in the canonical repository before Discord
deduplication. The local history therefore grows by `MatchId` as windows overlap.

To import the complete window currently available without publishing to Discord,
changing the Dashboard or modifying published-match IDs, run:

```bash
./gradlew bootRun --args='backfill-canonical-matches'
```

In a multi-club installation, select the operational scope explicitly. The
omitted form above remains a legacy adapter to the configured default club and
never iterates through all registered clubs:

```bash
./gradlew bootRun --args='--club-id=8874106 backfill-canonical-matches'
./gradlew bootRun --args='--club-id=8874106 notify-latest'
./gradlew bootRun --args='--club-id=8874106 latest-matches'
```

Replay is also scoped before it selects matches. Set `EAFC_REPLAY_CLUB_ID` (or
`app.replay.club-id`) for a non-default club. A dry run never changes canonical
history, publication state or Discord. A publishing replay without a webhook
configured for that club exits with a clear error; it never borrows another
club's webhook.

The command reports requested, returned, processed, created, updated, ignored and
failed records, plus repository counts before and after execution.

The endpoint has no usable pagination and retains only a recent window. A match
that leaves that window before a successful poll cannot be recovered from this
endpoint. The scheduler currently polls every 60 seconds, which reduces but does
not eliminate this risk. Leaving the application stopped while more than ten
league matches are played can create an unrecoverable gap. This capture currently
includes only `leagueMatch`; playoff and friendly matches have independent recent
windows and may be added separately in a future delivery.

---

## Project structure

```
src/
├── main/kotlin/com/eafc26/discordstats/
│   ├── Eafc26DiscordStatsApplication.kt
│   ├── config/
│   │   ├── AppProperties.kt        # @ConfigurationProperties
│   │   └── WebClientConfig.kt      # WebClient @Bean
│   └── ea/
│       ├── EaApiResult.kt          # sealed class: Success/NoMatches/Unavailable/UnexpectedPayload
│       ├── EaProClubsClient.kt     # Club search + match history
│       └── model/
│           ├── ClubSearchResult.kt
│           └── MatchResponse.kt    # MatchResponse, ClubMatchEntry, PlayerEntry
└── test/
    ├── kotlin/.../ea/
    │   └── EaProClubsClientTest.kt # 15 tests, all offline via MockWebServer
    └── resources/fixtures/
        ├── clubs-search.json       # Synthetic fixture — see disclaimer above
        └── clubs-matches.json      # Synthetic fixture — see disclaimer above
```

---

## Building and testing

Requires Java 21.

```bash
./gradlew test          # run all tests (no network required)
./gradlew build -x bootJar  # compile + test without building the fat jar
```

**All 15 tests pass with zero live network calls.** MockWebServer intercepts all HTTP
requests; the EA endpoint does not need to be reachable.

### Desenvolvimento local

O macOS local via Gradle é o ambiente padrão de desenvolvimento. Inicie a
aplicação com um único comando:

```bash
./gradlew bootRun
```

Somente no `bootRun`, quando nenhuma credencial externa estiver definida, o
ambiente local utiliza `viewer-local` para VIEWER e `admin-local` para ADMIN.
Esses valores não entram no `application.yml`, no JAR, no Docker ou em uma futura
implantação. Para substituir as credenciais durante o desenvolvimento, exporte
`EAFC_VIEWER_PASSWORD` e `EAFC_ADMIN_PASSWORD` antes do comando; os valores
externos têm precedência.

A aplicação permanece em primeiro plano, com os logs visíveis no terminal. Quando
o Spring Boot estiver iniciado, o launcher consulta repetidamente `/api/health` e
só abre o Dashboard no navegador padrão depois de receber uma resposta HTTP de
sucesso.

A URL é construída com a porta efetivamente utilizada pelo servidor, portanto o
fluxo continua funcionando se `server.port` mudar ou for configurado dinamicamente.
Se a inicialização falhar, o erro permanece visível no terminal e o navegador não
é aberto. Use `Ctrl+C` para encerrar o Gradle e a aplicação.

No primeiro acesso como ADMIN, configure localmente o webhook de partidas pela
página `/setup`. Ele é mantido pelas preferências do macOS; a variável
`EAFC_DISCORD_MATCH_WEBHOOK_URL` continua disponível como alternativa, mas não é
necessária para iniciar localmente.
Partidas canônicas, IDs publicados e frases são armazenados em
`~/Library/Application Support/EAFC26DiscordStats/`.

O task `./gradlew dev` permanece como alias compatível, mas `./gradlew bootRun` é
o comando oficial para desenvolvimento.

Esse fluxo é independente do `.app`; as tasks de empacotamento abaixo continuam
reservadas para distribuição e validação do bundle.

### EA Gateway

O acesso à EA é isolado em `apps/ea-gateway`, um serviço Node.js/TypeScript que
usa `fetch` nativo. Inicie-o com `EA_GATEWAY_INTERNAL_TOKEN` e configure o mesmo
segredo no Spring. O backend usa `EA_GATEWAY_BASE_URL` (por padrão,
`http://127.0.0.1:8081`) e continua concentrando aquisição, domínio e publicação.

```bash
cd apps/ea-gateway
npm install
npm run build
EA_GATEWAY_INTERNAL_TOKEN=seu-segredo npm start
```

Em outro terminal, inicie o Spring com o mesmo segredo:

```bash
EA_GATEWAY_INTERNAL_TOKEN=seu-segredo ./gradlew bootRun
```

### Aplicativo nativo para macOS

O projeto usa `org.beryx.runtime`, a variante Beryx apropriada para aplicações
não modulares como Spring Boot. Ela cria um runtime Java 21 reduzido com
`jlink` e usa `jpackage` para produzir um bundle autocontido.

O `.app` precisa ser gerado em um Mac com JDK 21:

```bash
./gradlew macApp
```

Resultado:

```text
build/macos/EA FC STATS.app
```

Para gerar e abrir o aplicativo:

```bash
./gradlew openMacApp
```

O atalho histórico também foi restaurado:

```bash
./gradlew packageApp
```

`openMacApp` e `packageApp` iniciam o bundle. O aplicativo aguarda o evento
`ApplicationReadyEvent` do Spring Boot e confirma `/api/health` antes de abrir o
Dashboard no navegador padrão. Se a porta tiver sido alterada, a URL usa a porta
efetivamente iniciada.

Esse comportamento é habilitado apenas no `.app` pela propriedade
`eafc.dashboard.auto-open`. Execuções normais do servidor e testes não abrem o
navegador.

Depois de alterar código ou recursos, gere uma versão completamente nova,
removendo os artefatos anteriores:

```bash
./gradlew rebuildMacApp
```

Para também atualizar a versão registrada no bundle:

```bash
./gradlew rebuildMacApp -PmacAppVersion=1.0.1
```

A versão padrão é `1.0.0`. O valor de `macAppVersion` deve ter de um a três
números separados por pontos e começar com um número positivo, conforme a
restrição do `jpackage` no macOS.

As tasks normais (`test`, `build` e `bootJar`) permanecem independentes do
empacotamento macOS. O bundle inclui seu próprio runtime; não exige uma
instalação externa do Java para execução.

O script legado `scripts/package-macos.sh` permanece como atalho compatível,
mas agora apenas delega para `rebuildMacApp`, evitando dois processos de
empacotamento diferentes.

### Container Linux opcional

A imagem Linux usa Java 21 e executa o EA Gateway em um container Node 22
separado. Este fluxo é
mantido para validação de portabilidade e implantação futura; ele não substitui
o desenvolvimento local via `bootRun`.

Pré-requisito: Docker com Compose disponível. Construa e inicie com:

```bash
cp .env.example .env
# Substitua as senhas e o token interno documentados no arquivo ignorado.
docker compose up --build -d
```

O serviço fica disponível em `http://localhost:8080`. Valide a aplicação e a
aquisição real da EA com:

```bash
curl --fail http://localhost:8080/api/health
docker compose logs ea-gateway app
docker compose exec app ps aux
```

O Compose habilita `init` e persiste os dados canônicos no volume `eafc-data`. A variável
`APP_WEB_NETWORK_ENABLED=true` limita-se a permitir que o servidor escute a
interface publicada pelo container; no macOS, a preferência local existente
continua definindo esse comportamento.

Em produção, a imagem define `JAVA_TOOL_OPTIONS` para limitar o heap da JVM a
20% da memória disponível no container. O valor pode ser sobrescrito externamente;
para o serviço completo em um container de 1 GiB, utilize o padrão documentado
em `.env.example`.

Encerre sem remover o volume persistente:

```bash
docker compose down
```

### Persistência PostgreSQL (espelho)

A aplicação suporta espelhamento paralelo das partidas canônicas para um
PostgreSQL remoto. O JSON local continua como persistência primária; o
PostgreSQL recebe uma cópia idempotente de cada `CanonicalMatch`.

Para habilitar, configure as variáveis:

```bash
export EAFC_POSTGRES_MIRROR_ENABLED=true
export SPRING_DATASOURCE_URL='jdbc:postgresql://host:5432/eafc_stats'
export SPRING_DATASOURCE_USERNAME='eafc_collector'
export SPRING_DATASOURCE_PASSWORD='replace-with-database-password'
```

Quando `EAFC_POSTGRES_MIRROR_ENABLED=false` (padrão), nenhum bean de banco é
criado e a aplicação inicia normalmente sem PostgreSQL.

Para copiar o acervo JSON existente para o PostgreSQL:

```bash
./gradlew bootRun --args='backfill-postgres'
```

O backfill não consulta a EA, não envia ao Discord e não altera o
`PublishedMatchStore`. Pode ser repetido com segurança.

#### Controle de aquisição

Para desabilitar a aquisição (polling EA + publicação Discord) sem desligar
a aplicação:

```bash
export EAFC_ACQUISITION_ENABLED=false
```

Isso permite iniciar o site e APIs locais sem executar o scheduler.

#### Arquitetura atual (Fase A)

```
EA → Spring local → JSON + PostgreSQL remoto → Discord
```

#### Arquitetura futura

```
EA → Spring coletor → PostgreSQL remoto → Next.js/Vercel
```

- O site Spring atual continua existindo temporariamente
- Vercel não faz aquisição da EA
- O EA Gateway Node permanece junto ao coletor
- O coletor pode futuramente sair do Mac e ir para Railway/VPS
- Somente um coletor publica enquanto o estado de publicação não for centralizado

Detalhes sobre o contrato futuro de leitura em
[`docs/future-reading-contract.md`](docs/future-reading-contract.md).

#### Segurança do banco

- Credencial do coletor: escrita (usado pelo Spring)
- Frontend/API pública futura: somente leitura controlada
- Secrets somente no ambiente, nunca versionados
- Nenhuma service-role key no navegador

#### Estado de publicação

O `PublishedMatchStore` permanece local (JSON em Application Support).
Antes de executar mais de um coletor, o estado de publicação precisará ser
centralizado. Somente uma instância deve ter polling/publicação habilitados
enquanto o estado permanecer local.

### Railway — implantação futura

O Railway não é um ambiente ativo nem o fluxo principal do projeto neste momento.
A containerização, as variáveis externas e os documentos de operação permanecem
preservados para uma possível retomada. Antes de uma futura implantação, aplique
novamente os gates de segurança, memória, persistência e aquisição real descritos
em [`docs/production-memory.md`](docs/production-memory.md),
[`docs/containerization-validation.md`](docs/containerization-validation.md) e
[`docs/discord-webhooks.md`](docs/discord-webhooks.md).

---

## Result type

Every EA API call returns an `EaApiResult<T>` — callers never receive null or catch
exceptions:

| Variant | Meaning |
|---|---|
| `Success<T>` | Valid, parsed response |
| `NoMatches` | HTTP 200 but empty array |
| `Unavailable(statusCode, message)` | Non-2xx HTTP status (403, 503, timeout, …) |
| `UnexpectedPayload(cause)` | HTTP 200 but unparseable body (schema change) |

---

## Planned increments

- **Increment 2:** `PublishedMatchStore` (persistent JSON file), `MatchService` (dedup
  logic), validate live payload shape against DTOs
- **Increment 3:** `DiscordWebhookClient`, `MatchPoller` (`@Scheduled`), end-to-end wiring
