# Shared access security

## Model

EA FC STATS uses Spring Security WebFlux with two externally configured shared
credentials. It does not store user accounts.

| Capability | Anonymous | VIEWER | ADMIN |
|---|---:|---:|---:|
| Login and minimal health | Yes | Yes | Yes |
| Overview, matches, players, opponents and comparison | No | Yes | Yes |
| Read-only sports APIs | No | Yes | Yes |
| Settings, setup and operational status | No | No | Yes |
| Acquisition, Discord resend and simulator operations | No | No | Yes |
| Configuration, development and shutdown APIs | No | No | Yes |

Authorization is enforced by the backend. Hiding controls in the AppShell is
only a presentation consequence of the authenticated role.

## Route inventory

Public:

- `GET /login`, `GET /admin/login`, `POST /login`;
- `GET /access-denied`, `GET /session-expired`;
- `GET /api/health` (only `{"status":"UP"}`).

VIEWER and ADMIN:

- pages: `/`, `/history`, `/players`, `/opponents`, `/opponents/{clubId}`,
  `/compare`, `/match-card`, and the legacy `/insights` redirect;
- APIs: `/api/auth/session`, `/api/match-card/latest`,
  `/api/history/matches/**`, `/api/player-profiles/**`, `/api/opponents/**`, and
  `/api/match-comparisons/**`;
- authenticated static resources required by those pages.

ADMIN only:

- pages: `/settings`, `/setup`;
- APIs: `/api/settings/**`, `/api/setup/**`, `/api/polling/**`,
  `/api/matches/**`, `/api/dev/**`, and `/api/application/**`.

All routes not explicitly public require authentication. Anonymous API calls
receive JSON `401`; authenticated calls without the required role receive JSON
`403`. Browser pages redirect to the branded login or access-denied page.

## Configuration

Required environment variables:

```text
EAFC_VIEWER_PASSWORD
EAFC_ADMIN_PASSWORD
```

Both must be non-empty and different. Startup fails with a clear variable name
when either is absent. Values are BCrypt-encoded for Spring Security's in-memory
user store and are never returned or logged. Copy `.env.example` to the ignored
`.env` file for local Compose use.

Optional variables:

```text
EAFC_SESSION_TIMEOUT=8h
EAFC_COOKIE_SECURE=false
```

Set `EAFC_COOKIE_SECURE=true` whenever the application is exposed through HTTPS.
Forwarded headers are handled by Spring so proxy scheme and redirects remain
correct. Changing either password and restarting invalidates future logins; to
force existing sessions out immediately, restart the application because the
session store is in memory.

## Session and request protection

Authentication uses the normal server-side HTTP session. The session cookie is
HttpOnly, SameSite=Lax and configurable as Secure. Spring Security protects
against session fixation, stores no JWT and writes nothing to localStorage.
Logout is a CSRF-protected POST and invalidates the session. The original local
path and query string are retained through login.

CSRF remains enabled for every state-changing operation. Browser requests send
the token from the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header. Passwords,
hashes and CSRF values must never be logged.

## Known limitation

VIEWER and ADMIN credentials are shared. The system cannot identify which person
performed an action, revoke one person independently or audit by individual.
Those capabilities require individual accounts and are deliberately outside this
version.
