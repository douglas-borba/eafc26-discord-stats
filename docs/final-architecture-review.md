# Final architecture review

## Conclusion

The migration objective is complete: production has one normalized match
model, one deterministic interpretation engine and one story projection shared
by Dashboard and Discord. Test-only legacy baselines do not enter the runtime
artifact.

## Possible simplifications

- `MatchAcquisitionService` currently normalizes and interprets the same match
  separately for Dashboard and Discord paths. A future application-level
  `CanonicalMatchPipeline` could return a small context containing
  `FootballMatch`, `MatchInterpretation` and `MatchStories` once per match.
- `DiscordRenderer` reuses `MatchSummaryBuilder` for common localized section
  formatting. A small channel-neutral localization component could make that
  sharing explicit if another renderer needs the same Portuguese copy.
- Some feature decisions could eventually share small typed metric records,
  but introducing a generic evaluation framework now would add more abstraction
  than value.

## Responsibilities to watch

- `MatchFeaturesEvaluator` contains several independent feature rules. It is
  cohesive today because it produces one feature set, but it may become too
  large as stories grow. Extract focused evaluators only when a feature gains
  meaningful complexity.
- `MatchAcquisitionService` coordinates acquisition, cache updates, delivery
  and persistence. It should remain orchestration-only; new consumer-specific
  behavior must not accumulate there.
- `MatchSummaryBuilder` contains localized copy and phrase selection. It must
  not become a second story classifier.

## Coupling opportunities

- A canonical pipeline context would remove duplicated orchestration and make
  adding consumers cheaper.
- Renderer-facing lookup helpers for player and club identity could reduce
  repeated map construction without hiding decisions.
- Delivery ports could separate rendering from Discord/webhook transport if
  multiple outbound transports appear.

## Extension points

- Optional LLM realization over semantic narrative keys and structured story
  content.
- Read-only API exposing interpretation, stories and provenance.
- Social-media renderers with their own length and media constraints.
- Rule-version comparison tooling using `RuleReference`.
- Persisted interpretation snapshots for historical re-rendering.

## Technical risks

- Phrase and localization behavior is shared implicitly between current
  renderers; careless edits may cause channel drift.
- EA payload evolution can introduce normalization fallbacks that conceal new
  anomalies unless normalization issues are monitored.
- Story ordering is observable presentation input and needs explicit tests.
- Retained test-only legacy code increases test maintenance cost; removing it
  later trades that cost for less historical executable evidence.
- Rule evidence can grow substantially. Persistence or API exposure should
  define size and privacy boundaries before storing it.

## Future evolution guidance

Do not introduce a general rule framework, event bus or plugin system until a
concrete second use case requires it. Prefer focused evaluators, immutable
decision records and explicit composition. Future work should optimize
orchestration and localization sharing without weakening the canonical
interpretation boundary.
