# Discord webhook configuration

## Destination

The application has one Discord destination: `EAFC_DISCORD_MATCH_WEBHOOK_URL`.
It receives the complete match summary, awards and narratives. The variable is
optional at boot because local development may configure the same destination
through Setup. Resolution is deterministic:

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
It contains delivered `MatchId` values at:

`~/Library/Application Support/EAFC26DiscordStats/published-matches.json`

When this store is empty, the first normal acquisition—scheduler, manual update or
CLI—records the complete EA window as a baseline and sends nothing to Discord.
Later cycles publish only MatchIds absent from that baseline, then persist each ID
after successful delivery. Restarting the same environment therefore does not
republish a match. Force resend remains the only explicit path that bypasses this
deduplication and it does not alter the store.

Local macOS, Docker and any future deployment use independent filesystems. No
automatic copy or synchronization occurs between their publication stores. Docker
persists the Application Support directory through the `eafc-data` volume.

## Railway (future deployment)

Railway is not the active development environment. If deployment resumes, define
`EAFC_DISCORD_MATCH_WEBHOOK_URL` and mount persistent storage for the Application
Support directory. A new empty volume safely establishes a baseline without
publishing the existing EA window.
