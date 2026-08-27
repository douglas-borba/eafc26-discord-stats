// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { PlayerXRaySkeleton } from "@/components/players/player-xray-skeleton";

describe("PlayerXRaySkeleton", () => {
  it("identifies the selected player without rendering any previous player data", () => {
    const html = renderToStaticMarkup(<PlayerXRaySkeleton playerName="Arthur" />);

    expect(html).toContain("RAIO-X DO JOGADOR");
    expect(html).toContain("Arthur");
    expect(html).toContain("Carregando Raio-X...");
    expect(html).toContain('role="status"');
    expect(html).toContain("player-xray-skeleton-kpis");
    expect(html).toContain("player-xray-skeleton-stat-grid");
  });

  it("uses a neutral identity placeholder when only the id is available", () => {
    const html = renderToStaticMarkup(<PlayerXRaySkeleton playerName={null} />);

    expect(html).toContain("Carregando Raio-X...");
    expect(html).not.toContain("undefined");
  });
});
