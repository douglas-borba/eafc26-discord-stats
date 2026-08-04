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
    expect(matchCardService).not.toContain(".filter((p)");
  });

  it("only reads from editorial view", () => {
    expect(matchCardService).toContain("dashboard_editorial_presentations");
    // No longer queries raw match tables - uses editorial read model exclusively
  });

  it("service is minimal and focused", () => {
    const lines = matchCardService.split("\n").length;
    // Service includes TypeScript interfaces for the presentation model
    // Actual logic is under 20 lines (just fetch from Supabase)
    expect(lines).toBeLessThan(200);
  });
});

describe("Editorial Architecture: Supabase Only", () => {
  const matchCardService = readFile("lib/services/match-card-service.ts");

  it("uses only Supabase", () => {
    expect(matchCardService).toContain("createServerSupabase");
  });

  it("does not call Spring Boot API", () => {
    expect(matchCardService).not.toContain('fetch("/api');
    expect(matchCardService).not.toContain("localhost");
    expect(matchCardService).not.toContain("BACKEND_API_URL");
  });

  it("filters by clubId", () => {
    expect(matchCardService).toContain('eq("club_id", clubId)');
  });

  it("orders by played_at", () => {
    expect(matchCardService).toContain('.order("played_at"');
  });
});
