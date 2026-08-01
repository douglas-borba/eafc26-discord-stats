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

Edit `src/main/resources/application.yml`:

```yaml
app:
  ea:
    base-url: https://proclubs.ea.com/api/fc
    platform: common-gen5
    club-id: ""         # Your club's numeric ID
    club-name: ""       # Human-readable name (for search)
    match-type: leagueMatch
    max-result-count: 20
    user-agent: "Mozilla/5.0 ..."  # Change if EA updates bot detection
```

To find your `club-id`, use `EaProClubsClient.searchClubs("Your Club Name")` and note
the `clubId` in the response.

### Canonical capture and backfill

The EA endpoint is requested with `maxResultCount=20`, but currently returns at
most ten recent league matches. Every successful production acquisition stores
the complete returned window in the canonical repository before Discord
deduplication. The local history therefore grows by `MatchId` as windows overlap.

To import the complete window currently available without publishing to Discord,
changing the Dashboard or modifying published-match IDs, run:

```bash
./gradlew bootRun --args='backfill-canonical-matches'
```

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

Inicie o ambiente de desenvolvimento com um único comando:

```bash
./gradlew dev
```

A aplicação permanece em primeiro plano, com os logs visíveis no terminal. Quando
o Spring Boot estiver iniciado, o launcher consulta repetidamente `/api/health` e
só abre o Dashboard no navegador padrão depois de receber uma resposta HTTP de
sucesso.

A URL é construída com a porta efetivamente utilizada pelo servidor, portanto o
fluxo continua funcionando se `server.port` mudar ou for configurado dinamicamente.
Se a inicialização falhar, o erro permanece visível no terminal e o navegador não
é aberto. Use `Ctrl+C` para encerrar o Gradle e a aplicação.

Esse fluxo é independente do `.app`; as tasks de empacotamento abaixo continuam
reservadas para distribuição e validação do bundle.

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
