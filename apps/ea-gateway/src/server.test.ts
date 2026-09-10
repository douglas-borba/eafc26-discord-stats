import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { afterEach, test } from "node:test";
import { createGatewayServer, type GatewayTelemetry } from "./server.js";

const servers: Server[] = [];
const token = "test-internal-token";

async function listen(server: Server): Promise<string> {
  servers.push(server);
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("missing address");
  return `http://127.0.0.1:${address.port}`;
}

afterEach(async () => {
  await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve()))));
});

async function fixture(handler: (url: URL) => { status?: number; contentType?: string; body: string; delay?: number }) {
  return listen(createServer(async (req, res) => {
    const response = handler(new URL(req.url ?? "/", "http://ea"));
    if (response.delay) await new Promise(resolve => setTimeout(resolve, response.delay));
    res.writeHead(response.status ?? 200, { "Content-Type": response.contentType ?? "application/json" });
    res.end(response.body);
  }));
}

async function gateway(eaBaseUrl: string, timeoutMs = 1_000, telemetry?: (event: GatewayTelemetry) => void) {
  return listen(createGatewayServer({ token, eaBaseUrl, timeoutMs, telemetry }));
}

const auth = { Authorization: `Bearer ${token}` };

test("health is public and internal endpoints require bearer authentication", async () => {
  const base = await gateway(await fixture(() => ({ body: "[]" })));
  assert.equal((await fetch(`${base}/health`)).status, 200);
  assert.equal((await fetch(`${base}/ea/clubs/search?name=BF`)).status, 401);
  assert.equal((await fetch(`${base}/ea/clubs/search?name=BF`, { headers: { Authorization: "Bearer wrong" } })).status, 401);
});

