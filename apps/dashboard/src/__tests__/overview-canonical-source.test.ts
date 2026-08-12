import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("overview uses canonical_matches as source of truth", () => {
  const matchCardService = read("lib/services/match-card-service.ts");
  const overviewPage = read("app/clubs/[clubId]/(fullwidth)/overview/page.tsx");

  describe("getOverviewCards merges canonical + editorial", () => {
    it("fetches canonical matches from Spring API first", () => {
      expect(matchCardService).toContain('fetchSports<CanonicalMatchList>(clubPath(clubId, "/overview/matches"))');
    });

    it("fetches editorial presentations from Supabase keyed by match_id", () => {
      expect(matchCardService).toContain('.from("dashboard_editorial_presentations")');
      expect(matchCardService).toContain('.in("match_id", matchIds)');
    });

    it("falls back to basic presentation when editorial is missing", () => {
      expect(matchCardService).toContain("canonicalToBasicPresentation");
    });

    it("builds basic presentation with all required fields from canonical data", () => {
      expect(matchCardService).toContain("ourName: m.ourClub.name");
      expect(matchCardService).toContain("oppName: m.opponentClub.name");
      expect(matchCardService).toContain("ourScore: m.ourClub.score");
      expect(matchCardService).toContain("oppScore: m.opponentClub.score");
      expect(matchCardService).toContain("matchId: m.matchId");
      expect(matchCardService).toContain("date: m.dateLabel");
    });

    it("basic presentation has null editorial sections (not missing)", () => {
      expect(matchCardService).toContain("goals: null");
      expect(matchCardService).toContain("assists: null");
      expect(matchCardService).toContain("highlights: null");
      expect(matchCardService).toContain("craque: null");
      expect(matchCardService).toContain("bagre: null");
    });

    it("preserves canonical ordering (not Supabase ordering)", () => {
      expect(matchCardService).toContain("matches.map((m) => editorialMap.get(m.matchId)");
    });

    it("respects the limit parameter on canonical matches", () => {
      expect(matchCardService).toContain("canonical.matches.slice(0, limit)");
    });
  });

  describe("overview page uses getOverviewCards", () => {
    it("imports getOverviewCards, not getRecentMatchCards", () => {
      expect(overviewPage).toContain("getOverviewCards");
      expect(overviewPage).not.toContain("getRecentMatchCards");
    });

    it("handles SportsApiUnavailable gracefully", () => {
      expect(overviewPage).toContain("SportsApiUnavailable");
      expect(overviewPage).toContain("Não foi possível carregar os dados agora");
    });

    it("re-throws non-unavailability errors", () => {
      expect(overviewPage).toContain("throw error");
    });
  });

  describe("multi-club isolation in editorial lookup", () => {
    it("filters editorial presentations by club_id", () => {
      expect(matchCardService).toContain('.eq("club_id", clubId)');
    });

    it("only looks up matchIds from the canonical response", () => {
      expect(matchCardService).toContain("const matchIds = matches.map((m) => m.matchId)");
    });
  });
});

describe("spring unavailability handling", () => {
  const sportsClient = read("lib/api/sports-client.ts");
  const clubLayout = read("app/clubs/[clubId]/layout.tsx");
  const publicClubPage = read("app/[clubId]/page.tsx");
  const homePage = read("app/page.tsx");

  it("sports client exports SportsApiUnavailable", () => {
    expect(sportsClient).toContain("export class SportsApiUnavailable");
  });

  it("sports client throws SportsApiUnavailable on network error", () => {
    expect(sportsClient).toContain("throw new SportsApiUnavailable(path)");
  });

  it("sports client throws SportsApiUnavailable on non-200/non-404 responses", () => {
    expect(sportsClient).toContain("throw new SportsApiUnavailable(`${path} status=");
  });

  it("club layout catches SportsApiUnavailable", () => {
    expect(clubLayout).toContain("SportsApiUnavailable");
    expect(clubLayout).toContain("Não foi possível carregar os dados agora");
  });

  it("public club page catches SportsApiUnavailable", () => {
    expect(publicClubPage).toContain("SportsApiUnavailable");
  });

  it("home page stays independent from Spring availability", () => {
    expect(homePage).not.toContain("SportsApiUnavailable");
    expect(homePage).not.toContain("listClubs");
    expect(homePage).not.toContain("getClub(");
  });

  it("404 for unknown clubs is preserved (not converted to unavailable)", () => {
    expect(publicClubPage).toContain("SportsApiNotFound");
    expect(publicClubPage).toContain("notFound()");
    expect(clubLayout).toContain("SportsApiNotFound");
    expect(clubLayout).toContain("notFound()");
  });
});

describe("public club catalog removed", () => {
  const overviewRepo = read("lib/repositories/overview-repository.ts");

  it("listClubs uses the admin endpoint, not the public catalog", () => {
    expect(overviewRepo).toContain('"/api/admin/clubs"');
    expect(overviewRepo).not.toContain('"/api/clubs"');
  });

  it("listClubs uses fetchSportsInternal with auth", () => {
    expect(overviewRepo).toContain("fetchSportsInternal");
  });

  it("getClub still uses the public club-scoped endpoint", () => {
    expect(overviewRepo).toContain("clubPath(clubId)");
  });
});
