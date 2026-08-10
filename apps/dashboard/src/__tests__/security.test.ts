import { describe, it, expect, vi } from "vitest";
import { readFileSync, readdirSync } from "fs";
import { join } from "path";

vi.mock("server-only", () => ({}));

const SRC = join(__dirname, "..");

function findFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && entry.name !== "node_modules" && entry.name !== ".next") {
      results.push(...findFiles(full, ext));
    } else if (entry.name.endsWith(ext) && !entry.name.endsWith(".test.ts")) {
      results.push(full);
    }
  }
  return results;
}

const sourceFiles = findFiles(SRC, ".ts").concat(findFiles(SRC, ".tsx"));
const allSource = sourceFiles.map((f) => ({ path: f, content: readFileSync(f, "utf-8") }));

describe("Security: credential configuration", () => {
  it("server.ts requires SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY", () => {
    const serverTs = readFileSync(join(SRC, "lib/supabase/server.ts"), "utf-8");
    expect(serverTs).toContain("process.env.SUPABASE_URL");
    expect(serverTs).toContain("process.env.SUPABASE_PUBLISHABLE_KEY");
    expect(serverTs).toMatch(/if\s*\(\s*!url\s*\|\|\s*!key\s*\)/);
    expect(serverTs).toContain("throw new Error");
  });

  it("server.ts does not use service_role or secret key", () => {
    const serverTs = readFileSync(join(SRC, "lib/supabase/server.ts"), "utf-8");
    expect(serverTs).not.toMatch(/SERVICE_ROLE/i);
    expect(serverTs).not.toMatch(/SECRET_KEY/i);
  });
});

describe("Security: no service_role references", () => {
  it("no source file references SERVICE_ROLE", () => {
    for (const { path, content } of allSource) {
      expect(content, `${path} references SERVICE_ROLE`).not.toMatch(/SERVICE_ROLE/i);
    }
  });

  it("no source file references secret key", () => {
    for (const { path, content } of allSource) {
      expect(content, `${path} references SECRET_KEY`).not.toMatch(/SECRET_KEY/i);
    }
  });
});

describe("Security: no NEXT_PUBLIC_ prefix on Supabase vars", () => {
  it("no source file uses NEXT_PUBLIC_SUPABASE", () => {
    for (const { path, content } of allSource) {
      expect(content, `${path} uses NEXT_PUBLIC_SUPABASE`).not.toMatch(/NEXT_PUBLIC_SUPABASE/);
    }
  });
});

describe("Security: server-only module", () => {
  it("supabase/server.ts imports server-only", () => {
    const serverTs = allSource.find((f) => f.path.includes("supabase/server.ts"));
    expect(serverTs).toBeDefined();
    expect(serverTs!.content).toContain('import "server-only"');
  });
});

