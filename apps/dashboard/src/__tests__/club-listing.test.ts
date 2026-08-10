import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("club listing fault tolerance", () => {
  const overviewRepo = read("lib/repositories/overview-repository.ts");
  const clubsPage = read("app/clubs/page.tsx");

  it("listClubs catches metadata errors per club without failing the batch", () => {
    expect(overviewRepo).toContain("try {");
    expect(overviewRepo).toContain("} catch {");
    expect(overviewRepo).toContain("totalMatches:0");
  });

  it("all clubs from /api/clubs are always included in the result", () => {
    expect(overviewRepo).toContain('fetchSports<Club[]>("/api/clubs")');
    expect(overviewRepo).toContain("clubs.map(async club");
    expect(overviewRepo).not.toContain(".filter(");
  });

  it("displays 'Aguardando primeira partida' when totalMatches is 0", () => {
    expect(clubsPage).toContain("Aguardando primeira partida");
    expect(clubsPage).toContain("totalMatches > 0");
  });

  it("displays match count when totalMatches > 0", () => {
    expect(clubsPage).toContain("partidas");
  });

  it("does not import or reference supabase in the public clubs flow", () => {
    expect(overviewRepo).not.toContain("supabase");
    expect(clubsPage).not.toContain("supabase");
  });
});
