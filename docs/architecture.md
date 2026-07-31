# Architecture

## Objective

The application turns EA SPORTS FC Pro Clubs match data into deterministic,
auditable football interpretations that can later be rendered for Discord, a
dashboard, social media or an optional language model.

Football decisions belong to code. Presentation components may express a
decision, but must not decide who won, who was eligible or who earned an
award.

The repository is in an incremental migration. The production presentation
flow still consumes EA DTOs directly. In parallel, an isolated normalized flow
now reaches `MatchInterpretation`; it is not connected to production
consumers yet.

## Layers and responsibilities

### Infrastructure

Infrastructure contains integration details:

- `ea`: HTTP/browser gateways, JSON parsing and EA DTOs;
- `ea.mapping`: the Anti-Corruption Layer that translates EA DTOs into the
  normalized domain;
- `discord`: Discord payload models, webhook delivery and legacy
  presentation-oriented evaluators;
- `web`, `scheduler`, `cli`, `store`, `config` and `service`: runtime adapters,
  orchestration, persistence and framework configuration.

Infrastructure may depend on application and domain types. Domain and
application code must not depend on these adapters.

### Domain

`domain.match` contains immutable football facts and typed values:

- match, clubs and players;
- score and reported result;
- player role and participation;
- attacking, passing, defending, discipline and goalkeeping statistics.

`domain.interpretation` contains deterministic decisions and their audit
contracts:

- result;
- statistical eligibility;
- team metrics;
- awards;
- `RuleReference`;
- `DecisionEvidence`;
- the canonical `MatchInterpretation`.

The domain contains no Spring, Jackson, EA DTO, Discord or transport concern.

### Application

`application.interpretation` applies and composes football rules:

- `MatchOutcomeEvaluator`;
- `PlayerEligibilityEvaluator`;
- `TeamMetricsCalculator`;
- award evaluators and `MatchAwardsEvaluator`;
- `MatchInterpreter`, which composes the canonical interpretation.

Application services consume normalized domain objects. They do not parse EA
fields and do not build presentation payloads.

### Presentation

The current `presentation` and `discord` packages build the dashboard summary
and Discord embeds. During migration they still contain legacy football rules.
The target responsibility is rendering already interpreted stories without
re-evaluating football facts.

## Current production flow

```text
EA HTTP/browser payload
  -> EaClubsGateway implementation
  -> EaResponseParser/Jackson
  -> MatchResponse and PlayerEntry EA DTOs
  -> MatchAcquisitionService
     -> MatchSummaryBuilder
        -> MatchSummaryPresentation
        -> LatestMatchHolder
        -> MatchCardService/web dashboard
     -> DiscordEmbedBuilder and HistoryEmbedBuilder
        -> DiscordWebhookClient
     -> PublishedMatchStore
```

This is the observable production path. Its legacy builders and selectors
remain unchanged while the normalized path is developed and tested.

## Normalized interpretation flow

```text
EA MatchResponse
  -> EaMatchMapper (Anti-Corruption Layer)
  -> MatchNormalizationResult
     -> FootballMatch
     -> normalization warnings
  -> MatchInterpreter
     -> MatchOutcomeEvaluator
     -> PlayerEligibilityEvaluator
     -> TeamMetricsCalculator
     -> MatchAwardsEvaluator
        -> BagreEvaluator
        -> CraqueEvaluator
        -> XerifeEvaluator
  -> MatchInterpretation
     -> decisions
     -> RuleReference values
     -> DecisionEvidence values
```

This path currently ends at `MatchInterpretation`. It has no production
consumer and therefore cannot alter Discord or dashboard behavior.

## Anti-Corruption Layer

`EaMatchMapper` is the only place where EA-specific representations are
translated into the normalized match domain. It:

- parses text-based statistics into typed values;
- supplies documented fallbacks required by domain invariants;
- rejects unrecoverable match identity or structure;
- clamps inconsistent completed/attempted values;
- records every anomaly as a normalization issue.

EA DTOs must not escape through this boundary into domain or application
rules. Conversely, the domain must not know field names or encoding
conventions from the EA payload.

## Dependency rules

Allowed:

```text
Infrastructure/presentation -> Application -> Domain
Infrastructure/ACL          -> Domain
Tests                        -> any layer under test
```

Prohibited:

- Domain -> Spring, Jackson, Discord, EA DTOs or application services.
- Application -> Spring, Jackson, Discord, EA DTOs or web transports.
- Renderers -> football selection or eligibility rules in the target
  architecture.
- ACL -> presentation models.
- Optional LLM -> winner selection, eligibility or award decisions.

The legacy production path temporarily violates the target renderer rule. Its
behavior is protected by characterization tests until consumers are migrated.

## Principles

### Determinism

Given the same normalized match and club perspective, evaluators must produce
the same result. Random phrase selection and language generation are not
football decisions.

### Traceability

Every deterministic decision identifies the rule and version that produced it
through `RuleReference`. `DecisionEvidence` records the facts, candidates,
thresholds and exclusions needed to audit the outcome.

### One canonical interpretation

`MatchInterpretation` is the intended single source for every future
presentation channel. Consumers must not independently reinterpret the match.

### Separation of football and language

The domain describes football facts and decisions. Story and rendering layers
select structure and wording. Discord, dashboards and an optional LLM render
an already interpreted result; they do not decide it.

### Incremental migration

The normalized pipeline remains side-by-side with production until parity is
demonstrated. Each phase compiles independently, preserves existing tests and
does not change observable behavior unless that change is explicitly approved.
