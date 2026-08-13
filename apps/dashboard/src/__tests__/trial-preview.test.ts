import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const source = (path: string) => readFileSync(resolve(process.cwd(), "src", path), "utf8");

describe("trial snapshot presentation", () => {
  it("communicates a preview without the former three-match counter", () => {
    const notice = source("components/club/trial-notice.tsx");
    expect(notice).toContain("Prévia gratuita");
    expect(notice).toContain("acompanhamento automático não está ativo");
    expect(notice).not.toContain("partidas acompanhadas");
    expect(notice).not.toContain("de 3");
  });

  it("keeps the overview as the only trial destination and commercial contact configurable", () => {
    const notice = source("components/club/trial-notice.tsx");
    expect(notice).toContain("COMMERCIAL_CONTACT_URL");
    expect(notice).toContain("Ativar acompanhamento");
  });
});
