// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { OverviewMatchCard } from "@/components/overview/overview-match-card";
import { OverviewMatchCarousel } from "@/components/overview/overview-match-carousel";
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

    expect(html).toContain("match-summary-card w-full min-w-0 overflow-hidden rounded-[10px] border border-border bg-surface");
    expect(html).toContain("card-header grid w-full overflow-hidden rounded-t-[10px]");
    expect(html).toContain(`background:${ribbonColor}`);
    expect(html).toContain("rgba(22, 27, 34, 0.98) 100%");
    expect(html).not.toContain("rgba(88, 166, 255, 0.08), transparent");
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

  it("formats the footer timestamp in Brasilia time instead of using persisted display copy", () => {
    const html = render({
      ...basePresentation,
      date: "12 ago. 2026 • 22:07",
      timestamp: "2026-08-12T22:07:00Z",
    });

    expect(html).toContain("12 ago. 2026 • 19:07");
    expect(html).not.toContain("12 ago. 2026 • 22:07");
    expect(html).not.toContain("card-date");
    expect(html).toContain("card-footer");
  });

  it("keeps the team average beside the highlights and renders advanced conditional blocks", () => {
    const html = render({
      ...basePresentation,
      xerife: { name: "Dnph27", tacklesMade: 2, tackleAttempts: 2, successRate: 100, interceptions: 6, phrase: "Leu o jogo antes dos adversários." },
      behindThePlay: { name: "Guilherme", secondAssists: 2, throughPasses: 9, phrase: "Nem toda participação decisiva aparece na súmula." },
      oneOnOne: { name: "Dnph27", beats: 8, dribblesCompleted: 18, phrase: "Chamou para o duelo e passou." },
    });

    expect(html).toContain("card-highlights-layout grid grid-cols-[minmax(0,1fr)_auto]");
    expect(html).toContain("📊 Média do time");
    expect(html).toContain("POR TRÁS DA JOGADA");
    expect(html).toContain("2 pré-assistências • 9 passes em profundidade");
    expect(html).toContain("NO UM CONTRA UM");
    expect(html).toContain("8 adversários superados • 18 dribles completos");
    expect(html).toContain("🛡️ XERIFE");
    expect(html).toContain("6 interceptações • 2 desarmes certos (100% de acerto)");
    expect(html.indexOf("POR TRÁS DA JOGADA")).toBeLessThan(html.indexOf("NO UM CONTRA UM"));
    expect(html.indexOf("NO UM CONTRA UM")).toBeLessThan(html.indexOf("🛡️ XERIFE"));
  });

  it("does not render advanced conditional blocks when no decisions were produced", () => {
    const html = render(basePresentation);

    expect(html).not.toContain("POR TRÁS DA JOGADA");
    expect(html).not.toContain("NO UM CONTRA UM");
    expect(html).not.toContain("🛡️ XERIFE");
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
    expect(sparse).toContain("match-summary-card w-full min-w-0 overflow-hidden");
    expect(sparse).not.toContain("GOLS");
  });

  it("reserves both carousel control columns before and after pagination", () => {
    const html = renderToStaticMarkup(
      <OverviewMatchCarousel presentations={[basePresentation, { ...basePresentation, matchId: "two" }, { ...basePresentation, matchId: "three" }, { ...basePresentation, matchId: "four" }]} />,
    );

    expect((html.match(/w-12 shrink-0/g) ?? [])).toHaveLength(2);
    expect(html).toContain("min-w-0 flex-1 overflow-hidden");
    expect(html).toContain("grid w-full min-w-0 grid-cols-1");
  });
});
