import { describe, it, expect } from "vitest";
import { readFileSync, existsSync } from "fs";
import { join } from "path";

const SRC = join(__dirname, "..");

function readFile(relativePath: string): string {
  return readFileSync(join(SRC, relativePath), "utf-8");
}

describe("UX: Split-pane pages use searchParams", () => {
  it("matches page accepts ?match= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/matches/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("match:");
    expect(page).toContain("result.content[0]?.matchId");
  });

  it("players page accepts ?player= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/players/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("player:");
    expect(page).toContain("result.content[0]?.playerId");
  });

  it("opponents page accepts ?opponent= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/opponents/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("opponent:");
    expect(page).toContain("result.content[0]?.clubId");
  });
});

describe("UX: Detail routes redirect to searchParam versions", () => {
  it("matches/[matchId] redirects to ?match=", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/matches/[matchId]/page.tsx");
    expect(page).toContain("redirect");
    expect(page).toContain("?match=");
  });

  it("players/[playerId] redirects to ?player=", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/players/[playerId]/page.tsx");
    expect(page).toContain("redirect");
    expect(page).toContain("?player=");
  });

  it("opponents/[opponentClubId] redirects to ?opponent=", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/opponents/[opponentClubId]/page.tsx");
    expect(page).toContain("redirect");
    expect(page).toContain("?opponent=");
  });
});

describe("UX: Shell components exist for split-pane", () => {
  it("matches-shell.tsx exists and is a client component", () => {
    const shell = readFile("components/matches/matches-shell.tsx");
    expect(shell).toContain('"use client"');
    expect(shell).toContain("useRouter");
    expect(shell).toContain("useSearchParams");
  });

  it("players-shell.tsx exists and is a client component", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).toContain('"use client"');
    expect(shell).toContain("useRouter");
    expect(shell).toContain("useSearchParams");
  });

  it("opponents-shell.tsx exists and is a client component", () => {
    const shell = readFile("components/opponents/opponents-shell.tsx");
    expect(shell).toContain('"use client"');
    expect(shell).toContain("useRouter");
    expect(shell).toContain("useSearchParams");
  });
});

describe("UX: Mobile back buttons", () => {
  it("matches shell has mobile back button", () => {
    const shell = readFile("components/matches/matches-shell.tsx");
    expect(shell).toContain("Todas as partidas");
    expect(shell).toContain("ArrowLeft");
  });

  it("players shell has mobile back button", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).toContain("Todos os jogadores");
    expect(shell).toContain("ArrowLeft");
  });

  it("opponents shell has mobile back button", () => {
    const shell = readFile("components/opponents/opponents-shell.tsx");
    expect(shell).toContain("Todos os adversários");
    expect(shell).toContain("ArrowLeft");
  });
});

describe("UX: Active item uses inset left bar (original style)", () => {
  it("match list uses inset shadow for active state", () => {
    const shell = readFile("components/matches/matches-shell.tsx");
    expect(shell).toContain("shadow-[inset_3px_0_var(--color-accent)]");
  });

  it("player list uses inset shadow for active state", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).toContain("shadow-[inset_3px_0_var(--color-accent)]");
  });

  it("opponent list uses inset shadow for active state", () => {
    const shell = readFile("components/opponents/opponents-shell.tsx");
    expect(shell).toContain("shadow-[inset_3px_0_var(--color-accent)]");
  });

  it("sidebar nav uses inset shadow for active state", () => {
    const sidebar = readFile("components/layout/sidebar-nav.tsx");
    expect(sidebar).toContain("shadow-[inset_3px_0_var(--color-accent)]");
  });
});

describe("UX: Match detail has editorial sections", () => {
  const detail = readFile("components/matches/match-detail-view.tsx");

  it("has Personagens da Partida section", () => {
    expect(detail).toContain("Personagens da Partida");
  });

  it("has A História do Jogo section", () => {
    expect(detail).toContain("A História do Jogo");
  });

  it("has O Time em Números section", () => {
    expect(detail).toContain("O Time em Números");
  });

  it("has Jogador por Jogador section", () => {
    expect(detail).toContain("Jogador por Jogador");
  });

  it("shows editorial messages for characters", () => {
    expect(detail).toContain("Uma atuação que fez a diferença nesta partida.");
    expect(detail).toContain("Consistência e segurança para proteger o time.");
  });

  it("has editorial narrative cards for non-redundant stories", () => {
    expect(detail).toContain("Poder de Fogo");
    expect(detail).toContain("Construção das Jogadas");
    expect(detail).toContain("Muralha");
    expect(detail).toContain("Ponto de Atenção");
    expect(detail).toContain("Momento Disciplinar");
  });

  it("filters out redundant story types from editorial timeline", () => {
    expect(detail).toContain("REDUNDANT_STORY_TYPES");
    expect(detail).toContain("MATCH_OUTCOME");
    expect(detail).toContain("AWARD");
    expect(detail).toContain("GOALS");
    expect(detail).toContain("ASSISTS");
  });

  it("prioritizes stories by relevance not fixed order", () => {
    expect(detail).toContain("cards.sort((a, b) => b.priority - a.priority)");
  });

  it("keeps storyRegistry for audit section", () => {
    expect(detail).toContain("getStoryPresentation");
  });

  it("shows unawarded characters with reduced opacity", () => {
    expect(detail).toContain("opacity-50");
    expect(detail).toContain("Não concedido nesta partida.");
  });

  it("links player names to player profiles", () => {
    expect(detail).toContain("/players?player=");
  });

  it("links opponent name to opponent history", () => {
    expect(detail).toContain("/opponents?opponent=");
  });
});

