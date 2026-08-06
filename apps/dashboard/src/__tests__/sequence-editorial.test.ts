import { describe, it, expect, vi } from "vitest";
import { readFileSync, existsSync } from "fs";
import { join } from "path";

// Mock server-only module before importing the service
vi.mock("@/lib/supabase/server", () => ({
  createServerSupabase: vi.fn(),
}));

import { buildSequenceEditorial } from "../lib/services/sequence-editorial-service";
import type { MatchSummaryPresentation } from "../lib/services/match-card-service";

const SRC = join(__dirname, "..");

function makePresentation(overrides: Partial<MatchSummaryPresentation> = {}): MatchSummaryPresentation {
  return {
    ourName: "Associação BF",
    oppName: "Adversário FC",
    ourScore: 3,
    oppScore: 1,
    outcome: { emoji: "✅", label: "Vitória", color: 0x238636, type: "WIN" },
    date: "01/08/2026",
    timestamp: "2026-08-01T20:00:00Z",
    matchId: "match-1",
    goals: { scorers: [{ name: "R. Nazario", count: 2 }, { name: "Pelé", count: 1 }] },
    assists: { assisters: [{ name: "Zidane", count: 1 }] },
    highlights: { top3: [{ medal: "🥇", name: "R. Nazario", rating: "9.2" }, { medal: "🥈", name: "Pelé", rating: "8.5" }, { medal: "🥉", name: "Zidane", rating: "8.0" }], teamAverage: "7.8" },
    craque: { name: "R. Nazario", reason: "2 gols e nota 9.2", phrase: "Imparável" },
    offensiveNarratives: [],
    bagre: { name: "Maguire", rating: "4.2", reason: "Perdeu todos os duelos", tackleStats: null, passStats: null, phrase: "Dia esquecível" },
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
    ...overrides,
  };
}

