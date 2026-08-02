# Production memory budget

## Runtime limit

The Railway Trial service runs one replica with 2 vCPU and 1 GiB of memory.
The JVM, Playwright's Node driver, Chromium processes, native allocations and
filesystem/shared-memory pages all consume that same cgroup budget.

## Baseline diagnosis

The unoptimized image was measured locally with the same `--memory=1g` and
`--cpus=2` limits. During a cold acquisition it reached 1,061,187,584 bytes
(approximately 1,012 MiB), leaving only about 12 MiB of theoretical headroom.
The cgroup recorded `memory.max` pressure events even though no local OOM kill
occurred during the observation window.

At that point the JVM was already container-aware: its maximum heap was 256 MiB
and its live heap remained far below that value. The process tree contained one
JVM, one Playwright Node driver and one browser with its Chromium subprocesses.
Playwright and the browser were initialized only once and reused across polling
cycles. This rules out duplicate browser creation as the cause and identifies
total cgroup pressure during cold Chromium startup as the relevant constraint.

## Production budget

The image uses:

```text
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError
```

For a 1 GiB cgroup this caps the Java heap at approximately 206 MiB. The cap is
percentage-based so it follows the actual container limit rather than assuming
an absolute host size. The remainder stays available to JVM non-heap/native
memory, the Node driver, Chromium and an operating margin. `ExitOnOutOfMemoryError`
ensures the platform can restart a failed process instead of leaving a partially
functional service alive.

Metaspace, thread stacks and direct memory are not artificially capped. Their
measured use did not justify those additional failure modes.

## Browser acquisition profile

The browser continues to use Playwright 1.47.0, Chromium `headless=new`, the
approved User-Agent and the initial navigation to `https://proclubs.ea.com` for
the Akamai session. Since the page is never displayed, its scripts and visual
resources are aborted during that initial navigation. The document and all
fetch/XHR traffic remain enabled. GPU/rasterization are disabled and Chromium is
limited to one renderer process because this browser is used only as an HTTP
session transport.

The real matches and members/stats calls both continued returning HTTP 200 after
these changes.

The final optimized cold-start peak under the same local 1 GiB/2 vCPU limit was
788,082,688 bytes (approximately 752 MiB). Docker's measured working-set peak
was 604.1 MiB. The stricter raw cgroup measurement therefore leaves approximately
285 MiB of headroom. The cgroup recorded no `memory.max`, OOM or OOM-kill events
during the optimized validation.

## Railway runtime configuration

Use one replica and retain the platform's 1 GiB memory and 2 vCPU limits. The
runtime command remains the image entrypoint; no Gradle or build process runs in
the final image.

Required or recommended runtime values are:

```text
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError
APP_WEB_NETWORK_ENABLED=true
EAFC_DASHBOARD_AUTO_OPEN=false
PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
EAFC_COOKIE_SECURE=true
EAFC_SESSION_TIMEOUT=8h
```

`EAFC_VIEWER_PASSWORD` and `EAFC_ADMIN_PASSWORD` remain required secrets and
must be different. No password, cookie, webhook URL or complete EA payload may
be written to logs. Parser diagnostics remain available through an explicit
temporary logging override; the production default is INFO and does not emit
one line per player/statistic.

## Validation gate

The local result does not replace Railway observation. A deployment is approved
only after several real polling cycles show HTTP 200 for both EA operations,
healthy login and pages, preserved canonical data, no exit 137/OOM-kill/restart,
and a stable Railway memory graph below the 1 GiB limit.