describe("UX: Player profile completeness", () => {
  const profile = readFile("components/players/player-profile-view.tsx");

  it("has 'Perfil histórico' kicker", () => {
    expect(profile).toContain("Perfil histórico");
  });

  it("shows W/D/L record badges computed from recent matches", () => {
    expect(profile).toContain('m.outcome === "WIN"');
    expect(profile).toContain('m.outcome === "DRAW"');
    expect(profile).toContain('m.outcome === "LOSS"');
  });

  it("displays record badge values (not hardcoded 0)", () => {
    expect(profile).toContain("value={wins}");
    expect(profile).toContain("value={draws}");
    expect(profile).toContain("value={losses}");
  });

  it("links recent matches to match detail", () => {
    expect(profile).toContain("/matches?match=");
  });
});

describe("UX: Opponent history completeness", () => {
  const history = readFile("components/opponents/opponent-history-view.tsx");

  it("shows W/D/L as large numbers", () => {
    expect(history).toContain("text-2xl font-black text-win");
    expect(history).toContain("text-2xl font-black text-draw");
    expect(history).toContain("text-2xl font-black text-loss");
  });

  it("shows aproveitamento percentage", () => {
    expect(history).toContain("Aproveitamento");
    expect(history).toContain("winRate");
  });

  it("highlights last match", () => {
    expect(history).toContain("Último Confronto Registrado");
  });

  it("links matches to match detail", () => {
    expect(history).toContain("/matches?match=");
  });
});

describe("UX: Overview completeness", () => {
  const page = readFile("app/clubs/[clubId]/(fullwidth)/overview/page.tsx");

  it("fetches recent match cards", () => {
    expect(page).toContain("getRecentMatchCards");
    expect(page).toContain("clubId");
    expect(page).toContain("presentations");
  });

  it("fetches top players", () => {
    // Overview now focuses on match card only (Thymeleaf fidelity)
    // Top players were removed to match original Thymeleaf layout
    expect(page).not.toContain("getPlayers");
  });

  it("fetches recent matches for form", () => {
    // Recent form was removed to match original Thymeleaf layout
    expect(page).not.toContain("getMatches");
  });

  it("has editorial-cards layout", () => {
    expect(page).toContain("lg:grid-cols-[300px_minmax(0,1fr)]");
  });

  it("does not call Spring Boot local API", () => {
    // Must not depend on /api/match-card/latest from Spring Boot
    expect(page).not.toContain("/api/match-card/latest");
    expect(page).not.toContain("localhost");
    expect(page).not.toContain("BACKEND_API_URL");
  });
});

describe("UX: Overview sub-components exist", () => {
  it("overview-sequence-editorial.tsx is the active editorial component", () => {
    expect(existsSync(join(SRC, "components/overview/overview-sequence-editorial.tsx"))).toBe(true);
  });

  it("overview-latest-match.tsx removed (replaced by sequence editorial)", () => {
    expect(existsSync(join(SRC, "components/overview/overview-latest-match.tsx"))).toBe(false);
  });

  it("overview-recent-form.tsx removed", () => {
    expect(existsSync(join(SRC, "components/overview/overview-recent-form.tsx"))).toBe(false);
  });

  it("overview-top-players.tsx removed", () => {
    expect(existsSync(join(SRC, "components/overview/overview-top-players.tsx"))).toBe(false);
  });

  it("overview uses editorial sequence and match card components", () => {
    expect(existsSync(join(SRC, "components/overview/overview-actions.tsx"))).toBe(false);
    expect(existsSync(join(SRC, "components/overview/overview-last-match.tsx"))).toBe(false);
    const page = readFile("app/clubs/[clubId]/(fullwidth)/overview/page.tsx");
    expect(page).not.toContain("OverviewActions");
    expect(page).not.toContain("OverviewLatestMatch");
    expect(page).toContain("OverviewClubPanel");
    expect(page).toContain("OverviewMatchCard");
  });
});

describe("UX: Sidebar has tagline", () => {
  const sidebar = readFile("components/layout/sidebar-nav.tsx");

  it("shows tagline text", () => {
    expect(sidebar).toContain("Partidas viram histórias");
  });
});

