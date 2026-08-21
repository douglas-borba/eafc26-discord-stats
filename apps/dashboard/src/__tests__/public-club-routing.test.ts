import { describe, expect, it } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("public club routing", () => {
  describe("/{clubId} route", () => {
    const page = read("app/[clubId]/page.tsx");

    it("exists as a top-level dynamic route", () => {
      expect(existsSync(resolve(root, "app/[clubId]/page.tsx"))).toBe(true);
    });

    it("renders the overview page for a valid clubId", () => {
      expect(page).toContain("OverviewPage");
      expect(page).toContain("getClub");
    });

    it("returns 404 for non-numeric clubId to avoid catching other routes", () => {
      expect(page).toContain("notFound()");
      expect(page).toContain("/^\\d+$/.test(clubId)");
    });

    it("returns 404 for unknown clubs without fallback to a default club", () => {
      expect(page).toContain("SportsApiNotFound");
      expect(page).toContain("notFound()");
      expect(page).not.toContain("redirect");
      expect(page).not.toContain("1104972");
    });
  });

  describe("/clubs no longer exposes public listing", () => {
    const clubsPage = read("app/clubs/page.tsx");

    it("does not list clubs publicly", () => {
      expect(clubsPage).not.toContain("listClubs");
      expect(clubsPage).not.toContain("map");
      expect(clubsPage).not.toContain("Link");
    });

    it("returns notFound", () => {
      expect(clubsPage).toContain("notFound");
    });
  });

  describe("existing /clubs/{clubId} routes are preserved", () => {
    it("overview route still exists", () => {
      expect(existsSync(resolve(root, "app/clubs/[clubId]/(fullwidth)/overview/page.tsx"))).toBe(true);
    });

    it("players route still exists", () => {
      expect(existsSync(resolve(root, "app/clubs/[clubId]/(sidebar)/players/page.tsx"))).toBe(true);
    });

    it("removed opponents routes redirect safely to matches", () => {
      const opponents = read("app/clubs/[clubId]/(sidebar)/opponents/page.tsx");
      const detail = read("app/clubs/[clubId]/(sidebar)/opponents/[opponentClubId]/page.tsx");
      expect(opponents).toContain("redirect");
      expect(opponents).toContain("/matches");
      expect(opponents).not.toContain("getOpponents");
      expect(opponents).not.toContain("searchParams");
      expect(detail).toContain("redirect");
      expect(detail).toContain("/matches");
    });

    it("matches route still exists", () => {
      expect(existsSync(resolve(root, "app/clubs/[clubId]/(sidebar)/matches/page.tsx"))).toBe(true);
    });
  });
});

describe("admin dashboard links", () => {
  const listComponent = read("components/admin/club-admin-list.tsx");
  const detailComponent = read("components/admin/club-admin-detail.tsx");

  describe("club list", () => {
    it("has an 'Abrir dashboard' link", () => {
      expect(listComponent).toContain("Abrir dashboard");
    });

    it("opens dashboard in a new tab", () => {
      expect(listComponent).toContain('target="_blank"');
    });

    it("links to /{clubId} using the club's id", () => {
      expect(listComponent).toContain("`/${club.clubId}`");
    });

    it("has a 'Copiar link' button", () => {
      expect(listComponent).toContain("Copiar link");
    });

    it("builds the link using window.location.origin, not hardcoded domain", () => {
      expect(listComponent).toContain("window.location.origin");
      expect(listComponent).not.toContain("eafc-stats.vercel.app");
      expect(listComponent).not.toContain("vercel.app");
    });

    it("shows 'Link copiado.' feedback after copying", () => {
      expect(listComponent).toContain("Link copiado.");
    });
  });

  describe("club detail", () => {
    it("has an 'Abrir dashboard' link", () => {
      expect(detailComponent).toContain("Abrir dashboard");
    });

    it("opens dashboard in a new tab", () => {
      expect(detailComponent).toContain('target="_blank"');
    });

    it("has a 'Copiar link' button", () => {
      expect(detailComponent).toContain("Copiar link");
    });

    it("builds the link using window.location.origin, not hardcoded domain", () => {
      expect(detailComponent).toContain("window.location.origin");
      expect(detailComponent).not.toContain("eafc-stats.vercel.app");
      expect(detailComponent).not.toContain("vercel.app");
    });

    it("shows 'Link copiado.' feedback after copying", () => {
      expect(detailComponent).toContain("Link copiado.");
    });
  });
});
