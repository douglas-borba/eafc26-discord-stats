/**
 * Tests for opponent history calculator.
 * Ported from OpponentHistoryServiceTest.kt
 */

import { describe, it, expect } from "vitest";
import {
  calculateCurrentRun,
  calculateRunRecords,
  calculatePlayerLeaders,
  extractAwards,
} from "@/lib/domain/opponent-history-calculator";
import type { MatchSummary } from "@/lib/domain/types";

// Helper to create test match
function match(
  id: string,
  time: string,
  outcome: "WIN" | "DRAW" | "LOSS",
  ourScore = 1,
  opponentScore = 0
): MatchSummary {
  return {
    matchId: id,
    playedAt: time,
    ourClubId: "ours",
    ourClubName: "Our Club",
    opponentClubId: "opponent-a",
    opponentClubName: "Opponent A",
    ourScore,
    opponentScore,
    outcome,
    matchType: "leagueMatch",
  };
}

describe("calculateCurrentRun", () => {
  it("returns null for less than 2 matches", () => {
    expect(calculateCurrentRun([])).toBeNull();
    expect(calculateCurrentRun([match("m1", "2026-01-01T00:00:00Z", "WIN")])).toBeNull();
  });

  it("calculates terminal winning sequence", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "DRAW", 1, 1),
      match("m3", "2026-01-03T00:00:00Z", "LOSS", 0, 1),
      match("m4", "2026-01-04T00:00:00Z", "WIN", 1, 0),
      match("m5", "2026-01-05T00:00:00Z", "WIN", 2, 0),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).not.toBeNull();
    expect(run!.type).toBe("WINNING");
    expect(run!.count).toBe(2);
    expect(run!.matchIds).toEqual(["m4", "m5"]);
    expect(run!.label).toBe("2 vitórias consecutivas");
  });

  it("calculates terminal unbeaten sequence", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "LOSS", 0, 1),
      match("m2", "2026-01-02T00:00:00Z", "WIN", 2, 0),
      match("m3", "2026-01-03T00:00:00Z", "DRAW", 1, 1),
      match("m4", "2026-01-04T00:00:00Z", "DRAW", 0, 0),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).not.toBeNull();
    expect(run!.type).toBe("UNBEATEN");
    expect(run!.count).toBe(3);
    expect(run!.matchIds).toEqual(["m2", "m3", "m4"]);
    expect(run!.label).toBe("3 jogos sem perder");
  });

  it("calculates terminal winless sequence", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 2, 0),
      match("m2", "2026-01-02T00:00:00Z", "DRAW", 1, 1),
      match("m3", "2026-01-03T00:00:00Z", "LOSS", 0, 1),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).not.toBeNull();
    expect(run!.type).toBe("WINLESS");
    expect(run!.count).toBe(2);
    expect(run!.matchIds).toEqual(["m2", "m3"]);
    expect(run!.label).toBe("2 jogos sem vencer");
  });

  it("prefers longer sequence over type priority", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "DRAW", 1, 1),
      match("m3", "2026-01-03T00:00:00Z", "DRAW", 0, 0),
      match("m4", "2026-01-04T00:00:00Z", "DRAW", 2, 2),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).not.toBeNull();
    expect(run!.type).toBe("UNBEATEN");
    expect(run!.count).toBe(4);
  });

  it("prefers WINNING over UNBEATEN when tied", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "LOSS", 0, 1),
      match("m2", "2026-01-02T00:00:00Z", "WIN", 1, 0),
      match("m3", "2026-01-03T00:00:00Z", "WIN", 2, 0),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).not.toBeNull();
    expect(run!.type).toBe("WINNING"); // Both WINNING and UNBEATEN have length 2, WINNING wins
    expect(run!.count).toBe(2);
  });

  it("returns null if no sequence reaches 2 matches", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "LOSS", 0, 1),
      match("m3", "2026-01-03T00:00:00Z", "WIN", 2, 0),
      match("m4", "2026-01-04T00:00:00Z", "LOSS", 1, 2),
    ];

    const run = calculateCurrentRun(matches);
    expect(run).toBeNull();
  });
});

