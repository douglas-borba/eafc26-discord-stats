import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
vi.mock("server-only", () => ({}));
vi.mock("@/lib/supabase/auth-server", () => ({ requireAdmin: vi.fn(async () => ({ kind: "allowed" })) }));
import { GET as listClubs, POST as createClub } from "@/app/api/admin/clubs/route";
import { GET as searchClubs } from "@/app/api/admin/clubs/search/route";
import { GET as getClub, DELETE as deleteClub } from "@/app/api/admin/clubs/[clubId]/route";
import { PATCH as updateMonitoring } from "@/app/api/admin/clubs/[clubId]/monitoring/route";
import { PUT as configureDiscord, DELETE as removeDiscord } from "@/app/api/admin/clubs/[clubId]/discord/route";
import { GET as getStatus } from "@/app/api/admin/clubs/[clubId]/status/route";
import { GET as getSystemHealth } from "@/app/api/admin/system/health/route";

const originalBackendUrl = process.env.BACKEND_URL;
const clubContext = { params: Promise.resolve({ clubId: "8874106" }) };

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

  it("proxies club deletion and returns 204", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await deleteClub(new Request("https://dashboard.test", { method: "DELETE" }), clubContext);

    expect(response.status).toBe(204);
    expect(fetchMock.mock.calls[1][0]).toBe("https://spring.example.test/api/admin/clubs/8874106");
    expect(fetchMock.mock.calls[1][1].method).toBe("DELETE");
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
});
