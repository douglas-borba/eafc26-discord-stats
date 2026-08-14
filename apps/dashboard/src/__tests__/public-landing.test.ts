import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("public landing route", () => {
  const landing = read("app/page.tsx");

  it("uses Club11 branding, not FC Stats", () => {
    expect(landing).toContain("Club11");
    expect(landing).toContain("CLUB11");
    expect(landing).not.toMatch(/FC Stats/i);
    expect(landing).not.toContain("FC STATS");
  });

  it("renders the approved headline and CTA", () => {
    expect(landing).toContain("Todo mundo acha que joga muito.");
    expect(landing).toContain("Agora dá pra provar.");
    expect(landing).toContain("Quero ver meu clube");
  });

  it("does not contain mock/fictitious data", () => {
    expect(landing).not.toContain("Clube Exemplo");
    expect(landing).not.toContain("Jogador 10");
    expect(landing).not.toContain("Jogador 9");
    expect(landing).not.toContain("ProductMockup");
    expect(landing).not.toContain("mockup");
  });

  it("uses HeroClubSnapshot for hero and OverviewShowcase for proof section", () => {
    expect(landing).toContain("HeroClubSnapshot");
    expect(landing).toContain("OverviewShowcase");
    expect(landing).not.toContain("Suspense");
  });

  it("does not depend on runtime backend for showcase", () => {
    expect(landing).not.toContain("SHOWCASE_CLUB_ID");
    expect(landing).not.toContain("getClub");
    expect(landing).not.toContain("getOverviewCards");
    expect(landing).not.toContain("process.env");
  });

  it("keeps the landing free from public club discovery and hardcoded client URLs", () => {
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

  it("defines Club11 metadata without external assets", () => {
    expect(landing).toContain("Club11 — O acompanhamento do seu clube no Pro Clubs");
    expect(landing).toContain("openGraph");
  });
});

describe("overview-showcase (hero + proof)", () => {
  const showcase = read("components/landing/overview-showcase.tsx");

  it("exports HeroClubSnapshot for the hero", () => {
    expect(showcase).toContain("export function HeroClubSnapshot");
    expect(showcase).toContain("hero-snapshot");
  });

  it("exports OverviewShowcase for the proof section", () => {
    expect(showcase).toContain("export function OverviewShowcase");
    expect(showcase).toContain("overview-proof");
  });

  it("imports data from the static snapshot, not runtime APIs", () => {
    expect(showcase).toContain("landing-showcase-data");
    expect(showcase).toContain("SHOWCASE_CLUB_NAME");
    expect(showcase).toContain("SHOWCASE_EDITORIAL");
    expect(showcase).toContain("SHOWCASE_CARDS");
  });

  it("does not depend on SHOWCASE_CLUB_ID or runtime data fetching", () => {
    expect(showcase).not.toContain("SHOWCASE_CLUB_ID");
    expect(showcase).not.toContain("process.env");
    expect(showcase).not.toContain("getClub");
    expect(showcase).not.toContain("getOverviewCards");
    expect(showcase).not.toContain("buildSequenceEditorial");
    expect(showcase).not.toContain("fetchSports");
    expect(showcase).not.toContain("supabase");
  });

  it("reuses OverviewMatchCard and OverviewClubSummary", () => {
    expect(showcase).toContain("OverviewMatchCard");
    expect(showcase).toContain("OverviewClubSummary");
  });

  it("does not use carousel, sidebar, or admin controls", () => {
    expect(showcase).not.toContain("Carousel");
    expect(showcase).not.toContain("sidebar");
    expect(showcase).not.toContain("admin");
    expect(showcase).not.toContain("Trial");
  });

  it("hero snapshot does not render match cards", () => {
    const heroFnMatch = showcase.match(/export function HeroClubSnapshot[\s\S]*?^}/m);
    const heroFnBody = heroFnMatch ? heroFnMatch[0] : "";
    expect(heroFnBody).not.toContain("OverviewMatchCard");
    expect(heroFnBody).not.toContain("presentation=");
  });

  it("returns null when no showcase cards exist", () => {
    expect(showcase).toContain("return null");
  });
});

describe("landing-showcase-data (static snapshot)", () => {
  const snapshotFile = read("components/landing/landing-showcase-data.ts");

  it("contains real Associação BF data, not mock", () => {
    expect(snapshotFile).toContain("Associação BF");
    expect(snapshotFile).not.toContain("Clube Exemplo");
    expect(snapshotFile).not.toContain("Jogador 10");
    expect(snapshotFile).not.toContain("Jogador 9");
  });

  it("documents its origin as real pipeline data", () => {
    expect(snapshotFile).toContain("real processed Club11 data");
    expect(snapshotFile).toContain("must not be replaced with fictional");
  });

  it("uses real domain types", () => {
    expect(snapshotFile).toContain("SequenceEditorial");
    expect(snapshotFile).toContain("MatchSummaryPresentation");
  });

  it("contains real match IDs and player names from the pipeline", () => {
    expect(snapshotFile).toContain("968624156790107");
    expect(snapshotFile).toContain("960632703180174");
  });

  it("does not import any runtime dependencies", () => {
    expect(snapshotFile).not.toContain("supabase");
    expect(snapshotFile).not.toContain("fetchSports");
    expect(snapshotFile).not.toContain("process.env");
  });
});

describe("overview-club-summary", () => {
  const summary = read("components/overview/overview-club-summary.tsx");

  it("exists as a reusable component", () => {
    expect(summary).toContain("OverviewClubSummary");
    expect(summary).toContain("SequenceEditorial");
  });

  it("does not contain navigation, sidebar or admin controls", () => {
    expect(summary).not.toContain("Link");
    expect(summary).not.toContain("usePathname");
    expect(summary).not.toContain("navItems");
    expect(summary).not.toContain("admin");
  });
});

describe("overview-club-panel remains intact", () => {
  const panel = read("components/overview/overview-club-panel.tsx");

  it("still exports OverviewClubPanel with full dashboard structure", () => {
    expect(panel).toContain("OverviewClubPanel");
    expect(panel).toContain("navItems");
    expect(panel).toContain("usePathname");
    expect(panel).toContain("Link");
  });
});

describe("trial request form", () => {
  const form = read("components/landing/trial-request-form.tsx");

  it("posts to the same API endpoint with the same payload shape", () => {
    expect(form).toContain("/api/trial-requests");
    expect(form).toContain("clubName");
    expect(form).toContain("requesterName");
    expect(form).toContain("contact");
  });

  it("uses updated CTA copy", () => {
    expect(form).toContain("Quero ver meu clube");
  });

  it("has persistent labels above inputs", () => {
    expect(form).toContain("Nome do clube");
    expect(form).toContain("Seu nome ou gamertag");
    expect(form).toContain("Como a gente te responde?");
    expect(form).toContain("<label");
  });
});

describe("discord section uses real asset", () => {
  const landing = read("app/page.tsx");

  it("references the real Discord match card image", () => {
    expect(landing).toContain("batista-flores-match-card.png");
  });

  it("presents the image in a Discord channel context", () => {
    expect(landing).toContain("landing-discord-channel");
    expect(landing).toContain("resultados");
    expect(landing).toContain("BOT");
  });
});

describe("landing CSS", () => {
  const css = read("app/landing.css");

  it("is scoped to .landing class", () => {
    expect(css).toContain(".landing {");
    expect(css).toContain("Club11");
  });

  it("respects prefers-reduced-motion", () => {
    expect(css).toContain("prefers-reduced-motion");
  });

  it("does not reference FC Stats", () => {
    expect(css).not.toMatch(/FC Stats/i);
  });

  it("has hero snapshot styles separate from overview proof styles", () => {
    expect(css).toContain(".hero-snapshot");
    expect(css).toContain(".overview-proof");
  });

  it("has form label styles", () => {
    expect(css).toContain(".landing-trial-label");
    expect(css).toContain(".landing-trial-field");
  });
});
