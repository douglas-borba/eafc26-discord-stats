# Containerization validation

The Compose stack contains two services: the Spring application and the internal
Node EA Gateway. The gateway is authenticated with `EA_GATEWAY_INTERNAL_TOKEN`,
is not published to the host, and must become healthy before Spring starts.

Canonical data remains in the `eafc-data` volume. Validate with `docker compose
up --build -d`, `/api/health`, the two service logs, and a stop/start cycle that
does not remove the volume.

The ARM64 stack was revalidated on 2026-08-08. Both services became healthy;
unauthenticated gateway access returned 401; authenticated search returned one
club; match acquisition returned 20 merged matches (10 league and 10 playoff);
and members/stats returned 19 members. A controlled canonical backfill processed
20 matches with no failures and without a configured Discord webhook.

Railway service configuration is documented in
[`railway-ea-gateway.md`](railway-ea-gateway.md).
