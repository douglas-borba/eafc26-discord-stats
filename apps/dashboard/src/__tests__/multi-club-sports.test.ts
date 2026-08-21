import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const source = (path: string) => readFileSync(resolve(process.cwd(), path), "utf8");

describe("explicit multi-club sports consumption", () => {
  it("uses the route clubId for every definitive sports request", () => {
    const files = [
      "src/lib/repositories/match-repository.ts",
      "src/lib/repositories/player-repository.ts",
      "src/lib/api/panorama-client.ts",
    ].map(source).join("\n");
    expect(files).toContain("clubPath(clubId");
    expect(files).not.toContain("1104972");
    expect(files).not.toContain("Associação BF");
  });

  it("separates fetch identity by embedding clubId in the request path and disables shared fetch cache", () => {
    const client = source("src/lib/api/sports-client.ts");
    expect(client).toContain("encodeURIComponent(clubId)");
    expect(client).toContain('cache: "no-store"');
  });

  it("preserves clubId in navigation and exposes the active sports areas", () => {
    const navigation = source("src/components/layout/sidebar-nav.tsx");
    expect(navigation).toContain("`/clubs/${clubId}/${href}`");
    for (const destination of ["overview", "matches", "players"]) expect(navigation).toContain(`href: "${destination}"`);
    expect(navigation).not.toContain('href: "opponents"');
  });

  it("contains no fixed Association identity in multi-club UI sources", () => {
    const files = [
      "src/components/overview/overview-club-panel.tsx",
      "src/components/overview/overview-match-card.tsx",
      "src/components/players/players-shell.tsx",
      "src/components/players/player-profile-view.tsx",
    ].map(source).join("\n");
    expect(files).not.toContain("Associação BF");
  });
});
