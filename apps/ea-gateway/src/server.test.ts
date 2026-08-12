import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { afterEach, test } from "node:test";
import { createGatewayServer } from "./server.js";

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

async function gateway(eaBaseUrl: string, timeoutMs = 1_000) {
  return listen(createGatewayServer({ token, eaBaseUrl, timeoutMs }));
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
