import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { timingSafeEqual } from "node:crypto";

export interface GatewayConfig {
  token: string;
  eaBaseUrl: string;
  timeoutMs: number;
  /**
   * Optional structured diagnostic sink. Production uses the safe JSON logger;
   * tests use this hook to verify telemetry without asserting rendered logs.
   */
  telemetry?: (event: GatewayTelemetry) => void;
}

type JsonRecord = Record<string, unknown>;

export type GatewayTelemetry = MatchFetchTelemetry | MatchMergeTelemetry;

export type MatchFetchTelemetry = {
  event: "EA_MATCH_FETCH";
  gatewayBuildSha: string | null;
  clubId: string;
  platform: string;
  matchType: string;
  maxResultCount: string;
  status: number | null;
  returnedCount: number | null;
  matchIds: string[];
  errorKind?: string;
};

export type MatchMergeTelemetry = {
  event: "EA_MATCH_MERGE";
  gatewayBuildSha: string | null;
  clubId: string;
  platform: string;
  maxResultCount: string;
  leagueCount: number;
  playoffCount: number;
  friendlyCount: number;
  mergedCount: number;
  mergedMatchIds: string[];
};

type EaJsonResponse = { payload: unknown; status: number };

class EaHttpError extends Error {
  constructor(readonly status: number) {
    super(`EA_HTTP_${status}`);
  }
}

class EaPayloadError extends Error {
  constructor(readonly status: number, code: "EA_INVALID_CONTENT_TYPE" | "EA_INVALID_JSON") {
    super(code);
  }
}

function send(res: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  const json = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(json),
    ...headers,
  });
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

const EA_HEADERS: Record<string, string> = {
  Accept: "application/json",
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  Origin: "https://www.ea.com",
  Referer: "https://www.ea.com/",
};

async function eaJson(url: URL, config: GatewayConfig): Promise<EaJsonResponse> {
  const response = await fetch(url, { headers: EA_HEADERS, signal: AbortSignal.timeout(config.timeoutMs) });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    const preview = body.slice(0, 200);
    console.error(`[upstream] HTTP ${response.status} from ${url.pathname}${url.search}${preview ? ` body=${preview}` : ""}`);
    throw new EaHttpError(response.status);
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) throw new EaPayloadError(response.status, "EA_INVALID_CONTENT_TYPE");
  try {
    return { payload: await response.json(), status: response.status };
  } catch {
    throw new EaPayloadError(response.status, "EA_INVALID_JSON");
  }
}

function matches(payload: unknown): JsonRecord[] {
  if (!Array.isArray(payload) || payload.some(item => item === null || typeof item !== "object" || Array.isArray(item))) {
    throw new Error("EA_INVALID_MATCHES");
  }
  return payload as JsonRecord[];
}

function gatewayBuildSha(): string | null {
  return process.env.RAILWAY_GIT_COMMIT_SHA ?? process.env.GIT_COMMIT_SHA ?? null;
}

function emitTelemetry(config: GatewayConfig, event: GatewayTelemetry): void {
  if (config.telemetry) {
    config.telemetry(event);
    return;
  }
  console.info(JSON.stringify(event));
}

function matchIds(items: JsonRecord[]): string[] {
  return items
    .map(item => item.matchId)
    .filter((matchId): matchId is string | number => typeof matchId === "string" || typeof matchId === "number")
    .map(String);
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
        const result = (await eaJson(upstream, config)).payload;
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
        const result = (await eaJson(upstream, config)).payload;
        console.log(`[upstream] 200 OK (${Date.now() - start}ms)`);
        return send(res, 200, result);
      }

      const load = async (matchType: string) => {
        const upstream = new URL(`${config.eaBaseUrl}/clubs/matches`);
        upstream.search = new URLSearchParams({ platform, clubIds: clubId, matchType, maxResultCount }).toString();
        console.log(`[upstream] GET ${upstream.origin}${upstream.pathname}${upstream.search}`);
        let status: number | null = null;
        try {
          const upstreamResponse = await eaJson(upstream, config);
          status = upstreamResponse.status;
          const result = matches(upstreamResponse.payload);
          emitTelemetry(config, {
            event: "EA_MATCH_FETCH",
            gatewayBuildSha: gatewayBuildSha(),
            clubId,
            platform,
            matchType,
            maxResultCount,
            status,
            returnedCount: result.length,
            matchIds: matchIds(result),
          });
          console.log(`[upstream] ${matchType} ${status} OK ${result.length} matches (${Date.now() - start}ms)`);
          return result;
        } catch (error) {
          const httpStatus = error instanceof EaHttpError || error instanceof EaPayloadError ? error.status : status;
          emitTelemetry(config, {
            event: "EA_MATCH_FETCH",
            gatewayBuildSha: gatewayBuildSha(),
            clubId,
            platform,
            matchType,
            maxResultCount,
            status: httpStatus,
            returnedCount: null,
            matchIds: [],
            errorKind: classifyError(error).kind,
          });
          throw error;
        }
      };
      const [league, playoff, friendly] = await Promise.all([
        load("leagueMatch"),
        load("playoffMatch"),
        load("friendlyMatch"),
      ]);
      const merged = [...league, ...playoff, ...friendly]
        .filter(item => typeof item.matchId === "string" || typeof item.matchId === "number")
        .filter((item, index, all) => all.findIndex(candidate => String(candidate.matchId) === String(item.matchId)) === index)
        .sort((a, b) => Number(b.timestamp ?? 0) - Number(a.timestamp ?? 0));
      emitTelemetry(config, {
        event: "EA_MATCH_MERGE",
        gatewayBuildSha: gatewayBuildSha(),
        clubId,
        platform,
        maxResultCount,
        leagueCount: league.length,
        playoffCount: playoff.length,
        friendlyCount: friendly.length,
        mergedCount: merged.length,
        mergedMatchIds: matchIds(merged),
      });
      console.log(`[req] 200 OK ${merged.length} merged matches (${Date.now() - start}ms)`);
      return send(res, 200, merged, {
        "X-EA-League-Match-Count": String(league.length),
        "X-EA-Playoff-Match-Count": String(playoff.length),
        "X-EA-Friendly-Match-Count": String(friendly.length),
      });
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
    console.log(`EA gateway listening on port ${process.env.PORT ?? 8081}; buildSha=${gatewayBuildSha() ?? "unknown"}`);
  });
}