test("search forwards the expected EA query and payload", async () => {
  const ea = await fixture(url => {
    assert.equal(url.pathname, "/allTimeLeaderboard/search");
    assert.equal(url.searchParams.get("clubName"), "Associação BF");
    return { body: '[{"clubId":"1104972","name":"Associação BF"}]' };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/search?name=${encodeURIComponent("Associação BF")}`, { headers: auth });
  assert.equal(response.status, 200);
  assert.equal((await response.json() as unknown[]).length, 1);
});

test("matches requests leagueMatch", async () => {
  const requested: string[] = [];
  const ea = await fixture(url => {
    requested.push(url.searchParams.get("matchType") ?? "");
    return { body: url.searchParams.get("matchType") === "leagueMatch" ? '[{"matchId":"league","timestamp":1}]' : "[]" };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/1104972/matches`, { headers: auth });
  assert.equal(response.status, 200);
  assert.ok(requested.includes("leagueMatch"));
  assert.deepEqual((await response.json() as Array<{ matchId: string }>).map(it => it.matchId), ["league"]);
});

test("matches requests playoffMatch", async () => {
  const requested: string[] = [];
  const ea = await fixture(url => {
    requested.push(url.searchParams.get("matchType") ?? "");
    return { body: url.searchParams.get("matchType") === "playoffMatch" ? '[{"matchId":"playoff","timestamp":1}]' : "[]" };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/1104972/matches`, { headers: auth });
  assert.equal(response.status, 200);
  assert.ok(requested.includes("playoffMatch"));
  assert.deepEqual((await response.json() as Array<{ matchId: string }>).map(it => it.matchId), ["playoff"]);
});

test("matches merge, deduplicate and order league and playoff", async () => {
  const requested: string[] = [];
  const ea = await fixture(url => {
    const type = url.searchParams.get("matchType") ?? "";
    requested.push(type);
    assert.equal(url.searchParams.get("clubIds"), "1104972");
    assert.equal(url.searchParams.get("maxResultCount"), "20");
    return type === "leagueMatch"
      ? { body: '[{"matchId":"old","timestamp":10},{"matchId":"same","timestamp":20}]' }
      : { body: '[{"matchId":"new","timestamp":30},{"matchId":"same","timestamp":20}]' };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/1104972/matches`, { headers: auth });
  assert.equal(response.status, 200);
  assert.deepEqual(requested.sort(), ["leagueMatch", "playoffMatch"]);
  assert.deepEqual((await response.json() as Array<{ matchId: string }>).map(it => it.matchId), ["new", "same", "old"]);
});

test("matches emits safe structured telemetry for each competition and the merged window", async () => {
  const telemetry: GatewayTelemetry[] = [];
  const ea = await fixture(url => {
    const type = url.searchParams.get("matchType");
    return type === "leagueMatch"
      ? { body: '[{"matchId":"league-1","timestamp":10}]' }
      : { body: '[{"matchId":"playoff-1","timestamp":20},{"matchId":"league-1","timestamp":10}]' };
  });

  const response = await fetch(
    `${await gateway(ea, 1_000, event => telemetry.push(event))}/ea/clubs/11262883/matches?platform=common-gen5&maxResultCount=5`,
    { headers: auth },
  );

  assert.equal(response.status, 200);
  const fetches = telemetry.filter((event): event is Extract<GatewayTelemetry, { event: "EA_MATCH_FETCH" }> => event.event === "EA_MATCH_FETCH")
    .sort((left, right) => left.matchType.localeCompare(right.matchType));
  assert.deepEqual(fetches, [
    {
      event: "EA_MATCH_FETCH", gatewayBuildSha: null, clubId: "11262883", platform: "common-gen5",
      matchType: "leagueMatch", maxResultCount: "5", status: 200, returnedCount: 1, matchIds: ["league-1"],
    },
    {
      event: "EA_MATCH_FETCH", gatewayBuildSha: null, clubId: "11262883", platform: "common-gen5",
      matchType: "playoffMatch", maxResultCount: "5", status: 200, returnedCount: 2, matchIds: ["playoff-1", "league-1"],
    },
  ]);
  assert.deepEqual(telemetry.find((event): event is Extract<GatewayTelemetry, { event: "EA_MATCH_MERGE" }> => event.event === "EA_MATCH_MERGE"), {
    event: "EA_MATCH_MERGE", gatewayBuildSha: null, clubId: "11262883", platform: "common-gen5", maxResultCount: "5",
    leagueCount: 1, playoffCount: 2, mergedCount: 2, mergedMatchIds: ["playoff-1", "league-1"],
  });
  assert.equal(JSON.stringify(telemetry).includes(token), false);
  assert.equal(JSON.stringify(telemetry).toLowerCase().includes("authorization"), false);
});

test("matches emits the upstream HTTP status when one competition request fails", async () => {
  const telemetry: GatewayTelemetry[] = [];
  const ea = await fixture(url => url.searchParams.get("matchType") === "playoffMatch"
    ? { status: 503, body: "{}" }
    : { body: "[]" });

  const response = await fetch(`${await gateway(ea, 1_000, event => telemetry.push(event))}/ea/clubs/11262883/matches`, { headers: auth });

  assert.equal(response.status, 502);
  assert.deepEqual(telemetry.find((event): event is Extract<GatewayTelemetry, { event: "EA_MATCH_FETCH" }> =>
    event.event === "EA_MATCH_FETCH" && event.matchType === "playoffMatch"), {
    event: "EA_MATCH_FETCH", gatewayBuildSha: null, clubId: "11262883", platform: "common-gen5",
    matchType: "playoffMatch", maxResultCount: "20", status: 503, returnedCount: null, matchIds: [], errorKind: "ea_http_error",
  });
});

test("matches keeps the HTTP 200 diagnostic when an upstream match payload is invalid", async () => {
  const telemetry: GatewayTelemetry[] = [];
  const ea = await fixture(url => url.searchParams.get("matchType") === "playoffMatch"
    ? { body: "{}" }
    : { body: "[]" });

  const response = await fetch(`${await gateway(ea, 1_000, event => telemetry.push(event))}/ea/clubs/11262883/matches`, { headers: auth });

  assert.equal(response.status, 502);
  assert.deepEqual(telemetry.find((event): event is Extract<GatewayTelemetry, { event: "EA_MATCH_FETCH" }> =>
    event.event === "EA_MATCH_FETCH" && event.matchType === "playoffMatch"), {
    event: "EA_MATCH_FETCH", gatewayBuildSha: null, clubId: "11262883", platform: "common-gen5",
    matchType: "playoffMatch", maxResultCount: "20", status: 200, returnedCount: null, matchIds: [], errorKind: "ea_invalid_payload",
  });
});

test("matches forwards a bounded maxResultCount to both EA competition requests", async () => {
  const ea = await fixture(url => {
    assert.equal(url.searchParams.get("maxResultCount"), "5");
    return { body: "[]" };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/1104972/matches?maxResultCount=5`, { headers: auth });
  assert.equal(response.status, 200);
});

test("members forwards and returns the EA members envelope", async () => {
  const ea = await fixture(url => {
    assert.equal(url.pathname, "/members/stats");
    assert.equal(url.searchParams.get("clubId"), "1104972");
    return { body: '{"members":[{"name":"Player"}]}' };
  });
  const response = await fetch(`${await gateway(ea)}/ea/clubs/1104972/members`, { headers: auth });
  assert.equal(response.status, 200);
  assert.equal(((await response.json() as { members: unknown[] }).members).length, 1);
});

test("EA timeout becomes 504", async () => {
  const response = await fetch(`${await gateway(await fixture(() => ({ body: "[]", delay: 100 })), 10)}/ea/clubs/search?name=BF`, { headers: auth });
  assert.equal(response.status, 504);
});

test("invalid EA payload becomes 502", async () => {
  const response = await fetch(`${await gateway(await fixture(() => ({ body: "not-json" })))}/ea/clubs/search?name=BF`, { headers: auth });
  assert.equal(response.status, 502);
});

test("EA HTTP error becomes 502", async () => {
  const response = await fetch(`${await gateway(await fixture(() => ({ status: 503, body: "{}" })))}/ea/clubs/search?name=BF`, { headers: auth });
  assert.equal(response.status, 502);
});
