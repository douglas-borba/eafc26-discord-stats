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

GET /clubs/matches?platform=common-gen5&clubIds=<ID>&matchType=<TYPE>&maxResultCount=5
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
    max-result-count: 5
    user-agent: "Mozilla/5.0 ..."  # Change if EA updates bot detection
```

To find your `club-id`, use `EaProClubsClient.searchClubs("Your Club Name")` and note
the `clubId` in the response.

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
