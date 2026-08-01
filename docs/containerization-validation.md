# Containerization validation

## Current decision

**NOT APPROVED FOR MERGE.**

The image and runtime contracts are implemented on `feature/containerization`,
but the required container execution could not be performed on the validation
host because no Docker, Podman, Colima, OrbStack or compatible container runtime
is installed. The main branch remains unchanged.

## Validated without a container daemon

- the complete JVM test suite passes;
- `compose.yml` is valid YAML;
- `bootJar` is generated successfully;
- the official `mcr.microsoft.com/playwright/java:v1.47.0-noble` manifest exists
  for both Linux amd64 and arm64;
- the external network setting binds Spring to `0.0.0.0` and `/api/health`
  responds successfully;
- Playwright and application versions are pinned to 1.47.0 and Java 21;
- the macOS preference-based binding remains covered by its existing tests.

## Mandatory validation before merge

On a host with Docker and Compose, run:

```bash
docker compose up --build -d
curl --fail http://localhost:8080/api/health
docker compose logs app | grep "EA API response: status=200"
docker compose ps
docker compose down
```

Approval requires all commands to succeed, the EA response to contain HTTP 200,
and the application logs to show clean Playwright shutdown without orphaned
Chromium processes. Linux/Akamai behavior must not be inferred from the macOS
gateway validation.
