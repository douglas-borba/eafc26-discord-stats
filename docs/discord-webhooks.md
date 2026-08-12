# Discord webhook configuration

## Destination

Discord is an optional destination per monitored club. With PostgreSQL enabled,
each configured destination is stored in `discord_webhook_secrets`, separate from
the club row, encrypted with AES-256-GCM. The club row holds only an opaque
reference. The encryption key is `EAFC_DISCORD_SECRET_ENCRYPTION_KEY`, encoded as
Base64 for exactly 32 bytes; it is required whenever
`EAFC_POSTGRES_MIRROR_ENABLED=true` and must remain stable across deploys.

Without PostgreSQL, the local adapter resolves
`EAFC_DISCORD_MATCH_WEBHOOK_URL`; local development may configure the same
destination through Setup. Its resolution is:

1. a non-blank environment value (`ENVIRONMENT`);
2. the value saved locally (`STORED`);
3. no value (`NOT_CONFIGURED`).

Configured values must be HTTPS URLs under `discord.com/api/webhooks` containing
an ID and token. Environment and stored secrets are never returned to the browser
or written to logs. Environment-managed values cannot be changed by the UI.

The former `EAFC_DISCORD_HISTORY_WEBHOOK_URL` and the legacy Java Preferences key
`discord.history-webhook.url` are ignored. Existing legacy values are deliberately
not migrated, copied, used or automatically deleted.

## Publication baseline and deduplication

`PublishedMatchStore` is the source of truth for Discord publication deduplication.
Its identity is `(ClubId, MatchId)` and each club's WAL records live at:

`~/Library/Application Support/EAFC26DiscordStats/clubs/{clubId}/published-matches.json`

The former root file is preserved and imported idempotently into club `1104972`
on first access. Existing scoped records win collisions, so newer operational
state cannot be replaced by legacy data.

When this store is empty, the first normal acquisition—scheduler, manual update or
CLI—records the complete EA window as a baseline and sends nothing to Discord.
Later cycles publish only MatchIds absent from that baseline, then persist each ID
after successful delivery. Restarting the same environment therefore does not
republish a match. Force resend remains the explicit path that bypasses the
existing state and records the result in that same club namespace.

Local macOS, Docker and any future deployment use independent filesystems. No
automatic copy or synchronization occurs between their publication stores. Docker
persists the Application Support directory through the `eafc-data` volume.

## Railway (future deployment)

Railway is not the active development environment. If deployment resumes with
PostgreSQL, set `EAFC_DISCORD_SECRET_ENCRYPTION_KEY`; the database stores durable,
ciphertext-only per-club destinations. Do not rotate or lose this key without a
planned secret migration. Existing `preferences:club:*` references whose local
secret has already disappeared are intentionally shown as requiring
reconfiguration; no webhook is guessed or copied automatically.
