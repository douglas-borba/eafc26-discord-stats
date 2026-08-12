// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { OverviewMatchCard } from "@/components/overview/overview-match-card";
import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

const basePresentation: MatchSummaryPresentation = {
  ourName: "Associação BF",
  oppName: "JardimHelenaFC",
  ourScore: 4,
  oppScore: 2,
  outcome: { emoji: "✅", label: "Vitória", color: 0x3fb950, type: "WIN" },
  date: "11 ago. 2026 • 19:46",
  timestamp: "2026-08-11T22:46:00.000Z",
  matchId: "960632703180174",
  goals: { scorers: [{ name: "R. Nazario", count: 2 }] },
  assists: { assisters: [{ name: "Ronaldinho", count: 1 }] },
  highlights: { top3: [{ medal: "🥇", name: "Ronaldinho", rating: "9,20" }], teamAverage: "7,90" },
  craque: { name: "Ronaldinho", reason: "Nota 9,20", phrase: "Lembrou a todos por que é craque." },
  offensiveNarratives: [],
  bagre: null,
  redCard: null,
  xerife: null,
  passePrecisao: null,
  correioExtraviado: null,
  muralha: null,
};

function render(presentation: MatchSummaryPresentation) {
  return renderToStaticMarkup(<OverviewMatchCard presentation={presentation} isLatest />);
}

describe("OverviewMatchCard", () => {
  it.each([
    ["WIN", "Vitória", "var(--color-win)"],
    ["DRAW", "Empate", "var(--color-draw)"],
    ["LOSS", "Derrota", "var(--color-loss)"],
  ] as const)("renders a clipped header and the %s ribbon", (type, label, ribbonColor) => {
    const html = render({ ...basePresentation, outcome: { ...basePresentation.outcome, type, label } });

    expect(html).toContain("match-summary-card overflow-hidden rounded-[10px] border border-border bg-surface");
    expect(html).toContain("card-header grid w-full overflow-hidden rounded-t-[10px]");
    expect(html).toContain(`background:${ribbonColor}`);
    expect(html).toContain(label);
  });

  it("keeps long club names and two-digit scores inside the normal score layout", () => {
    const html = render({
      ...basePresentation,
      oppName: "SuaIrmãÉNossaFC",
      ourScore: 10,
      oppScore: 12,
    });

    expect(html).toContain("min-w-0 text-center");
    expect(html).toContain("max-w-[110px]");
    expect(html).toContain("SuaIrmãÉNossaFC");
    expect(html).toContain(">10</span>");
    expect(html).toContain(">12</span>");
  });

  it("renders rich and sparse cards without changing the card shell", () => {
    const rich = render({
      ...basePresentation,
      bagre: { name: "NEYMAR", rating: "5,90", reason: "Menor nota", tackleStats: null, passStats: null, phrase: "A próxima é uma nova oportunidade." },
      passePrecisao: { name: "Beckham", passesMade: 13, passAttempts: 14, accuracy: 92, phrase: "Ligou o time inteiro." },
      correioExtraviado: { name: "Haalandinho", playerAccuracyPct: 53, teamAccuracyPct: 76, deltaPct: 23, phrase: "Abaixo da média." },
    });
    const sparse = render({ ...basePresentation, goals: null, assists: null, highlights: null, craque: null });

    expect(rich).toContain("PASSE PRECISÃO");
    expect(rich).toContain("CORREIO EXTRAVIADO");
    expect(sparse).toContain("match-summary-card overflow-hidden");
    expect(sparse).not.toContain("GOLS");
  });
});
