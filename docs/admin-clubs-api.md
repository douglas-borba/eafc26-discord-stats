# Monitored clubs administration API

The administrative API manages monitored EA clubs at runtime. `clubId` is always
the official EA identifier. Responses expose only whether Discord is configured;
webhook URLs and opaque secret references are never returned.

| Method | Path | Request | Success | Main errors |
|---|---|---|---|---|
| GET | `/api/admin/clubs` | — | summaries ordered by `clubId` | — |
| GET | `/api/admin/clubs/{clubId}` | — | club summary | `404` unknown club |
| GET | `/api/admin/clubs/search?query=...` | — | EA selection candidates | `400` blank query, `502` EA unavailable |
| POST | `/api/admin/clubs` | `clubId`, `displayName`, `platform`, optional `monitoringEnabled` | idempotent club summary | `400` invalid body, `403` missing CSRF |
| PATCH | `/api/admin/clubs/{clubId}/monitoring` | `enabled` | updated summary | `404`, `403` |
| PUT | `/api/admin/clubs/{clubId}/discord` | `webhookUrl` | updated summary | `400` invalid URL, `404`, `403` |
| DELETE | `/api/admin/clubs/{clubId}/discord` | — | updated summary | `404`, `403` |
| GET | `/api/admin/clubs/{clubId}/status` | — | scoped operational status | `404` unknown club |

Club summaries contain `clubId`, `displayName`, `platform`, `monitoringEnabled`
and `discordConfigured`. Search candidates additionally contain the optional EA
`currentDivision`. Operational status combines the existing polling, acquisition
and latest-match in-memory states without adding a second status persistence.

Mutations use the existing cookie-based CSRF mechanism (`XSRF-TOKEN` cookie and
`X-XSRF-TOKEN` header). Registration and monitoring changes are observed by the
next coordinator cycle; no process restart or environment change is required.

Webhook configuration is optional. The raw URL is validated and stored by the
secret store. `monitored_clubs.discord_webhook_secret_ref` contains only an opaque
reference. Removing a webhook does not remove the club or its history.
