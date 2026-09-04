import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
vi.mock("server-only", () => ({}));
vi.mock("@/lib/supabase/auth-server", () => ({ requireAdmin: vi.fn(async () => ({ kind: "allowed", email: "admin@example.com" })) }));
import { requireAdmin } from "@/lib/supabase/auth-server";
import { GET as listClubs, POST as createClub } from "@/app/api/admin/clubs/route";
import { GET as searchClubs } from "@/app/api/admin/clubs/search/route";
import { GET as getClub, DELETE as deleteClub } from "@/app/api/admin/clubs/[clubId]/route";
import { PATCH as updateMonitoring } from "@/app/api/admin/clubs/[clubId]/monitoring/route";
import { PUT as configureDiscord, DELETE as removeDiscord } from "@/app/api/admin/clubs/[clubId]/discord/route";
import { GET as getStatus } from "@/app/api/admin/clubs/[clubId]/status/route";
import { GET as getSystemHealth } from "@/app/api/admin/system/health/route";
import { POST as resetCanonicalReadDiagnostics } from "@/app/api/admin/system/canonical-read-diagnostics/reset/route";
import { POST as forcePublish } from "@/app/api/admin/clubs/[clubId]/publication/[matchId]/force-publish/route";
import { POST as poll } from "@/app/api/admin/clubs/[clubId]/poll/route";
import { POST as testEa } from "@/app/api/admin/clubs/[clubId]/ea/test/route";
import { POST as testDiscord } from "@/app/api/admin/clubs/[clubId]/discord/test/route";
import { POST as approveTrialRequest } from "@/app/api/admin/trial-requests/[requestId]/approve/route";
import { GET as discovery } from "@/app/api/admin/explorer/clubs/[clubId]/discovery/route";
import { GET as novelMetrics } from "@/app/api/admin/explorer/clubs/[clubId]/novel-metrics/route";
import { GET as positionObservations } from "@/app/api/admin/explorer/clubs/[clubId]/players/[playerId]/position-observations/route";
import { GET as getExplorerObservations, POST as saveExplorerObservation } from "@/app/api/admin/explorer/clubs/[clubId]/matches/[matchId]/players/[playerId]/observations/route";
import { GET as compareExplorerObservations } from "@/app/api/admin/explorer/clubs/[clubId]/players/[playerId]/observation-comparison/route";
import { POST as previewImport } from "@/app/api/admin/explorer/clubs/[clubId]/observations/preview/route";
import { POST as executeImport } from "@/app/api/admin/explorer/clubs/[clubId]/observations/import/route";

const originalBackendUrl = process.env.BACKEND_URL;
const clubContext = { params: Promise.resolve({ clubId: "8874106" }) };
const publicationContext = { params: Promise.resolve({ clubId: "8874106", matchId: "960520613970171" }) };
const trialRequestContext = { params: Promise.resolve({ requestId: "42" }) };
const explorerContext = { params: Promise.resolve({ clubId: "8874106" }) };
const playerExplorerContext = { params: Promise.resolve({ clubId: "8874106", playerId: "player-1" }) };
const matchPlayerExplorerContext = { params: Promise.resolve({ clubId: "8874106", matchId: "match-1", playerId: "player-1" }) };

beforeEach(() => { process.env.BACKEND_URL = "https://spring.example.test/"; process.env.ADMIN_INTERNAL_TOKEN = "test-admin-token"; });
afterEach(() => {
  vi.unstubAllGlobals();
  if (originalBackendUrl === undefined) delete process.env.BACKEND_URL;
  else process.env.BACKEND_URL = originalBackendUrl;
});

const json = (payload: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(payload), { status, headers: { "content-type": "application/json", ...headers } });

const csrf = () => json([], 200, { "set-cookie": "XSRF-TOKEN=server%2Dtoken; Path=/; SameSite=Lax" });

