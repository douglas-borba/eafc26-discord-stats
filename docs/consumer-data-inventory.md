# Consumer data inventory

This Phase 8 inventory is retained as migration history. The “current source”
column records the pre-cutover source; Dashboard and Discord now consume the
canonical destinations exclusively.

| Information | Current source | Canonical destination | Owner |
|---|---|---|---|
| Match ID | `MatchResponse.matchId` | `FootballMatch.id` | Domain |
| Match timestamp | `MatchResponse.timestamp` | `FootballMatch.playedAt` | Domain |
| Competition/match type | `MatchResponse.matchType` | `FootballMatch.competition` | Domain |
| Perspective club ID | runtime configuration | `MatchInterpretation.perspectiveClubId` | Application |
| Our club identity/name | `MatchResponse.clubs` | `FootballMatch.participants.club` | Domain |
| Opponent identity/name | `MatchResponse.clubs` | `FootballMatch.participants.club` | Domain |
| Our and opponent scores | club DTO strings | `MatchInterpretation.result` | Engine |
| Win/draw/loss | `MatchOutcomeResolver` in each renderer | `MatchInterpretation.result` | Engine |
| Outcome decision source | implicit resolver behavior | `ResultDecision.decidedBy` | Engine |
| Localized outcome label, emoji and color | Discord/presentation constants | canonical presentation model/renderer | Presentation |
| Localized date formats and time zone | renderer `DateTimeFormatter` | renderer from `FootballMatch.playedAt` | Presentation |
| Player canonical ID | player map key | `PlayerIdentity.id` | Domain |
| Platform and Virtual Pro names | `PlayerEntry` plus members lookup | `PlayerIdentity` populated by ACL | Domain |
| Unknown/BOT display-name fallback | renderer helpers | canonical presentation model/renderer | Presentation |
| Player role/goalkeeper status | EA position string | `PlayerRole` | Domain |
| Participation duration/status | EA strings | `Participation` | Domain |
| Statistical eligibility | `PlayerStatisticsEligibility` | `MatchInterpretation.eligibility` | Engine |
| Goals by player | eligible `PlayerEntry.goals` | `MatchInterpretation.features.contributions` | Engine |
| Assists by player | eligible `PlayerEntry.assists` | `MatchInterpretation.features.contributions` | Engine |
| Goal/assist section ordering | renderer sorting | contribution decision order | Engine |
| Player rating | `PlayerEntry.rating` | `PlayerMatchPerformance.rating` | Domain |
| Team average rating | recalculated by renderers | `MatchInterpretation.teamMetrics` | Engine |
| Top-three highlights | renderer sorting; Dashboard excludes Bagre | `MatchInterpretation.features.highlights` | Engine |
| Highlight medals and formatted rating | hard-coded in renderers | canonical presentation model/renderer | Presentation |
| EA man-of-the-match recognition | `PlayerEntry.manOfTheMatch` | `EaRecognition` | Domain |
| Craque winner/reason | `CraqueSelector` | `MatchInterpretation.awards.craque` | Engine |
| Bagre winner/reason | `BagrePerformanceEvaluator` | `MatchInterpretation.awards.bagre` | Engine |
| Bagre supporting pass/tackle/rating facts | evaluator reparses DTO | `FootballMatch` plus award evidence | Domain/Engine |
| Xerife winner and defensive score | `XerifeSelector` | `MatchInterpretation.awards.xerife` | Engine |
| Offensive narrative candidate/category | `OffensiveNarrativeEvaluator` | `MatchInterpretation.features.offensiveNarratives` | Engine |
| Offensive title, emoji and message | `AttackingThreatPresenter` | narrative key rendered by presentation | Presentation |
| Discord maximum of two offensive stories | `take(2)` | renderer policy over complete `MatchStories` | Presentation |
| Red-card subject/count | `RedCardEvaluator` | `MatchInterpretation.features.redCard` | Engine |
| Red-card phrase | phrase bank | narrative key rendered by presentation | Presentation |
| Best passing-accuracy winner/sample | `PassePrecisaoSelector` | `MatchInterpretation.features.passPrecision` | Engine |
| Lost-mail winner/sample/team delta | `CorreioExtraviadoSelector` | `MatchInterpretation.features.lostMail` | Engine |
| Team passing totals/accuracy | recalculated by selector | `MatchInterpretation.teamMetrics` and lost-mail evidence | Engine |
| Goalkeeper identity | renderer chooses longest-playing goalkeeper | `MatchInterpretation.features.goalkeeper` | Engine |
| Saves and goals conceded | `PlayerEntry` | `GoalkeepingStats` and goalkeeper decision | Domain/Engine |
| Save-type breakdown | `PlayerEntry` | `GoalkeepingStats.saveBreakdown` | Domain |
| Goalkeeper archetype | `GoalkeeperEvaluator` | `MatchInterpretation.features.goalkeeper` | Engine |
| Goalkeeper localized title/message | Discord enum and phrase pools | narrative key rendered by presentation | Presentation |
| User-customized phrase selection | `PhraseBank` and deterministic hash/random simulator | canonical presentation model/renderer | Presentation |
| Section presence | null/empty results from legacy selectors | presence of canonical decisions/stories | Engine/Stories |
| Section order and separators | hard-coded renderer order | canonical presentation model/renderer | Presentation |
| Discord field names, inline flags and payload shape | `DiscordEmbedBuilder` | Discord renderer | Presentation |
| History result emoji | `HistoryEmbedBuilder` | history renderer from outcome story | Presentation |
| History MVP | direct EA MVP lookup, including goalkeeper | `EA_RECOGNIZED_MVP` story | Engine/Stories |
| ISO timestamp | `MatchResponse.timestamp` | renderer from `FootballMatch.playedAt` | Presentation |

## Explicit ownership decisions

- `FootballMatch` owns source facts and renderable identity context. The ACL
  already accepts the Virtual Pro name lookup and stores the resolved names in
  `PlayerIdentity`.
- `MatchInterpretation` owns every selection, classification, ordering and
  aggregate that carries football meaning.
- `MatchStories` owns which interpreted decisions are narratively available,
  their semantic keys and their complete provenance.
- A canonical presentation model is required for localized labels, emojis,
  colors, phrases, number/date formatting, section order and channel limits.
  The current renderers apply those presentation concerns directly over the
  canonical inputs.

There are no unowned input facts after this assignment. The known Dashboard
highlight behavior is represented by filtered and unfiltered canonical
highlight projections. History EA recognition remains an explicitly audited
decision separate from Craque so the characterized payload can be preserved.