describe("Security: repositories are read-only", () => {
  it("no repository uses insert, update, delete, upsert, or rpc", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    expect(repos.length).toBeGreaterThan(0);
    for (const { path, content } of repos) {
      expect(content, `${path} uses .insert`).not.toMatch(/\.insert\s*\(/);
      expect(content, `${path} uses .update`).not.toMatch(/\.update\s*\(/);
      expect(content, `${path} uses .delete`).not.toMatch(/\.delete\s*\(/);
      expect(content, `${path} uses .upsert`).not.toMatch(/\.upsert\s*\(/);
      expect(content, `${path} uses .rpc`).not.toMatch(/\.rpc\s*\(/);
    }
  });
});

describe("Security: client components do not import Supabase", () => {
  it("no 'use client' file imports supabase/server", () => {
    for (const { path, content } of allSource) {
      if (content.includes('"use client"') || content.includes("'use client'")) {
        expect(content, `${path} imports supabase`).not.toMatch(/supabase\/server/);
      }
    }
  });
});

describe("Security: sports repositories use the scoped Spring API", () => {
  it("no repository queries canonical_matches directly", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    for (const { path, content } of repos) {
      expect(content, `${path} queries canonical_matches`).not.toMatch(/from\(["']canonical_matches["']\)/);
    }
  });

  it("no repository queries player_match_stats directly", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    for (const { path, content } of repos) {
      expect(content, `${path} queries player_match_stats`).not.toMatch(/from\(["']player_match_stats["']\)/);
    }
  });

  it("sports repositories use the definitive club API", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    expect(repos.filter((f) => /match-repository|player-repository|opponent-repository/.test(f.path)).every((f) => f.content.includes("clubPath(clubId"))).toBe(true);
  });

  it("sports repositories no longer access Supabase directly", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    for (const repo of repos.filter((f) => /match-repository|player-repository|opponent-repository/.test(f.path))) {
      expect(repo.content).not.toContain("createServerSupabase");
    }
  });

  it("match detail uses compound club and match identity in its path", () => {
    const matchRepo = allSource.find((f) => f.path.includes("/repositories/match-repository"));
    expect(matchRepo).toBeDefined();
    expect(matchRepo!.content).toContain('clubPath(clubId, `/history/matches/${encodeURIComponent(matchId)}`)');
  });

  it("dashboard_matches view does not expose payload column", () => {
    const migrationPath = join(__dirname, "../../../../src/main/resources/db/migration/V3__dashboard_read_only_views.sql");
    const migration = readFileSync(migrationPath, "utf-8");
    const dashboardMatchesView = migration.match(
      /CREATE OR REPLACE VIEW public\.dashboard_matches[\s\S]*?FROM public\.canonical_matches;/
    );
    expect(dashboardMatchesView).toBeDefined();
    expect(dashboardMatchesView![0]).not.toMatch(/\bpayload\b/);
  });

  it("dashboard_match_detail view projects only interpretation, footballMatch, stories", () => {
    const migrationPath = join(__dirname, "../../../../src/main/resources/db/migration/V3__dashboard_read_only_views.sql");
    const migration = readFileSync(migrationPath, "utf-8");
    const detailView = migration.match(
      /CREATE OR REPLACE VIEW public\.dashboard_match_detail[\s\S]*?FROM public\.canonical_matches;/
    );
    expect(detailView).toBeDefined();
    expect(detailView![0]).toContain("payload->'interpretation'");
    expect(detailView![0]).toContain("payload->'footballMatch'");
    expect(detailView![0]).toContain("payload->'stories'");
    // Verify no raw "payload" column select — only jsonb_build_object projection
    expect(detailView![0]).toContain("jsonb_build_object");
  });
});

describe("Security: V3 migration structure", () => {
  const migrationPath = join(__dirname, "../../../../src/main/resources/db/migration/V3__dashboard_read_only_views.sql");
  const migration = readFileSync(migrationPath, "utf-8");

  it("enables RLS on canonical_matches", () => {
    expect(migration).toMatch(/ALTER TABLE.*canonical_matches.*ENABLE ROW LEVEL SECURITY/);
  });

  it("enables RLS on player_match_stats", () => {
    expect(migration).toMatch(/ALTER TABLE.*player_match_stats.*ENABLE ROW LEVEL SECURITY/);
  });

  it("creates SELECT-only policy on canonical_matches for anon", () => {
    expect(migration).toMatch(/CREATE POLICY.*ON.*canonical_matches\s+FOR SELECT TO anon/);
  });

  it("creates SELECT-only policy on player_match_stats for anon", () => {
    expect(migration).toMatch(/CREATE POLICY.*ON.*player_match_stats\s+FOR SELECT TO anon/);
  });

  it("does not grant INSERT, UPDATE, DELETE, or TRUNCATE to anon", () => {
    expect(migration).not.toMatch(/GRANT\s+(INSERT|UPDATE|DELETE|TRUNCATE).*TO\s+anon/i);
  });

  it("does not contain REVOKE ALL that would break security_invoker", () => {
    expect(migration).not.toMatch(/REVOKE ALL ON.*FROM anon/);
  });

  it("grants SELECT on underlying tables for security_invoker views", () => {
    expect(migration).toMatch(/GRANT SELECT ON.*canonical_matches.*TO anon/);
    expect(migration).toMatch(/GRANT SELECT ON.*player_match_stats.*TO anon/);
  });

  it("match views include club_id", () => {
    const matchViews = migration.match(/CREATE OR REPLACE VIEW public\.dashboard_match[\s\S]*?;/g) ?? [];
    expect(matchViews.length).toBe(2);
    for (const view of matchViews) {
      expect(view).toContain("club_id");
    }
  });
});

describe("Security: multi-club scoping", () => {
  it("all repository functions that query by entity receive clubId parameter", () => {
    const repos = allSource.filter((f) => f.path.includes("/repositories/"));
    for (const { path, content } of repos) {
      const exportedFns = content.match(/export async function (\w+)\(/g) ?? [];
      for (const fn of exportedFns) {
        const fnName = fn.match(/function (\w+)/)?.[1];
        if (fnName === "listClubs") continue;
        const fnBody = content.slice(content.indexOf(fn));
        const sig = fnBody.match(/export async function \w+\([^)]*\)/)?.[0] ?? "";
        expect(sig, `${path}:${fnName} must receive clubId`).toMatch(/clubId/);
      }
    }
  });

  it("match detail request scopes by clubId", () => {
    const matchRepo = allSource.find((f) => f.path.includes("/repositories/match-repository"));
    expect(matchRepo).toBeDefined();
    const detailFn = matchRepo!.content.slice(matchRepo!.content.indexOf("getMatchDetail"));
    expect(detailFn).toContain("clubPath(clubId");
  });

  it("player and opponent requests scope identity by clubId", () => {
    const playerRepo = readFileSync(join(SRC, "lib/repositories/player-repository.ts"), "utf-8");
    const opponentRepo = readFileSync(join(SRC, "lib/repositories/opponent-repository.ts"), "utf-8");

    expect(playerRepo).toContain("clubPath(clubId");
    expect(opponentRepo).toContain("clubPath(clubId");
  });

  it("no hardcoded club id in source", () => {
    for (const { path, content } of allSource) {
      expect(content, `${path} has hardcoded club id`).not.toMatch(/1104972/);
    }
  });
});
