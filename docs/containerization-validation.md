# Containerization validation

The Compose stack contains two services: the Spring application and the internal
Node EA Gateway. The gateway is authenticated with `EA_GATEWAY_INTERNAL_TOKEN`,
is not published to the host, and must become healthy before Spring starts.

Canonical data remains in the `eafc-data` volume. Validate with `docker compose
up --build -d`, `/api/health`, the two service logs, and a stop/start cycle that
does not remove the volume.
