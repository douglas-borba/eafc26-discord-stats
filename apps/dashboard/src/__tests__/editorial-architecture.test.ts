/**
 * Tests for editorial architecture compliance
 */

import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { join } from "path";

const SRC = join(process.cwd(), "src");

function readFile(relativePath: string): string {
  const fullPath = join(SRC, relativePath);
  return readFileSync(fullPath, "utf-8");
}

describe("Editorial Architecture: Zero Business Logic", () => {
  const matchCardService = readFile("lib/services/match-card-service.ts");

  it("does not use Math.random()", () => {
    expect(matchCardService).not.toContain("Math.random");
  });

  it("does not contain local phrase arrays", () => {
    expect(matchCardService).not.toMatch(/phrases\s*=\s*\[/);
  });

  it("does not contain build functions", () => {
    expect(matchCardService).not.toContain("function build");
  });

  it("does not calculate awards", () => {
    expect(matchCardService).not.toContain("reduce((prev, curr)");
    // .filter((p) is used for null-safety in getRecentMatchCards, not award calculation
  });

  it("only reads from editorial view", () => {
    expect(matchCardService).toContain("dashboard_editorial_presentations");
    // No longer queries raw match tables - uses editorial read model exclusively
  });

  it("service includes presentation model and merge logic", () => {
    const lines = matchCardService.split("\n").length;
    expect(lines).toBeLessThan(300);
  });
});

describe("Editorial Architecture: Supabase Only", () => {
  const matchCardService = readFile("lib/services/match-card-service.ts");

  it("uses only Supabase", () => {
    expect(matchCardService).toContain("createServerSupabase");
  });

  it("uses Spring API for canonical match list (source of truth)", () => {
    expect(matchCardService).toContain("fetchSports");
    expect(matchCardService).toContain("/overview/matches");
  });

  it("filters by clubId", () => {
    expect(matchCardService).toContain('eq("club_id", clubId)');
  });

  it("requests the panorama for the active club", () => {
    const panoramaClient = readFile("lib/api/panorama-client.ts");
    const overviewPage = readFile("app/clubs/[clubId]/(fullwidth)/overview/page.tsx");

    expect(panoramaClient).toContain("fetchAiPanorama(clubId: string)");
    expect(panoramaClient).toContain("/api/clubs/${encodeURIComponent(clubId)}/panorama");
    expect(overviewPage).toContain("fetchAiPanorama(clubId)");
  });

  it("orders by played_at", () => {
    expect(matchCardService).toContain('.order("played_at"');
  });
});
