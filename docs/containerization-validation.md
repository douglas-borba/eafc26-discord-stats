# Containerization validation

## Decision

**APPROVED FOR MERGE.**

The containerization on `feature/containerization` was validated end to end on
2026-08-01. The application ran inside a Linux ARM64 container, Chromium ran in
headless mode through Playwright, the Akamai session was established, and the
real EA endpoint returned HTTP 200.

## Environment

- host: macOS ARM64;
- container runtime: OrbStack using the `orbstack` Docker context;
- Docker Engine: 29.4.0;
- Docker Compose: v5.1.2;
- container platform: Linux ARM64 (`aarch64`);
- application image: `ea-fc-stats:local`;
- runtime base: `mcr.microsoft.com/playwright/java:v1.47.0-noble`;
- runtime Chromium: 129.0.6668.29, supplied by Playwright 1.47.0;
- JVM: Eclipse Temurin 21.

## Runtime evidence

- Spring Boot started on port 8080;
- `/api/health` returned `{"status":"ok"}`;
- Docker reported the application container as `healthy`;
- Playwright initialized with `headless=true` under the unprivileged `pwuser`;
- Chromium was launched with `--headless=new` and without `--headless=old`;
- the gateway navigated to `https://proclubs.ea.com` before requesting data, to
  establish the Akamai session;
- the real EA match request returned HTTP 200 with a 129,085-byte payload;
- the payload contained 10 matches, all represented in canonical persistence;
- the members/stats request also returned HTTP 200, with 16 members parsed and
  14 Virtual Pro names loaded;
- the acquisition finished in `COMPLETED` with no scheduler error.

The effective Chromium launch also included `--no-sandbox`,
`--disable-dev-shm-usage`, `--remote-debugging-pipe`, and
`--no-startup-window`. Compose provides host IPC, and `/dev/shm` exposed 5.9 GiB
inside the container.

## Persistence evidence

The named volume `eafcstats_eafc-data` contained 10 `CanonicalMatch` files before
restart. The stack was stopped with `docker compose down`, without removing the
volume, then started again. After the second successful acquisition and health
check, the same 10 canonical matches remained available. No backfill or Discord
publication was triggered for this validation.

## Shutdown evidence

The final shutdown uses `docker compose down`. The application container and
Compose network are removed, no Compose services or orphan containers remain,
and the named data volume is preserved. Playwright and Chromium execute only
inside the application container and terminate with it.

## Compatibility

The Linux-specific runtime uses the official Playwright ARM64 image and enables
external web binding through container configuration. It does not replace the
validated macOS launcher or application bundle flow. The complete JVM suite and
a native macOS startup/health smoke test must remain green before merge.
