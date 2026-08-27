import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("server-only", () => ({}));
vi.mock("@/lib/services/player-service", () => ({ getPlayer: vi.fn() }));

import { GET } from "@/app/api/clubs/[clubId]/players/[playerId]/route";
import { getPlayer } from "@/lib/services/player-service";

const context = { params: Promise.resolve({ clubId: "1104972", playerId: "y-alberto" }) };

beforeEach(() => vi.mocked(getPlayer).mockReset());
afterEach(() => vi.unstubAllGlobals());

describe("public player detail bridge", () => {
  it("returns only the requested profile with no-store semantics", async () => {
    vi.mocked(getPlayer).mockResolvedValue({ playerId: "y-alberto", xRay: { attack: { goals: 26 } } } as never);

    const response = await GET(new Request("https://dashboard.test/api/clubs/1104972/players/y-alberto"), context);

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toMatchObject({ profile: { playerId: "y-alberto", xRay: { attack: { goals: 26 } } } });
    expect(getPlayer).toHaveBeenCalledWith("1104972", "y-alberto");
  });

  it("contains a missing detail without touching the player list", async () => {
    vi.mocked(getPlayer).mockResolvedValue(null);

    const response = await GET(new Request("https://dashboard.test"), context);

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toEqual({ error: "not_found" });
    expect(getPlayer).toHaveBeenCalledTimes(1);
  });
});
