# Railway deployment with the EA Gateway

Deploy two services from the same repository and Railway environment.

## EA Gateway service

- Railway root directory: `apps/ea-gateway`
- Dockerfile: `Dockerfile` within that root
- fixed service variable: `PORT=8081`
- secret: `EA_GATEWAY_INTERNAL_TOKEN`
- healthcheck path: `/health`
- no public domain is required

The gateway listens on the dual-stack address `::` and is reached only through
Railway private networking. `EA_API_BASE_URL` and `EA_GATEWAY_TIMEOUT_MS` are
optional operational overrides; their defaults target the production EA API and
use a 30-second timeout.

## Spring service

- Railway root directory: repository root
- Dockerfile: `Dockerfile`
- target port (or fixed service variable): `PORT=8080`
- healthcheck path: `/api/health`
- public domain: enabled for the web application

Configure `EA_GATEWAY_BASE_URL` with a Railway reference variable:

```text
http://${{ea-gateway.RAILWAY_PRIVATE_DOMAIN}}:${{ea-gateway.PORT}}
```

Configure the same sealed `EA_GATEWAY_INTERNAL_TOKEN` value in both services.

Spring variables required for the canonical PostgreSQL mirror are
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, and `EAFC_POSTGRES_MIRROR_ENABLED=true`.
`EAFC_DISCORD_MATCH_WEBHOOK_URL` is optional. LLM and periodic PostgreSQL sync
variables remain optional and disabled by default.

## Public monitoring

The Next.js dashboard exposes `GET /api/health` as the public system
healthcheck. It reads `BACKEND_URL` on the server and checks the Spring
`GET /api/health` endpoint without forwarding browser cookies or headers. A
healthy Vercel response therefore verifies the Vercel to Spring Railway link;
failures return only `{"status":"DOWN"}` (or
`backend_not_configured`) without internal connection details.

The current application does not read the historical
`EAFC_VIEWER_PASSWORD`, `EAFC_ADMIN_PASSWORD`, `EAFC_COOKIE_SECURE`, or
`EAFC_SESSION_TIMEOUT` names. Do not configure them as if they protected this
baseline.
