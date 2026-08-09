# Production memory budget

Hosted deployment is currently optional. The Spring container retains the
container-aware JVM budget configured by `JAVA_TOOL_OPTIONS`. EA transport now
runs in the small Node gateway service, without a browser process.

Any future 1 GiB deployment must be remeasured with both services under their
real platform limits before approval. Authentication secrets and complete EA
payloads must never be written to logs.