describe("administrative BFF", () => {
  it("proxies the deterministic club list without browser credentials", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json([{ clubId: "1104972", discordConfigured: true }]));
    vi.stubGlobal("fetch", fetchMock);

    const response = await listClubs();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual([{ clubId: "1104972", discordConfigured: true }]);
    expect(fetchMock).toHaveBeenCalledWith("https://spring.example.test/api/admin/clubs", expect.objectContaining({ method: "GET", cache: "no-store" }));
    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("cookie")).toBeNull();
    expect(headers.get("x-xsrf-token")).toBeNull();
  });

  it("encodes and proxies EA search", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json([{ clubId: "8874106", displayName: "BRASIL 2030" }]));
    vi.stubGlobal("fetch", fetchMock);

    const response = await searchClubs(new Request("https://dashboard.test/api/admin/clubs/search?query=BRASIL%202030"));

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0][0]).toBe("https://spring.example.test/api/admin/clubs/search?query=BRASIL%202030");
  });

  it("creates a club with a server-side Spring CSRF handshake", async () => {
    const created = { clubId: "8874106", displayName: "BRASIL 2030", discordConfigured: false };
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json(created));
    vi.stubGlobal("fetch", fetchMock);
    const body = JSON.stringify({ clubId: "8874106", displayName: "BRASIL 2030", platform: "common-gen5" });

    const response = await createClub(new Request("https://dashboard.test/api/admin/clubs", { method: "POST", body }));

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const mutation = fetchMock.mock.calls[1];
    expect(mutation[0]).toBe("https://spring.example.test/api/admin/clubs");
    expect(mutation[1].method).toBe("POST");
    expect(mutation[1].body).toBe(body);
    const headers = mutation[1].headers as Headers;
    expect(headers.get("X-XSRF-TOKEN")).toBe("server-token");
    expect(headers.get("Cookie")).toBe("XSRF-TOKEN=server%2Dtoken");
  });

  it("proxies monitoring with CSRF and encoded club identity", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ clubId: "8874106", monitoringEnabled: true }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await updateMonitoring(new Request("https://dashboard.test", { method: "PATCH", body: '{"enabled":true}' }), clubContext);

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/clubs/8874106/monitoring");
    expect((fetchMock.mock.calls[1][1].headers as Headers).get("X-XSRF-TOKEN")).toBe("server-token");
  });

  it("configures and removes Discord without exposing its value", async () => {
    const secretUrl = "https://discord.com/api/webhooks/id/secret-token";
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ clubId: "8874106", discordConfigured: true, webhookUrl: secretUrl, discordWebhookSecretReference: "opaque" }))
      .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ clubId: "8874106", discordConfigured: false }));
    vi.stubGlobal("fetch", fetchMock);

    const configured = await configureDiscord(new Request("https://dashboard.test", { method: "PUT", body: JSON.stringify({ webhookUrl: secretUrl }) }), clubContext);
    const configuredBody = JSON.stringify(await configured.json());
    const removed = await removeDiscord(new Request("https://dashboard.test", { method: "DELETE" }), clubContext);

    expect(configured.status).toBe(200);
    expect(configuredBody).not.toContain("secret-token");
    expect(configuredBody).not.toContain("opaque");
    expect(removed.status).toBe(200);
    expect(fetchMock.mock.calls[3][1].method).toBe("DELETE");
  });

  it("proxies individual club and operational status", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ clubId: "8874106" }))
      .mockResolvedValueOnce(json({ clubId: "8874106", pollingStatus: "IDLE" }));
    vi.stubGlobal("fetch", fetchMock);

    expect((await getClub(new Request("https://dashboard.test"), clubContext)).status).toBe(200);
    expect((await getStatus(new Request("https://dashboard.test"), clubContext)).status).toBe(200);
    expect(fetchMock.mock.calls[1][0]).toContain("/8874106/status");
  });

  it("keeps Discovery behind the authenticated administrative BFF and forwards only bounded filters", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ analysis: { rawMatchesAnalyzed: 0 }, newAggregateDataDetected: [] }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await discovery(
      new Request("https://dashboard.test/api/admin/explorer/clubs/8874106/discovery?limit=10&aggregate=0&minimumMatches=2&minimumObservations=5"),
      explorerContext,
    );

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0][0]).toBe(
      "https://spring.example.test/api/admin/explorer/clubs/8874106/discovery?limit=10&aggregate=0&minimumMatches=2&minimumObservations=5&hideKnownRelationships=true",
    );
    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
  });

  it("keeps Novel Metric Discovery and Position Observations behind the bounded administrative BFF", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ candidates: [] }))
      .mockResolvedValueOnce(json({ coverage: "FULL", observations: [], distribution: [], distinctCodes: 0 }));
    vi.stubGlobal("fetch", fetchMock);

    expect((await novelMetrics(new Request("https://dashboard.test/api/admin/explorer/clubs/8874106/novel-metrics?limit=10&aggregateIndex=0&code=999"), explorerContext)).status).toBe(200);
    expect(fetchMock.mock.calls[0][0]).toBe("https://spring.example.test/api/admin/explorer/clubs/8874106/novel-metrics?limit=10&aggregateIndex=0&code=999");
    expect((await positionObservations(new Request("https://dashboard.test/api/admin/explorer/clubs/8874106/players/player-1/position-observations?limit=20"), playerExplorerContext)).status).toBe(200);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/explorer/clubs/8874106/players/player-1/position-observations?limit=20");
    expect((fetchMock.mock.calls[1][1].headers as Headers).get("Authorization")).toBe("Bearer test-admin-token");
  });

  it("keeps human observations internal, authenticated and scoped to club match and player", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json([]))
      .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ phrase: "Bom passe", observedCount: 4, completeness: "AT_LEAST" }))
      .mockResolvedValueOnce(json({
        phrase: "Bom passe", candidates: [{ aggregateIndex: 0, code: 112, candidateKind: "KNOWN_CONTROL", metricName: "Beats" }],
        contradictedCandidates: 0, observationCollisions: [], nextBestExperiments: [],
      }));
    vi.stubGlobal("fetch", fetchMock);

    expect((await getExplorerObservations(new Request("https://dashboard.test"), matchPlayerExplorerContext)).status).toBe(200);
    expect((await saveExplorerObservation(new Request("https://dashboard.test", { method: "POST", body: JSON.stringify({ phrase: "Bom passe", observedCount: 4 }) }), matchPlayerExplorerContext)).status).toBe(200);
    const comparison = await compareExplorerObservations(new Request("https://dashboard.test?phrase=Bom%20passe&limit=20"), playerExplorerContext);
    expect(comparison.status).toBe(200);
    await expect(comparison.json()).resolves.toMatchObject({ candidates: [{ candidateKind: "KNOWN_CONTROL", metricName: "Beats" }] });

    expect(fetchMock.mock.calls[0][0]).toContain("/api/admin/explorer/clubs/8874106/matches/match-1/players/player-1/observations");
    expect(fetchMock.mock.calls[2][0]).toContain("/api/admin/explorer/clubs/8874106/matches/match-1/players/player-1/observations");
    expect(fetchMock.mock.calls[3][0]).toContain("/observation-comparison?phrase=Bom%20passe&limit=20");
    expect((fetchMock.mock.calls[2][1].headers as Headers).get("Authorization")).toBe("Bearer test-admin-token");
  });

  it("proxies club deletion and returns 204", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await deleteClub(new Request("https://dashboard.test", { method: "DELETE" }), clubContext);

    expect(response.status).toBe(204);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/clubs/8874106");
    expect(fetchMock.mock.calls[1][1].method).toBe("DELETE");
  });

  it("force-publishes only the explicitly selected match through the server-side CSRF flow", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({ status: "success", outcome: "PUBLISHED", message: "Partida reenviada com sucesso" }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await forcePublish(new Request("https://dashboard.test", { method: "POST" }), publicationContext);

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ status: "success", outcome: "PUBLISHED" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/clubs/8874106/publication/960520613970171/force-publish");
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "POST", body: undefined });
    const headers = fetchMock.mock.calls[1][1].headers as Headers;
    expect(headers.get("X-XSRF-TOKEN")).toBe("server-token");
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
    expect(headers.get("X-Admin-Identity")).toBe("admin@example.com");
  });

  it("preserves a successful partial trial approval result from Spring", async () => {
    const result = {
      status: "approved",
      clubId: "35537",
      clubState: "TRIAL",
      snapshot: "unavailable",
      message: "Solicitação aprovada. Os dados iniciais não puderam ser carregados agora.",
    };
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json(result));
    vi.stubGlobal("fetch", fetchMock);

    const response = await approveTrialRequest(
      new Request("https://dashboard.test", { method: "POST", body: JSON.stringify({ clubId: "35537", displayName: "Qi da Topeira", platform: "common-gen5" }) }),
      trialRequestContext,
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual(result);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/trial-requests/42/approve");
  });

  it.each([
    ["poll", poll, "/api/admin/clubs/8874106/poll"],
    ["EA test", testEa, "/api/admin/clubs/8874106/ea/test"],
    ["Discord test", testDiscord, "/api/admin/clubs/8874106/discord/test"],
  ])("proxies %s through the authenticated server-side CSRF flow", async (_, handler, path) => {
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ status: "success" }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await handler(new Request("https://dashboard.test", { method: "POST" }), clubContext);

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[1][0]).toBe(`https://spring.example.test${path}`);
    const headers = fetchMock.mock.calls[1][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
    expect(headers.get("X-XSRF-TOKEN")).toBe("server-token");
  });

  it("rejects force-publish before contacting Spring when the caller is not an admin", async () => {
    vi.mocked(requireAdmin).mockResolvedValueOnce({ kind: "anonymous" });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await forcePublish(new Request("https://dashboard.test", { method: "POST" }), publicationContext);

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["poll", poll],
    ["EA test", testEa],
    ["Discord test", testDiscord],
  ])("rejects %s before contacting Spring when the caller is not an admin", async (_, handler) => {
    vi.mocked(requireAdmin).mockResolvedValueOnce({ kind: "anonymous" });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await handler(new Request("https://dashboard.test", { method: "POST" }), clubContext);

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps backend 409 to conflict error for default club", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ message: "default club" }, 409));
    vi.stubGlobal("fetch", fetchMock);

    const response = await deleteClub(new Request("https://dashboard.test", { method: "DELETE" }), clubContext);

    expect(response.status).toBe(409);
    const body = await response.json();
    expect(body.error).toBe("conflict");
  });

  it.each([
    [404, "not_found"],
    [502, "ea_unavailable"],
  ])("maps backend HTTP %s to a safe functional error", async (status, error) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ stacktrace: "internal", secret: "value" }, status)));

    const response = await getClub(new Request("https://dashboard.test"), clubContext);

    expect(response.status).toBe(status);
    const body = await response.json();
    expect(body.error).toBe(error);
    expect(JSON.stringify(body)).not.toContain("internal");
  });

  it("classifies network failures separately", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("private host failed")));

    const response = await listClubs();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({ error: "backend_unreachable", message: "Backend indisponível. Tente novamente." });
  });

  it("preserves a degraded health response instead of treating it as backend unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({
      overall: "DEGRADED",
      application: { status: "UP" },
      eaGateway: { status: "DOWN" },
    })));

    const response = await getSystemHealth();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ overall: "DEGRADED", eaGateway: { status: "DOWN" } });
  });

  it("preserves an UP health response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ overall: "UP", application: { status: "UP" } })));

    const response = await getSystemHealth();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ overall: "UP" });
  });

  it("resets only server-side canonical read diagnostics through the protected CSRF flow", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({ status: "reset", canonicalReadDiagnostics: { total: { calls: 0 } } }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await resetCanonicalReadDiagnostics();

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/system/canonical-read-diagnostics/reset");
    const headers = fetchMock.mock.calls[1][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
    expect(headers.get("X-XSRF-TOKEN")).toBe("server-token");
  });

  it("rejects diagnostic reset before contacting Spring when the caller is not an admin", async () => {
    vi.mocked(requireAdmin).mockResolvedValueOnce({ kind: "anonymous" });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await resetCanonicalReadDiagnostics();

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([401, 403])("classifies Spring HTTP %s as an internal authentication failure", async (status) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({}, status)));

    const response = await getSystemHealth();

    expect(response.status).toBe(502);
    await expect(response.json()).resolves.toMatchObject({ error: "backend_auth_error" });
  });

  it("classifies Spring HTTP 500 as a backend error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({}, 500)));

    const response = await getSystemHealth();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toMatchObject({ error: "backend_error" });
  });

  it("classifies timeouts separately from network failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(Object.assign(new Error("timed out"), { name: "TimeoutError" })));

    const response = await getSystemHealth();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toMatchObject({ error: "backend_timeout" });
  });

  it("rejects a non JSON Spring response without exposing its body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>setup</html>", { status: 200, headers: { "content-type": "text/html" } })));

    const response = await getSystemHealth();

    expect(response.status).toBe(502);
    await expect(response.json()).resolves.toMatchObject({ error: "invalid_backend_response" });
  });

  it("does not attempt a request when BACKEND_URL is absent", async () => {
    delete process.env.BACKEND_URL;
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await listClubs();

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toMatchObject({ error: "backend_not_configured" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("blocks mutation when Spring does not issue a CSRF cookie", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json([]));
    vi.stubGlobal("fetch", fetchMock);

    const response = await createClub(new Request("https://dashboard.test", { method: "POST", body: "{}" }));

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toMatchObject({ error: "csrf_unavailable" });
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("rejects unauthenticated observation preview", async () => {
    vi.mocked(requireAdmin).mockResolvedValueOnce({ kind: "anonymous" });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await previewImport(new Request("https://dashboard.test", { method: "POST", body: '{"observations":[]}' }), explorerContext);

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects unauthenticated observation import", async () => {
    vi.mocked(requireAdmin).mockResolvedValueOnce({ kind: "anonymous" });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await executeImport(new Request("https://dashboard.test", { method: "POST", body: '{"observations":[]}' }), explorerContext);

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("proxies authenticated observation preview with CSRF", async () => {
    const previewResult = { total: 1, newCount: 1, alreadyExistsCount: 0, conflictCount: 0, invalidCount: 0, records: [] };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json(previewResult));
    vi.stubGlobal("fetch", fetchMock);

    const response = await previewImport(new Request("https://dashboard.test", { method: "POST", body: '{"observations":[]}' }), explorerContext);

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const previewCall = fetchMock.mock.calls[1];
    expect(previewCall[0]).toContain("/observations/preview");
    const headers = previewCall[1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
    expect(headers.get("X-XSRF-TOKEN")).toBe("server-token");
  });

  it("proxies authenticated observation import with CSRF", async () => {
    const importResult = { inserted: 1, alreadyExisted: 0, total: 1 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json(importResult));
    vi.stubGlobal("fetch", fetchMock);

    const response = await executeImport(new Request("https://dashboard.test", { method: "POST", body: '{"observations":[]}' }), explorerContext);

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const importCall = fetchMock.mock.calls[1];
    expect(importCall[0]).toContain("/observations/import");
    const headers = importCall[1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-admin-token");
  });

  it("internal token remains server-side in import routes", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(json({ total: 0 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await previewImport(new Request("https://dashboard.test", { method: "POST", body: '{"observations":[]}' }), explorerContext);
    const body = await response.text();
    expect(body).not.toContain("test-admin-token");
  });
});