describe("UX: Sidebar nav order", () => {
  const sidebar = readFile("components/layout/sidebar-nav.tsx");

  it("Jogadores appears immediately after Visão Geral", () => {
    const visaoIdx = sidebar.indexOf('"Visão Geral"');
    const jogadoresIdx = sidebar.indexOf('"Jogadores"');
    const partidasIdx = sidebar.indexOf('"Partidas"');
    expect(visaoIdx).toBeGreaterThan(-1);
    expect(jogadoresIdx).toBeGreaterThan(visaoIdx);
    expect(partidasIdx).toBeGreaterThan(jogadoresIdx);
  });
});

describe("UX: Players page sort and auto-select", () => {
  it("requests averageRating DESC from getPlayers", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/players/page.tsx");
    expect(page).toContain('"averageRating"');
    expect(page).toContain('"DESC"');
  });

  it("auto-selects the first player (highest rated) when no searchParam", () => {
    const page = readFile("app/clubs/[clubId]/(sidebar)/players/page.tsx");
    expect(page).toContain("selectedPlayerId ?? result.content[0]?.playerId");
  });

  it("repository sort uses matches played then name as tiebreakers", () => {
    const repo = readFile("lib/repositories/player-repository.ts");
    expect(repo).toContain("b.matchesPlayed - a.matchesPlayed");
    expect(repo).toContain('localeCompare(nameB, "pt-BR")');
  });

  it("PlayersShell relies on server-provided order without re-sorting", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).not.toContain(".sort(");
  });
});

describe("UX: Desktop split-pane layout", () => {
  it("matches shell uses lg:grid with list and detail columns", () => {
    const shell = readFile("components/matches/matches-shell.tsx");
    expect(shell).toContain("lg:grid lg:grid-cols-[minmax(280px,360px)_minmax(0,1fr)]");
  });

  it("players shell uses lg:grid with list and detail columns", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).toContain("lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)]");
  });

  it("opponents shell uses lg:grid with list and detail columns", () => {
    const shell = readFile("components/opponents/opponents-shell.tsx");
    expect(shell).toContain("lg:grid lg:grid-cols-[minmax(280px,350px)_minmax(0,1fr)]");
  });
});

describe("UX: Mobile responsive", () => {
  it("matches shell hides list when detail is shown on mobile", () => {
    const shell = readFile("components/matches/matches-shell.tsx");
    expect(shell).toContain("mobileShowDetail");
    expect(shell).toMatch(/hidden.*lg:block/);
  });

  it("players shell hides list when detail is shown on mobile", () => {
    const shell = readFile("components/players/players-shell.tsx");
    expect(shell).toContain("mobileShowDetail");
  });

  it("opponents shell hides list when detail is shown on mobile", () => {
    const shell = readFile("components/opponents/opponents-shell.tsx");
    expect(shell).toContain("mobileShowDetail");
  });
});

describe("Security: preserved constraints", () => {
  it("server-only import still present in supabase client", () => {
    const server = readFile("lib/supabase/server.ts");
    expect(server).toContain('import "server-only"');
  });

  it("no shell component imports supabase", () => {
    const shells = [
      "components/matches/matches-shell.tsx",
      "components/players/players-shell.tsx",
      "components/opponents/opponents-shell.tsx",
    ];
    for (const path of shells) {
      const content = readFile(path);
      expect(content, `${path} imports supabase`).not.toMatch(/supabase/i);
    }
  });

  it("all data-fetching pages have force-dynamic", () => {
    const pages = [
      "app/clubs/[clubId]/(sidebar)/matches/page.tsx",
      "app/clubs/[clubId]/(sidebar)/players/page.tsx",
      "app/clubs/[clubId]/(sidebar)/opponents/page.tsx",
      "app/clubs/[clubId]/(fullwidth)/overview/page.tsx",
    ];
    for (const path of pages) {
      const content = readFile(path);
      expect(content, `${path} missing force-dynamic`).toContain('export const dynamic = "force-dynamic"');
    }
  });

  it("redirect pages do not fetch data", () => {
    const redirects = [
      "app/clubs/[clubId]/(sidebar)/matches/[matchId]/page.tsx",
      "app/clubs/[clubId]/(sidebar)/players/[playerId]/page.tsx",
      "app/clubs/[clubId]/(sidebar)/opponents/[opponentClubId]/page.tsx",
    ];
    for (const path of redirects) {
      const content = readFile(path);
      expect(content, `${path} should not import services`).not.toMatch(/from.*services/);
      expect(content, `${path} should not import repositories`).not.toMatch(/from.*repositories/);
    }
  });

  it("all pages pass clubId to data queries", () => {
    const pages = [
      readFile("app/clubs/[clubId]/(sidebar)/matches/page.tsx"),
      readFile("app/clubs/[clubId]/(sidebar)/players/page.tsx"),
      readFile("app/clubs/[clubId]/(sidebar)/opponents/page.tsx"),
      readFile("app/clubs/[clubId]/(fullwidth)/overview/page.tsx"),
    ];
    for (const content of pages) {
      expect(content).toContain("clubId");
    }
  });
});
