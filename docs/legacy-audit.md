# Legacy pipeline audit

This audit was completed before the Phase 11 removal. References were checked
across production, tests, resources and documentation.

## Classification

| Component | Classification | Reason | Impact if removed directly |
|---|---|---|---|
| `DiscordEmbedBuilder` | Test-only characterization/shadow | Baseline for Discord structural parity; no production caller | Shadow comparison and historical Discord contract tests would be lost |
| `HistoryEmbedBuilder` | Test-only characterization/shadow | Baseline for the history webhook, including DIV-003 | History parity and the known-defect proof would be lost |
| `LegacyMatchSummaryBuilder` | Test-only characterization/shadow | Baseline for Dashboard parity | Dashboard shadow and Phase 0 cross-consumer proofs would be lost |
| `MatchOutcomeResolver` | Test-only characterization/shadow | Old DTO-based outcome rule used only by legacy builders/tests | Old-result characterization would no longer compile |
| `CraqueSelector` | Test-only characterization/shadow | Replaced by `CraqueEvaluator` | Legacy award and renderer tests would be lost |
| `BagrePerformanceEvaluator` | Test-only characterization/shadow | Replaced by `BagreEvaluator` and canonical feature decisions | Legacy Bagre output baseline would be lost |
| `XerifeSelector` | Test-only characterization/shadow | Replaced by `XerifeEvaluator` | Legacy defensive-award baseline would be lost |
| `PassePrecisaoSelector` | Test-only characterization/shadow | Replaced by `MatchFeaturesEvaluator` | Legacy pass-award baseline would be lost |
| `CorreioExtraviadoSelector` | Test-only characterization/shadow | Replaced by `MatchFeaturesEvaluator` | Legacy negative-pass narrative baseline would be lost |
| `RedCardEvaluator` | Test-only characterization/shadow | Replaced by `MatchFeaturesEvaluator` | Legacy discipline narrative baseline would be lost |
| `OffensiveNarrativeEvaluator` | Test-only characterization/shadow | Replaced by `MatchFeaturesEvaluator` | Legacy offensive ordering and copy baseline would be lost |
| `GoalkeeperEvaluator` and legacy `GoalkeeperArchetype` | Test-only characterization/shadow | Replaced by canonical goalkeeper decision and domain archetype | Goalkeeper parity and phrase characterization would be lost |
| `AttackingThreatPresenter` and `PerigoConstanteSelector` | Test-only characterization | Superseded legacy offensive presentation helpers | Their focused historical tests would be lost |
| `PlayerStatisticsEligibility` | Test-only characterization/shadow | DTO-based eligibility replaced by `PlayerEligibilityEvaluator` | Legacy builders and eligibility parity tests would not compile |
| `PlayerEntry.displayName(...)` legacy helpers | Test-only characterization/shadow | Canonical names are normalized into `PlayerIdentity` by the ACL | Legacy renderer and DTO helper tests would not compile |
| `MatchSummaryDecisionAdapter` and projection types | Remove immediately | Transitional Phase 7 seam has no production consumer | Only its obsolete focused test is affected |
| legacy logger entries in `application.yml` | Remove immediately | Target classes no longer execute in production | No runtime behavior impact |

The test-only items are moved from `src/main` to `src/test`, not deleted. This
keeps executable characterization and shadow comparison while ensuring they
cannot be wired or called by a production artifact.

## Components that are not legacy

| Component | Classification | Reason | Impact if removed |
|---|---|---|---|
| Discord payload data classes | Keep | Transport contract shared by renderer and webhook client | Discord rendering and delivery fail |
| `DiscordRenderer` | Keep | Canonical Discord presentation adapter | Official Discord consumer is removed |
| `DiscordWebhookClient` | Keep | Transport delivery, with no football decisions | Webhook delivery is removed |
| `MatchSummaryBuilder` and presentation data classes | Keep | Canonical Dashboard renderer and presentation contract | Dashboard rendering fails |
| `EaMatchMapper` and normalization types | Keep | Sole Anti-Corruption Layer | EA payloads cannot enter the domain |
| EA DTOs and gateways | Keep | Infrastructure input contract; they end at the ACL/orchestrator | Acquisition fails |
| `FootballMatch` and match value objects | Keep | Normalized football facts | Canonical engine has no input model |
| interpretation evaluators and `MatchInterpreter` | Keep | Single canonical decision engine | Awards, eligibility and narratives disappear |
| `MatchStoryExtractor`, `MatchStories`, `Story` | Keep | Presentation-neutral story projection | Renderers lose canonical story input |
| `RuleReference` and `DecisionEvidence` | Keep | Audit trail for every decision | Decisions become unauditable |
| service, scheduler, web, CLI, store and configuration adapters | Keep | Runtime orchestration and external adapters | Product capabilities fail |
| published-store legacy-file migration | Keep | Data migration, unrelated to football pipeline | Existing installations may lose publication history |

## Temporary technical dependencies

No production dependency on the old football pipeline remains after this
phase. Test fixtures intentionally depend on the legacy implementations to
preserve the migration evidence. Removing those fixtures should only be
considered after an explicit retention decision for shadow tests and historical
contracts; it is not required to complete the production migration.
