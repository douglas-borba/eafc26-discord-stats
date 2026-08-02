# Discord webhook configuration

## Destinations

The application has exactly two Discord webhook destinations.

| Variable | Required | Purpose | Masked example | When absent |
|---|---|---|---|---|
| `EAFC_DISCORD_MATCH_WEBHOOK_URL` | Optional at boot; required by current setup readiness and primary publication | Receives the complete match summary, awards and narratives | `https://discord.com/api/webhooks/<id>/<token>` | Uses the locally stored match webhook; otherwise publication is unavailable |
| `EAFC_DISCORD_HISTORY_WEBHOOK_URL` | Optional at boot; required by current setup readiness | Receives the compact chronological match-history payload | `https://discord.com/api/webhooks/<id>/<token>` | Uses the locally stored history webhook; at transport level, absent history delivery is skipped |

There is no separate test webhook. The administrative force-resend operation uses
the primary match destination and must not be invoked against a production channel
as a configuration check without explicit authorization.

The history transport is technically optional in `DiscordWebhookClient`, but the
existing first-run experience deliberately requires both destinations before an
ADMIN can leave Setup. This behavior was preserved unchanged.

## Resolution and validation

Each destination is resolved independently in this order:

1. a non-blank environment value (`ENVIRONMENT`);
2. the value saved through the administrative interface (`STORED`);
3. no value (`NOT_CONFIGURED`).

Environment-managed values cannot be changed by the interface and are never sent
to the browser. Stored values are also not returned to the browser; the interface
reports only that a local value exists. Blank environment variables deliberately
activate the stored fallback.

Configured values must be HTTPS Discord webhook URLs with an ID and token. A
non-blank invalid environment value stops application startup. The error identifies
only the variable, for example `EAFC_DISCORD_MATCH_WEBHOOK_URL is invalid.`, and
never includes the supplied value. Failing instead of silently using a stored
fallback prevents delivery to an unintended channel.

## Railway

Create the two variables in the Railway service Variables area and redeploy. Do
not place their values in repository files. After deployment, an ADMIN can open
Settings and confirm that both origins are `ENVIRONMENT`; the values remain hidden.

Safe validation sequence:

1. confirm the variables exist in the container environment without printing them;
2. confirm startup logs report `match=ENVIRONMENT, history=ENVIRONMENT`;
3. confirm no webhook URL appears in application logs;
4. confirm the ADMIN Settings page says the server controls the webhooks;
5. use a controlled non-production destination for a delivery test.

Do not invoke force-resend or publish a real match to a production channel solely
for validation without explicit authorization.

## Publication and persistence

Changing only the webhook origin does not change polling, payload rendering,
deduplication, retry or first-run behavior. `PublishedMatchStore` remains the source
of delivered `MatchId` values. With `publish-existing-on-first-run=false`, an empty
store establishes a baseline instead of retroactively publishing the returned EA
window. An externally configured webhook does not itself trigger acquisition or
publication.

The production volume has a different responsibility from environment variables:

| Data | Storage | Production persistence requirement |
|---|---|---|
| Webhook secrets | Railway environment variables | Recreated consistently on every deploy |
| Published match IDs | `~/Library/Application Support/EAFC26DiscordStats/published-matches.json` | Persistent volume |
| Canonical matches | `~/Library/Application Support/EAFC26DiscordStats/canonical-matches/` | Persistent volume |
| Custom phrases | `~/Library/Application Support/EAFC26DiscordStats/phrases.json` | Persistent volume |
| Local settings and webhook fallback | Java Preferences (`~/.java/.userPrefs` on Linux; platform preferences on macOS) | Local compatibility only; not the production source of webhook secrets |

The Compose volume mounts the Application Support directory containing canonical
matches, published IDs and custom phrases. Java Preferences are intentionally not
treated as durable container configuration; production webhooks belong in the
environment.
