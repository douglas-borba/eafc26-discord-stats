# Dashboard shadow-mode divergences

## Result

The canonical Dashboard renderer has structural parity with the legacy
renderer for the rich and minimal characterized scenarios. No unexpected
Dashboard divergence remains at cutover.

The comparison includes header context, score, outcome, timestamps, goals,
assists, highlights, all awards and narratives, optional-section presence,
collection ordering, supporting metrics and deterministic phrases.

## Previously catalogued cross-consumer divergences

- **DIV-001 — Bagre in Discord highlights:** known Discord defect. Dashboard
  continues excluding Bagre. Discord remains unchanged in this phase.
- **DIV-002 — Discord offensive-story limit:** intentional Discord presentation
  constraint. Dashboard continues retaining the complete ordered collection.
- **DIV-003 — goalkeeper EA MVP in history:** known history defect. Dashboard
  continues using canonical Craque. Discord history remains unchanged.

These items are not Dashboard shadow failures and do not authorize a Discord
behavior change during Phase 9.