describe("calculateRunRecords", () => {
  it("returns empty array for less than 2 matches", () => {
    expect(calculateRunRecords([])).toEqual([]);
    expect(calculateRunRecords([match("m1", "2026-01-01T00:00:00Z", "WIN")])).toEqual([]);
  });

  it("calculates historical winning record", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "WIN", 2, 0),
      match("m3", "2026-01-03T00:00:00Z", "WIN", 3, 0),
      match("m4", "2026-01-04T00:00:00Z", "LOSS", 0, 1),
      match("m5", "2026-01-05T00:00:00Z", "WIN", 1, 0),
      match("m6", "2026-01-06T00:00:00Z", "WIN", 2, 0),
    ];

    const records = calculateRunRecords(matches);
    const winningRecord = records.find((r) => r.type === "WINNING");
    expect(winningRecord).not.toBeUndefined();
    expect(winningRecord!.count).toBe(3);
    expect(winningRecord!.matchIds).toEqual(["m1", "m2", "m3"]);
    expect(winningRecord!.tiedRuns).toBe(1);
    expect(winningRecord!.label).toBe("Maior sequência de vitórias: 3");
  });

  it("preserves multiple tied sequences", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "WIN", 2, 0),
      match("m3", "2026-01-03T00:00:00Z", "LOSS", 0, 1),
      match("m4", "2026-01-04T00:00:00Z", "WIN", 1, 0),
      match("m5", "2026-01-05T00:00:00Z", "WIN", 2, 0),
      match("m6", "2026-01-06T00:00:00Z", "DRAW", 1, 1),
    ];

    const records = calculateRunRecords(matches);
    const winningRecord = records.find((r) => r.type === "WINNING");
    expect(winningRecord).not.toBeUndefined();
    expect(winningRecord!.count).toBe(2);
    expect(winningRecord!.matchIds).toEqual(["m1", "m2", "m4", "m5"]); // Both sequences flattened
    expect(winningRecord!.tiedRuns).toBe(2);
  });

  it("calculates unbeaten and winless records", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 2, 0),
      match("m2", "2026-01-02T00:00:00Z", "DRAW", 1, 1),
      match("m3", "2026-01-03T00:00:00Z", "DRAW", 0, 0),
      match("m4", "2026-01-04T00:00:00Z", "LOSS", 0, 1),
      match("m5", "2026-01-05T00:00:00Z", "DRAW", 1, 1),
      match("m6", "2026-01-06T00:00:00Z", "LOSS", 0, 2),
    ];

    const records = calculateRunRecords(matches);

    const unbeattenRecord = records.find((r) => r.type === "UNBEATEN");
    expect(unbeattenRecord).not.toBeUndefined();
    expect(unbeattenRecord!.count).toBe(3);
    expect(unbeattenRecord!.label).toBe("Maior sequência invicta: 3");

    // Winless: m2(DRAW), m3(DRAW), m4(LOSS), m5(DRAW), m6(LOSS) = 5 consecutive non-wins
    const winlessRecord = records.find((r) => r.type === "WINLESS");
    expect(winlessRecord).not.toBeUndefined();
    expect(winlessRecord!.count).toBe(5);
    expect(winlessRecord!.label).toBe("Maior sequência sem vencer: 5");
  });

  it("filters out sequences shorter than 2", () => {
    const matches = [
      match("m1", "2026-01-01T00:00:00Z", "WIN", 1, 0),
      match("m2", "2026-01-02T00:00:00Z", "LOSS", 0, 1),
      match("m3", "2026-01-03T00:00:00Z", "WIN", 2, 0),
      match("m4", "2026-01-04T00:00:00Z", "LOSS", 1, 2),
    ];

    const records = calculateRunRecords(matches);
    // No sequence reaches length 2
    expect(records).toEqual([]);
  });
});

