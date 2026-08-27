// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("server-only", () => ({}));

import { PlayerProfileView } from "@/components/players/player-profile-view";
import type { PlayerProfile, PlayerXRay } from "@/lib/domain/types";
import { toPlayerProfile, type ApiProfile } from "@/lib/repositories/player-repository";

const basicXRay: PlayerXRay = {
  currentForm: {
    state: "COMPARED",
    recent: { appearances: 5, averageRating: 9.06, goalsPerMatch: 1.8, assistsPerMatch: 1.2, directContributionsPerMatch: 3, passAccuracy: 88, tackleEfficiency: 60, finishingConversion: 30, passAttempts: 100, tackleAttempts: 20, shots: 20 },
    previous: { appearances: 10, averageRating: 8.57, goalsPerMatch: 1.3, assistsPerMatch: 0.8, directContributionsPerMatch: 2.1, passAccuracy: 82, tackleEfficiency: 70, finishingConversion: 25, passAttempts: 160, tackleAttempts: 30, shots: 30 },
    differences: { averageRating: 0.49, goalsPerMatch: 0.5, assistsPerMatch: 0.4, directContributionsPerMatch: 0.9, passAccuracyPoints: 6, tackleEfficiencyPoints: -10, finishingConversionPoints: 5 },
  },
  attack: { goals: 26, goalsPerMatch: 1.73, shots: 78, shotsPerMatch: 5.2, finishingConversion: 33.33 },
  creation: { assists: 15, assistsPerMatch: 1, passesAttempted: 260, passesCompleted: 220, passAccuracy: 84.62, directContributions: 41, directContributionsPerMatch: 2.73 },
  defense: { tacklesAttempted: 50, tacklesCompleted: 34, tackleEfficiency: 68, tacklesCompletedPerMatch: 2.27 },
  advancedCoverage: { eligibleAppearances: 15, fullAppearances: 0, partialAppearances: 0, unavailableAppearances: 15, coverage: "UNAVAILABLE" },
  oneOnOne: null,
  recognitions: { craques: 3, bagres: 1, xerifes: 2 },
  records: { mostGoalsInMatch: null, mostAssistsInMatch: null, mostDirectContributionsInMatch: null, scoringStreak: 4, assistStreak: 3, directContributionStreak: 5, ratingTenMatches: 1 },
  analysis: { summary: "Y. Alberto soma 41 participações diretas em 15 partidas elegíveis.", strengths: ["Mantém 84.6% de precisão de passe em 260 tentativas."], currentForm: "Nas últimas cinco partidas, sua nota média está 0.49 acima da própria referência anterior.", opportunity: null },
};

const profile: PlayerProfile = {
  playerId: "y-alberto", displayName: "Y. Alberto", platformName: "Y Alberto", proName: "Y. Alberto",
  matchesPlayed: 15, totalGoals: 26, totalAssists: 15, totalShots: 78, averageRating: 8.57,
  manOfTheMatchCount: 3, redCardCount: 0, totalPassesCompleted: 220, totalPassesAttempted: 260,
  totalTacklesCompleted: 34, totalTacklesAttempted: 50, wins: 9, draws: 3, losses: 3,
  ratedMatchCount: 15, bagreCount: 1, xerifeCount: 2, xRay: basicXRay, recentMatches: [],
};

describe("PlayerProfileView", () => {
  it("renders the complete basic X-Ray when advanced coverage is unavailable", () => {
    const html = renderToStaticMarkup(<PlayerProfileView profile={profile} />);

    expect(html).toContain("ANÁLISE DO JOGADOR");
    expect(html).toContain("MOMENTO ATUAL");
    expect(html).toContain("ATAQUE");
    expect(html).toContain("CRIAÇÃO");
    expect(html).toContain("DEFESA");
    expect(html).toContain("RECONHECIMENTOS");
    expect(html).toContain("RECORDES PESSOAIS");
    expect(html).not.toContain("1 CONTRA 1");
    expect(html).not.toContain("Ainda não há dados básicos suficientes");
    expect(html).not.toContain("Últimas partidas");
  });

  it("renders advanced 1v1 facts only when their coverage exists", () => {
    const html = renderToStaticMarkup(<PlayerProfileView profile={{ ...profile, xRay: { ...basicXRay, advancedCoverage: { ...basicXRay.advancedCoverage, fullAppearances: 4, unavailableAppearances: 11, coverage: "PARTIAL" }, oneOnOne: { coveredAppearances: 4, dribblesCompleted: 12, opponentsBeaten: 7 } } }} />);

    expect(html).toContain("1 CONTRA 1");
    expect(html).toContain("Dados avançados em 4 de 15 partidas elegíveis.");
    expect(html).toContain("Dribles completos");
    expect(html).toContain("Adversários superados");
    expect(html).not.toContain("Dribles falhos");
    expect(html).not.toContain("Perdas de posse");
  });

  it("uses the legacy xray response key during the transition", () => {
    const apiProfile: ApiProfile = {
      playerId: "y-alberto", name: "Y. Alberto", matchCount: 15, averageRating: 8.57, ratedMatchCount: 15,
      wins: 9, draws: 3, losses: 3, goals: 26, assists: 15, craques: 3, bagres: 1, xerifes: 2,
      redCards: 0, shots: 78, passesCompleted: 220, passesAttempted: 260, tacklesCompleted: 34, tacklesAttempted: 50,
      recentMatches: [], xray: basicXRay,
    };

    expect(toPlayerProfile(apiProfile).xRay).toEqual(basicXRay);
  });

  it("uses the limited state only when no basic X-Ray exists", () => {
    const html = renderToStaticMarkup(<PlayerProfileView profile={{ ...profile, xRay: null }} />);

    expect(html).toContain("Ainda não há dados básicos suficientes para gerar o Raio-X deste perfil.");
    expect(html).not.toContain("ANÁLISE DO JOGADOR");
  });
});
