import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("club listing fault tolerance", () => {
  const overviewRepo = read("lib/repositories/overview-repository.ts");

  it("listClubs catches metadata errors per club without failing the batch", () => {
    expect(overviewRepo).toContain("try {");
    expect(overviewRepo).toContain("} catch {");
    expect(overviewRepo).toContain("totalMatches:0");
  });

  it("all clubs from admin endpoint are always included in the result", () => {
    expect(overviewRepo).toContain('fetchSportsInternal<Club[]>("/api/admin/clubs")');
    expect(overviewRepo).toContain("clubs.map(async club");
    expect(overviewRepo).not.toContain(".filter(");
  });

  it("does not import or reference supabase in the overview repository", () => {
    expect(overviewRepo).not.toContain("supabase");
  });
});

describe("/clubs page no longer lists clubs publicly", () => {
  const clubsPage = read("app/clubs/page.tsx");

  it("returns notFound instead of rendering a club list", () => {
    expect(clubsPage).toContain("notFound");
    expect(clubsPage).not.toContain("listClubs");
    expect(clubsPage).not.toContain("Link");
  });
});
