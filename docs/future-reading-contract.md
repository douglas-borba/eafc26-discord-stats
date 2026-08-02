# Future Frontend Reading Contract

> This document describes the queries the PostgreSQL `canonical_matches` table
> must support for a future Next.js frontend on Vercel. **No endpoints exist yet.**

## Planned Views

### Overview (Dashboard)
- Latest match summary
- Win/draw/loss counts
- Recent form (last N matches)
- **Query**: `SELECT payload FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC LIMIT ?`

### Match History
- Paginated list ordered by date
- Filter by match_type
- **Query**: `SELECT payload FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC LIMIT ? OFFSET ?`

### Match Detail
- Full canonical match by ID
- **Query**: `SELECT payload FROM canonical_matches WHERE match_id = ?`

### Players
- Aggregated from JSONB payload across matches
- Extract via: `payload->'footballMatch'->'participants'->0->'players'`

### Player Profile
- Filter matches containing a specific player
- Extract stats from JSONB per match

### Opponents
- Distinct opponent clubs with match count
- **Query**: `SELECT opponent_club_id, count(*) FROM canonical_matches WHERE club_id = ? GROUP BY opponent_club_id`

### Head-to-Head (Opponent Retrospect)
- All matches against a specific opponent
- **Query**: `SELECT payload FROM canonical_matches WHERE club_id = ? AND opponent_club_id = ? ORDER BY played_at DESC`

## Security Model

The future frontend will use a **read-only** database credential.
The collector's write credential must never be exposed to the frontend.

## Data Sufficiency

The JSONB `payload` column stores the complete `CanonicalMatch`, which includes:
- `FootballMatch` (players, stats, scores, competition)
- `MatchInterpretation` (awards, eligibility, team metrics, features)
- `MatchStories` (narrative elements)

All planned views can be derived from the payload without additional tables.
