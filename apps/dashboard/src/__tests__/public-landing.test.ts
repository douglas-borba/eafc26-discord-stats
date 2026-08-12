import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("public landing route", () => {
  const landing = read("app/page.tsx");

  it("renders a static product landing instead of redirecting to a club", () => {
    expect(landing).toContain("Seu Pro Clubs.");
    expect(landing).toContain("Sua história.");
    expect(landing).not.toContain("redirect(");
    expect(landing).not.toContain("listClubs");
    expect(landing).not.toContain("getClub(");
    expect(landing).not.toContain("fetch(");
  });

  it("keeps the landing free from public club discovery and hardcoded client URLs", () => {
    expect(landing).not.toContain("clubId");
    expect(landing).not.toContain("/api/admin/clubs");
    expect(landing).not.toMatch(/https?:\/\//);
  });

  it("preserves the public club dashboard, admin route and non-public clubs route", () => {
    const clubPage = read("app/[clubId]/page.tsx");
    const clubsPage = read("app/clubs/page.tsx");
    const adminLayout = read("app/admin/(protected)/layout.tsx");

    expect(clubPage).toContain("PublicClubPage");
    expect(clubPage).toContain("getClub(clubId)");
    expect(clubsPage).toContain("notFound()");
    expect(adminLayout).toContain("requireAdmin");
  });

  it("defines landing metadata without external assets", () => {
    expect(landing).toContain("FC Stats | Estatísticas para EA SPORTS FC Clubs");
    expect(landing).toContain("Dashboard automático de estatísticas, desempenho e partidas para clubes de EA SPORTS FC.");
    expect(landing).toContain("openGraph");
  });
});
