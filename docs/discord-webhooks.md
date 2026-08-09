# Discord webhook configuration

## Destination

Discord is an optional destination per monitored club. The current legacy
Associação BF adapter resolves `EAFC_DISCORD_MATCH_WEBHOOK_URL`; future secret
stores plug into the same opaque-reference boundary without exposing raw URLs to
application services. The legacy variable is optional at boot because local
development may configure the same destination through Setup. Its resolution is:

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

Railway is not the active development environment. If deployment resumes, define
`EAFC_DISCORD_MATCH_WEBHOOK_URL` and mount persistent storage for the Application
Support directory. A new empty volume safely establishes a baseline without
publishing the existing EA window.