describe("calculatePlayerLeaders", () => {
  it("returns empty array when no players have positive stats", () => {
    const result = calculatePlayerLeaders([], []);
    expect(result).toEqual([]);
  });

  it("calculates goals leaders", () => {
    const playerStats = [
      { playerId: "p1", displayName: "Ana", goals: 2, assists: 1 },
      { playerId: "p2", displayName: "Bia", goals: 1, assists: 0 },
    ];

    const leaders = calculatePlayerLeaders(playerStats, []);
    const goalsLeader = leaders.find((l) => l.type === "GOALS");

    expect(goalsLeader).not.toBeUndefined();
    expect(goalsLeader!.value).toBe(2);
    expect(goalsLeader!.players).toHaveLength(1);
    expect(goalsLeader!.players[0].playerId).toBe("p1");
    expect(goalsLeader!.label).toBe("Artilharia contra o adversário");
  });

  it("aggregates stats across multiple matches", () => {
    // Simulating what the repository would return: separate rows for each match
    const playerStats = [
      { playerId: "p1", displayName: "Ana", goals: 1, assists: 1 },
      { playerId: "p2", displayName: "Bia", goals: 0, assists: 1 },
      { playerId: "p1", displayName: "Ana", goals: 1, assists: 0 },
      { playerId: "p2", displayName: "Bia", goals: 1, assists: 0 },
    ];

    const leaders = calculatePlayerLeaders(playerStats, []);
    const goalsLeader = leaders.find((l) => l.type === "GOALS");

    expect(goalsLeader).not.toBeUndefined();
    expect(goalsLeader!.value).toBe(2);
    // Both players tied with 2 goals total (p1: 1+1=2, p2: 0+1=1, wait that's wrong)
    // Let me recalculate: p1 has 1+1=2 goals, p2 has 0+1=1 goal
    // So only p1 should be the leader
    expect(goalsLeader!.players).toHaveLength(1);
    expect(goalsLeader!.players[0].playerId).toBe("p1");
  });

  it("preserves ties and sorts by name then playerId", () => {
    const playerStats = [
      { playerId: "p3", displayName: "Carlos", goals: 3, assists: 0 },
      { playerId: "p1", displayName: "Ana", goals: 3, assists: 0 },
      { playerId: "p2", displayName: "Ana", goals: 3, assists: 0 },
    ];

    const leaders = calculatePlayerLeaders(playerStats, []);
    const goalsLeader = leaders.find((l) => l.type === "GOALS");

    expect(goalsLeader).not.toBeUndefined();
    expect(goalsLeader!.players).toHaveLength(3);
    // Sort by name (case-insensitive), then playerId
    expect(goalsLeader!.players[0].name).toBe("Ana");
    expect(goalsLeader!.players[0].playerId).toBe("p1"); // p1 before p2
    expect(goalsLeader!.players[1].playerId).toBe("p2");
    expect(goalsLeader!.players[2].name).toBe("Carlos");
  });

  it("calculates assists leaders", () => {
    const playerStats = [
      { playerId: "p1", displayName: "Ana", goals: 0, assists: 3 },
      { playerId: "p2", displayName: "Bia", goals: 1, assists: 1 },
    ];

    const leaders = calculatePlayerLeaders(playerStats, []);
    const assistsLeader = leaders.find((l) => l.type === "ASSISTS");

    expect(assistsLeader).not.toBeUndefined();
    expect(assistsLeader!.value).toBe(3);
    expect(assistsLeader!.players[0].playerId).toBe("p1");
    expect(assistsLeader!.label).toBe("Liderança em assistências");
  });

  it("calculates Craque leaders from awards", () => {
    const playerStats = [
      { playerId: "p1", displayName: "Ana", goals: 1, assists: 0 },
    ];

    const awards = [
      { type: "CRAQUE" as const, winnerId: "p1", awarded: true },
      { type: "CRAQUE" as const, winnerId: "p1", awarded: true },
      { type: "CRAQUE" as const, winnerId: "p2", awarded: true },
    ];

    const leaders = calculatePlayerLeaders(playerStats, awards);
    const craqueLeader = leaders.find((l) => l.type === "CRAQUES");

    expect(craqueLeader).not.toBeUndefined();
    expect(craqueLeader!.value).toBe(2);
    expect(craqueLeader!.players[0].playerId).toBe("p1");
    expect(craqueLeader!.label).toBe("Mais vezes Craque");
  });

  it("calculates Xerife leaders from awards", () => {
    const playerStats = [
      { playerId: "p1", displayName: "Ana", goals: 0, assists: 0 },
    ];

    const awards = [
      { type: "XERIFE" as const, winnerId: "p1", awarded: true },
      { type: "XERIFE" as const, winnerId: "p2", awarded: true },
      { type: "XERIFE" as const, winnerId: "p2", awarded: true },
    ];

    const leaders = calculatePlayerLeaders(playerStats, awards);
    const xerifeLeader = leaders.find((l) => l.type === "XERIFES");

    expect(xerifeLeader).not.toBeUndefined();
    expect(xerifeLeader!.value).toBe(2);
    expect(xerifeLeader!.players[0].playerId).toBe("p2");
    expect(xerifeLeader!.label).toBe("Mais vezes Xerife");
  });

  it("ignores awards that are not awarded or have no winner", () => {
    const playerStats: Array<{ playerId: string; displayName: string; goals: number; assists: number }> = [];

    const awards = [
      { type: "CRAQUE" as const, winnerId: "p1", awarded: false },
      { type: "CRAQUE" as const, winnerId: null, awarded: true },
      { type: "XERIFE" as const, winnerId: "p2", awarded: true },
    ];

    const leaders = calculatePlayerLeaders(playerStats, awards);

    expect(leaders.find((l) => l.type === "CRAQUES")).toBeUndefined();
    expect(leaders.find((l) => l.type === "XERIFES")).not.toBeUndefined();
  });

  it("uses playerId as fallback display name for award winners", () => {
    const playerStats: Array<{ playerId: string; displayName: string; goals: number; assists: number }> = [];

    const awards = [
      { type: "CRAQUE" as const, winnerId: "unknown-player", awarded: true },
    ];

    const leaders = calculatePlayerLeaders(playerStats, awards);
    const craqueLeader = leaders.find((l) => l.type === "CRAQUES");

    expect(craqueLeader).not.toBeUndefined();
    expect(craqueLeader!.players[0].name).toBe("unknown-player");
  });
});

