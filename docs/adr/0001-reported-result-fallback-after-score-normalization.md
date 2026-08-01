# ADR 0001: Reported-result fallback after mandatory score normalization

- Status: Accepted
- Date: 2026-07-30

## Context

The legacy `MatchOutcomeResolver` accepts missing or invalid score fields and,
in that situation, falls back to the result reported by EA.

Phase 2 introduced the EA anti-corruption layer. The ACL must always produce a
valid `FootballMatch`, whose participant scores are non-null `Score` values.
When an EA score cannot be recovered, the ACL normalizes it to `Score(0)` and
records a normalization issue.

Consequently, `MatchOutcomeEvaluator.evaluate(FootballMatch, ClubId)` can no
longer reach the reported-result fallback: normalized scores are always
present.

## Decision

Keep the score-optional `MatchOutcomeEvaluator.resolve` entry point and the
`REPORTED_RESULT_FALLBACK` decision source during the incremental migration.
The normalized `FootballMatch` entry point continues to use the scoreboard as
the authoritative source.

This preserves the characterized legacy rule without changing production
behavior while the new interpretation path is not yet integrated. It also
keeps the difference explicit and testable rather than silently deleting
behavior before parity can be assessed.

## Consequences

- The fallback is intentionally unreachable through a normalized
  `FootballMatch`.
- Direct tests continue to specify the legacy fallback behavior.
- A normalized `0-0` produced from irrecoverable scores is interpreted as a
  draw; the related ACL normalization issues are the audit trail for that
  fallback.
- The temporary optional-score entry point adds API surface that should not be
  treated as part of the final domain model.

## Review and removal criteria

Revisit this decision when the canonical interpretation pipeline is connected
to production and replaces `MatchOutcomeResolver`.

The fallback may be removed only after:

1. parity tests cover production payloads with absent, invalid and valid
   scores;
2. the team explicitly decides whether an irrecoverable score should remain a
   normalized `0`, make match normalization fail, or be represented as absent;
3. the chosen behavior is documented as a deliberate behavior change; and
4. no active production path depends on the legacy resolver fallback.

If the score model becomes optional before then, the fallback may instead be
promoted back into the canonical `FootballMatch` evaluation path.