describe("buildSequenceEditorial", () => {
  describe("0 partidas", () => {
    it("returns empty state", () => {
      const result = buildSequenceEditorial([]);
      expect(result.stats.matchCount).toBe(0);
      expect(result.stats.wins).toBe(0);
      expect(result.stats.draws).toBe(0);
      expect(result.stats.losses).toBe(0);
      expect(result.stats.goalsScored).toBe(0);
      expect(result.stats.goalsConceded).toBe(0);
      expect(result.stats.pointsPercentage).toBe("0.0");
      expect(result.title).toBe("Sem partidas recentes");
      expect(result.matchDetails).toEqual([]);
      expect(result.topScorer).toBeNull();
      expect(result.topAssister).toBeNull();
      expect(result.topHighlight).toBeNull();
      expect(result.topRatedPlayer).toBeNull();
      expect(result.currentStreak).toBeNull();
    });
  });

  describe("1 partida", () => {
    it("calculates stats from single match", () => {
      const result = buildSequenceEditorial([makePresentation()]);
      expect(result.stats.matchCount).toBe(1);
      expect(result.stats.wins).toBe(1);
      expect(result.stats.goalsScored).toBe(3);
      expect(result.stats.goalsConceded).toBe(1);
      expect(result.stats.goalDifference).toBe(2);
      expect(result.subtitle).toBe("A partida mais recente");
    });
  });

  describe("2 partidas", () => {
    it("calculates aggregate stats", () => {
      const result = buildSequenceEditorial([
        makePresentation({ ourScore: 3, oppScore: 1, outcome: { emoji: "✅", label: "Vitória", color: 0, type: "WIN" } }),
        makePresentation({ ourScore: 0, oppScore: 2, matchId: "match-2", outcome: { emoji: "❌", label: "Derrota", color: 0, type: "LOSS" } }),
      ]);
      expect(result.stats.matchCount).toBe(2);
      expect(result.stats.wins).toBe(1);
      expect(result.stats.losses).toBe(1);
      expect(result.stats.goalsScored).toBe(3);
      expect(result.stats.goalsConceded).toBe(3);
      expect(result.stats.pointsPercentage).toBe("50.0"); // 3 points out of 6 possible
      expect(result.subtitle).toBe("As 2 últimas partidas");
      expect(result.matchDetails).toHaveLength(2);
    });
  });

  describe("3 partidas", () => {
    it("calculates V/E/D correctly", () => {
      const result = buildSequenceEditorial([
        makePresentation({ outcome: { emoji: "✅", label: "Vitória", color: 0, type: "WIN" }, ourScore: 4, oppScore: 1 }),
        makePresentation({ matchId: "m2", outcome: { emoji: "🤝", label: "Empate", color: 0, type: "DRAW" }, ourScore: 2, oppScore: 2 }),
        makePresentation({ matchId: "m3", outcome: { emoji: "❌", label: "Derrota", color: 0, type: "LOSS" }, ourScore: 0, oppScore: 3 }),
      ]);
      expect(result.stats.wins).toBe(1);
      expect(result.stats.draws).toBe(1);
      expect(result.stats.losses).toBe(1);
      expect(result.stats.goalsScored).toBe(6);
      expect(result.stats.goalsConceded).toBe(6);
      expect(result.stats.goalDifference).toBe(0);
      expect(result.stats.avgGoalsScored).toBe("2.0");
      expect(result.stats.pointsPercentage).toBe("44.4"); // 4 points out of 9 possible
      expect(result.subtitle).toBe("As 3 últimas partidas");
    });

    it("picks top scorer across matches", () => {
      const result = buildSequenceEditorial([
        makePresentation({ goals: { scorers: [{ name: "R. Nazario", count: 2 }] } }),
        makePresentation({ matchId: "m2", goals: { scorers: [{ name: "Pelé", count: 1 }, { name: "R. Nazario", count: 1 }] } }),
        makePresentation({ matchId: "m3", goals: { scorers: [{ name: "Pelé", count: 3 }] } }),
      ]);
      expect(result.topScorer).toEqual({ name: "Pelé", goals: 4 });
    });

    it("picks top assister across matches", () => {
      const result = buildSequenceEditorial([
        makePresentation({ assists: { assisters: [{ name: "Zidane", count: 2 }] } }),
        makePresentation({ matchId: "m2", assists: { assisters: [{ name: "Beckham", count: 1 }, { name: "Zidane", count: 1 }] } }),
        makePresentation({ matchId: "m3", assists: { assisters: [{ name: "Beckham", count: 2 }] } }),
      ]);
      // Both have 3 assists, but alphabetically Beckham < Zidane, so Beckham wins
      expect(result.topAssister).toEqual({ name: "Beckham", assists: 3 });
    });

    it("picks top highlight across matches", () => {
      const result = buildSequenceEditorial([
        makePresentation({ highlights: { top3: [{ medal: "🥇", name: "R. Nazario", rating: "9.0" }], teamAverage: null } }),
        makePresentation({ matchId: "m2", highlights: { top3: [{ medal: "🥇", name: "R. Nazario", rating: "8.5" }], teamAverage: null } }),
        makePresentation({ matchId: "m3", highlights: { top3: [{ medal: "🥇", name: "Pelé", rating: "9.5" }], teamAverage: null } }),
      ]);
      expect(result.topHighlight).toEqual({ name: "R. Nazario", appearances: 2 });
    });

    it("chooses title based on sequence", () => {
      const allWins = buildSequenceEditorial([
        makePresentation({ outcome: { emoji: "✅", label: "V", color: 0, type: "WIN" } }),
        makePresentation({ matchId: "m2", outcome: { emoji: "✅", label: "V", color: 0, type: "WIN" } }),
        makePresentation({ matchId: "m3", outcome: { emoji: "✅", label: "V", color: 0, type: "WIN" } }),
      ]);
      expect(allWins.title).toBe("Fase impecável");
      expect(allWins.currentStreak).toEqual({ type: "WIN", count: 3, label: "3 vitórias" });

      const allLosses = buildSequenceEditorial([
        makePresentation({ outcome: { emoji: "❌", label: "D", color: 0, type: "LOSS" } }),
        makePresentation({ matchId: "m2", outcome: { emoji: "❌", label: "D", color: 0, type: "LOSS" } }),
        makePresentation({ matchId: "m3", outcome: { emoji: "❌", label: "D", color: 0, type: "LOSS" } }),
      ]);
      expect(allLosses.title).toBe("Momento difícil");
      expect(allLosses.currentStreak).toEqual({ type: "LOSS", count: 3, label: "3 derrotas" });
    });
  });

  describe("determinism", () => {
    it("produces identical output for same input", () => {
      const input = [
        makePresentation(),
        makePresentation({ matchId: "m2", outcome: { emoji: "❌", label: "D", color: 0, type: "LOSS" }, ourScore: 1, oppScore: 2 }),
        makePresentation({ matchId: "m3" }),
      ];
      const r1 = buildSequenceEditorial(input);
      const r2 = buildSequenceEditorial(input);
      expect(r1).toEqual(r2);
    });

    it("does not use Math.random", () => {
      const source = readFileSync(join(SRC, "lib/services/sequence-editorial-service.ts"), "utf-8");
      expect(source).not.toContain("Math.random");
    });
  });

  describe("no award recalculation", () => {
    it("does not recalculate craque, bagre, xerife or any individual award", () => {
      const source = readFileSync(join(SRC, "lib/services/sequence-editorial-service.ts"), "utf-8");
      expect(source).not.toMatch(/craque/i);
      expect(source).not.toMatch(/bagre/i);
      expect(source).not.toMatch(/xerife/i);
      expect(source).not.toMatch(/muralha/i);
      expect(source).not.toMatch(/passePrecisao/i);
      expect(source).not.toMatch(/correioExtraviado/i);
    });
  });
});

describe("Overview page composition", () => {
  it("uses OverviewClubPanel and OverviewMatchCard", () => {
    const page = readFileSync(join(SRC, "app/clubs/[clubId]/(fullwidth)/overview/page.tsx"), "utf-8");
    expect(page).toContain("OverviewClubPanel");
    expect(page).toContain("OverviewMatchCard");
    expect(page).toContain("getRecentMatchCards");
    expect(page).toContain("buildSequenceEditorial");
  });

  it("does not use OverviewLatestMatch", () => {
    const page = readFileSync(join(SRC, "app/clubs/[clubId]/(fullwidth)/overview/page.tsx"), "utf-8");
    expect(page).not.toContain("OverviewLatestMatch");
    expect(existsSync(join(SRC, "components/overview/overview-latest-match.tsx"))).toBe(false);
  });

  it("uses full variant for match cards", () => {
    const page = readFileSync(join(SRC, "app/clubs/[clubId]/(fullwidth)/overview/page.tsx"), "utf-8");
    expect(page).toContain('variant="full"');
  });

  it("has responsive grid layout", () => {
    const page = readFileSync(join(SRC, "app/clubs/[clubId]/(fullwidth)/overview/page.tsx"), "utf-8");
    expect(page).toContain("md:grid-cols-2");
    expect(page).toContain("xl:grid-cols-3");
  });
});

describe("match-card-service exports getRecentMatchCards", () => {
  it("function exists in source", () => {
    const source = readFileSync(join(SRC, "lib/services/match-card-service.ts"), "utf-8");
    expect(source).toContain("export async function getRecentMatchCards");
    expect(source).toContain("club_id");
    expect(source).toContain("played_at");
    expect(source).toContain(".limit(limit)");
  });
});