describe("extractAwards", () => {
  it("extracts Craque award", () => {
    const payload = {
      interpretation: {
        awards: {
          craque: { awarded: true, winnerId: "p1" },
        },
      },
    };

    const awards = extractAwards(payload);
    expect(awards).toHaveLength(1);
    expect(awards[0].type).toBe("CRAQUE");
    expect(awards[0].winnerId).toBe("p1");
  });

  it("extracts Xerife award", () => {
    const payload = {
      interpretation: {
        awards: {
          xerife: { awarded: true, winnerId: "p2" },
        },
      },
    };

    const awards = extractAwards(payload);
    expect(awards).toHaveLength(1);
    expect(awards[0].type).toBe("XERIFE");
    expect(awards[0].winnerId).toBe("p2");
  });

  it("extracts both Craque and Xerife", () => {
    const payload = {
      interpretation: {
        awards: {
          craque: { awarded: true, winnerId: "p1" },
          xerife: { awarded: true, winnerId: "p2" },
        },
      },
    };

    const awards = extractAwards(payload);
    expect(awards).toHaveLength(2);
    expect(awards.map((a) => a.type)).toContain("CRAQUE");
    expect(awards.map((a) => a.type)).toContain("XERIFE");
  });

  it("ignores awards that are not awarded", () => {
    const payload = {
      interpretation: {
        awards: {
          craque: { awarded: false, winnerId: "p1" },
          xerife: { awarded: true, winnerId: null },
        },
      },
    };

    const awards = extractAwards(payload);
    expect(awards).toHaveLength(0);
  });

  it("returns empty array for missing interpretation", () => {
    expect(extractAwards({})).toEqual([]);
    expect(extractAwards({ interpretation: {} })).toEqual([]);
    expect(extractAwards({ interpretation: { awards: {} } })).toEqual([]);
  });
});





