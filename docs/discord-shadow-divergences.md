# Discord shadow-mode divergences

## Result

The canonical Discord renderer has exact structural parity with the legacy
renderer for the rich, minimal and Virtual Pro scenarios. Both the primary
match payload and the history payload are compared.

The comparison covers embed count, title, description, color, complete ordered
field values, section presence, separators, emojis, footer and timestamp.
The current payload model has no author, thumbnail or image properties, so
those elements are also equal by absence.

## Classified legacy differences

- **DIV-001 — Bagre in Discord highlights:** **known defect preserved**.
  The canonical interpretation exposes both the positive-award highlights and
  the complete eligible outfield ranking. Discord renders the latter, retaining
  its existing behavior without recomputing the ranking.
- **DIV-002 — maximum two offensive stories:** **intentional difference**.
  `MatchStories` retains the full ordered collection and `DiscordRenderer`
  applies the channel-specific limit of two.
- **DIV-003 — goalkeeper EA MVP in history:** **known defect preserved**.
  A separately audited `recognition.ea-mvp` decision retains the legacy history
  output. It is deliberately not confused with the canonical Craque award.

No migration bug or unclassified divergence remains at cutover.
