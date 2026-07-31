# Phase 0 — Known presentation divergences

This catalog records differences observed before the canonical interpretation
migration. It describes current behavior and does not authorize a production
change during Phase 0.

## DIV-001 — Bagre appears in Discord highlights

**Disposition:** KNOWN DEFECT

`MatchSummaryBuilder` excludes the selected Bagre from the web card highlights.
`DiscordEmbedBuilder` builds its Top 3 from all eligible outfield players, so the
Bagre may still appear in `DESTAQUES`.

**Behavioral proof:** `Bagre is excluded from web highlights but currently remains in Discord highlights`.

**Migration decision:** The canonical interpretation must apply the positive-award
exclusion once. The final desired Top 3 behavior will be selected when the
renderer migration is planned; Phase 0 preserves both existing outputs.

## DIV-002 — Discord caps offensive narratives at two

**Disposition:** INTENTIONAL PRESENTATION DIFFERENCE

`MatchSummaryPresentation` retains every offensive narrative returned by the
evaluator. Discord intentionally renders only the first two because channel
space is a presentation constraint.

**Behavioral proof:** `dashboard keeps all offensive narratives while Discord intentionally caps them at two`.

**Migration decision:** Keep the complete story collection in the canonical
model. The Discord renderer owns the two-story display limit.

## DIV-003 — History can report goalkeeper EA MVP instead of card Craque

**Disposition:** KNOWN DEFECT

The web card chooses Craque from eligible outfield players. `HistoryEmbedBuilder`
searches all eligible players for the EA `manOfTheMatch` flag, including the
goalkeeper. A goalkeeper EA MVP can therefore differ from the card Craque.

**Behavioral proof:** `history currently reports goalkeeper EA MVP while card Craque remains outfield`.

**Migration decision:** The history renderer must eventually consume the
canonical Craque story. EA recognition may be shown separately if the product
wants to retain it.
