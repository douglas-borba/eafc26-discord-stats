import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { timingSafeEqual } from "node:crypto";

export interface GatewayConfig {
  token: string;
  eaBaseUrl: string;
  timeoutMs: number;
}

type JsonRecord = Record<string, unknown>;

function send(res: ServerResponse, status: number, body: unknown): void {
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Content-Length": Buffer.byteLength(json) });
  res.end(json);
}

function authorized(req: IncomingMessage, token: string): boolean {
  const supplied = req.headers.authorization?.replace(/^Bearer\s+/i, "") ?? "";
  const expectedBytes = Buffer.from(token);
  const suppliedBytes = Buffer.from(supplied);
  return expectedBytes.length === suppliedBytes.length && timingSafeEqual(expectedBytes, suppliedBytes);
}

function classifyError(error: unknown): { kind: string; detail: string } {
  if (!(error instanceof Error)) return { kind: "unknown", detail: String(error) };
  if (error.name === "TimeoutError" || error.name === "AbortError") return { kind: "timeout", detail: error.message };
  const msg = error.message;
  if (msg.startsWith("EA_HTTP_")) return { kind: "ea_http_error", detail: msg };
  if (msg === "EA_INVALID_CONTENT_TYPE") return { kind: "ea_invalid_content_type", detail: msg };
  if (msg === "EA_INVALID_MATCHES") return { kind: "ea_invalid_payload", detail: msg };
  if (msg.includes("fetch failed") || msg.includes("ENOTFOUND") || msg.includes("ECONNREFUSED") || msg.includes("ECONNRESET")) {
    return { kind: "network", detail: msg };
  }
  if (msg.includes("unable to verify") || msg.includes("certificate") || msg.includes("SSL") || msg.includes("TLS")) {
    return { kind: "tls", detail: msg };
  }
  return { kind: "exception", detail: `${error.name}: ${msg}` };
}

async function eaJson(url: URL, config: GatewayConfig): Promise<unknown> {
  const response = await fetch(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(config.timeoutMs) });
  if (!response.ok) throw new Error(`EA_HTTP_${response.status}`);
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) throw new Error("EA_INVALID_CONTENT_TYPE");
  return response.json();
}

function matches(payload: unknown): JsonRecord[] {
  if (!Array.isArray(payload) || payload.some(item => item === null || typeof item !== "object" || Array.isArray(item))) {
    throw new Error("EA_INVALID_MATCHES");
  }
  return payload as JsonRecord[];
}

export function createGatewayServer(config: GatewayConfig) {
  if (!config.token) throw new Error("EA_GATEWAY_INTERNAL_TOKEN is required");
  return createServer(async (req, res) => {
    const start = Date.now();
    try {
      const url = new URL(req.url ?? "/", "http://internal");
      if (req.method === "GET" && url.pathname === "/health") return send(res, 200, { status: "ok" });
      console.log(`[req] ${req.method} ${url.pathname}${url.search}`);
      if (!authorized(req, config.token)) { console.log(`[req] 401 unauthorized`); return send(res, 401, { error: "unauthorized" }); }
      if (req.method !== "GET") return send(res, 405, { error: "method_not_allowed" });

      if (url.pathname === "/ea/clubs/search") {
        const name = url.searchParams.get("name")?.trim();
        if (!name) return send(res, 400, { error: "name_required" });
        const upstream = new URL(`${config.eaBaseUrl}/allTimeLeaderboard/search`);
        upstream.search = new URLSearchParams({ platform: url.searchParams.get("platform") ?? "common-gen5", clubName: name }).toString();
        console.log(`[upstream] GET ${upstream.origin}${upstream.pathname}${upstream.search}`);
        const result = await eaJson(upstream, config);
        console.log(`[upstream] 200 OK (${Date.now() - start}ms)`);
        return send(res, 200, result);
      }

      const match = url.pathname.match(/^\/ea\/clubs\/([^/]+)\/(matches|members)$/);
      if (!match) return send(res, 404, { error: "not_found" });
      const clubId = decodeURIComponent(match[1]);
      const platform = url.searchParams.get("platform") ?? "common-gen5";
      const maxResultCount = url.searchParams.get("maxResultCount") ?? "20";

      if (match[2] === "members") {
        const upstream = new URL(`${config.eaBaseUrl}/members/stats`);
        upstream.search = new URLSearchParams({ platform, clubId }).toString();
        console.log(`[upstream] GET ${upstream.origin}${upstream.pathname}${upstream.search}`);
        const result = await eaJson(upstream, config);
        console.log(`[upstream] 200 OK (${Date.now() - start}ms)`);
        return send(res, 200, result);
      }

      const load = async (matchType: string) => {
        const upstream = new URL(`${config.eaBaseUrl}/clubs/matches`);
        upstream.search = new URLSearchParams({ platform, clubIds: clubId, matchType, maxResultCount }).toString();
        console.log(`[upstream] GET ${upstream.origin}${upstream.pathname}${upstream.search}`);
        const result = matches(await eaJson(upstream, config));
        console.log(`[upstream] ${matchType} 200 OK ${result.length} matches (${Date.now() - start}ms)`);
        return result;
      };
      const [league, playoff] = await Promise.all([load("leagueMatch"), load("playoffMatch")]);
      const merged = [...league, ...playoff]
        .filter(item => typeof item.matchId === "string" || typeof item.matchId === "number")
        .filter((item, index, all) => all.findIndex(candidate => String(candidate.matchId) === String(item.matchId)) === index)
        .sort((a, b) => Number(b.timestamp ?? 0) - Number(a.timestamp ?? 0));
      console.log(`[req] 200 OK ${merged.length} merged matches (${Date.now() - start}ms)`);
      return send(res, 200, merged);
    } catch (error) {
      const { kind, detail } = classifyError(error);
      const message = error instanceof Error ? error.message : "EA_GATEWAY_ERROR";
      const timeout = kind === "timeout";
      const status = timeout ? 504 : 502;
      console.error(`[error] ${status} kind=${kind} detail="${detail}" (${Date.now() - start}ms)`);
      return send(res, status, { error: timeout ? "ea_timeout" : message });
    }
  });
}

if (process.env.NODE_ENV !== "test") {
  const server = createGatewayServer({
    token: process.env.EA_GATEWAY_INTERNAL_TOKEN ?? "",
    eaBaseUrl: process.env.EA_API_BASE_URL ?? "https://proclubs.ea.com/api/fc",
    timeoutMs: Number(process.env.EA_GATEWAY_TIMEOUT_MS ?? 30_000),
  });
  server.listen(Number(process.env.PORT ?? 8081), process.env.HOST ?? "127.0.0.1", () => {
    console.log(`EA gateway listening on port ${process.env.PORT ?? 8081}`);
  });
}
