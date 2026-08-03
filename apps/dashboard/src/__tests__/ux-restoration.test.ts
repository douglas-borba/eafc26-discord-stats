import { describe, it, expect } from "vitest";
import { readFileSync, existsSync } from "fs";
import { join } from "path";

const SRC = join(__dirname, "..");

function readFile(relativePath: string): string {
  return readFileSync(join(SRC, relativePath), "utf-8");
}

describe("UX: Split-pane pages use searchParams", () => {
  it("matches page accepts ?match= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/matches/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("match:");
    expect(page).toContain("result.content[0]?.matchId");
  });

  it("players page accepts ?player= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/players/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("player:");
    expect(page).toContain("result.content[0]?.playerId");
  });

  it("opponents page accepts ?opponent= searchParam and auto-selects first", () => {
    const page = readFile("app/clubs/[clubId]/opponents/page.tsx");
    expect(page).toContain("searchParams");
    expect(page).toContain("opponent:");
    expect(page).toContain("result.content[0]?.clubId");
  });
});

describe("UX: Detail routes redirect to searchParam versions", () => {
  it("matches/[matchId] redirects to ?match=", () => {
    const page = readFile("app/clubs/[clubId]/matches/[matchId]/page.tsx");
    expect(page).toContain("redirect");
    expect(page).toContain("?match=");
  });

  it("players/[playerId] redirects to ?player=", () => {
    const page = readFile("app/clubs/[clubId]/players/[playerId]/page.tsx");
    expect(page).toContain("redirect");
    expect(page).toContain("?player=");
  });

  it("opponents/[opponentClubId] redirects to ?opponent=", () => {
    const page = readFile("app/clubs/[clubId]/opponents/[opponentClubId]/page.tsx");
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

  it("shows editorial messages for stories", () => {
    expect(detail).toContain("Fez a Diferença");
    expect(detail).toContain("Perigo Constante");
    expect(detail).toContain("Passe de Precisão");
    expect(detail).toContain("Correio Extraviado");
    expect(detail).toContain("Muralha");
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
  const page = readFile("app/clubs/[clubId]/overview/page.tsx");

  it("fetches last match detail", () => {
    expect(page).toContain("getMatch");
    expect(page).toContain("latestDetail");
  });

  it("fetches top players", () => {
    expect(page).toContain("getPlayers");
    expect(page).toContain("topPlayers");
  });

  it("fetches recent matches for form", () => {
    expect(page).toContain("getMatches");
    expect(page).toContain("recentMatches");
  });

  it("has two-column layout", () => {
    expect(page).toContain("lg:grid lg:grid-cols-[340px_minmax(0,1fr)]");
  });
});

describe("UX: Overview sub-components exist", () => {
  it("overview-last-match.tsx exists", () => {
    expect(existsSync(join(SRC, "components/overview/overview-last-match.tsx"))).toBe(true);
  });

  it("overview-recent-form.tsx exists", () => {
    expect(existsSync(join(SRC, "components/overview/overview-recent-form.tsx"))).toBe(true);
  });

  it("overview-top-players.tsx exists", () => {
    expect(existsSync(join(SRC, "components/overview/overview-top-players.tsx"))).toBe(true);
  });
});

describe("UX: Sidebar has tagline", () => {
  const sidebar = readFile("components/layout/sidebar-nav.tsx");

  it("shows tagline text", () => {
    expect(sidebar).toContain("Partidas viram histórias");
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
      "app/clubs/[clubId]/matches/page.tsx",
      "app/clubs/[clubId]/players/page.tsx",
      "app/clubs/[clubId]/opponents/page.tsx",
      "app/clubs/[clubId]/overview/page.tsx",
    ];
    for (const path of pages) {
      const content = readFile(path);
      expect(content, `${path} missing force-dynamic`).toContain('export const dynamic = "force-dynamic"');
    }
  });

  it("redirect pages do not fetch data", () => {
    const redirects = [
      "app/clubs/[clubId]/matches/[matchId]/page.tsx",
      "app/clubs/[clubId]/players/[playerId]/page.tsx",
      "app/clubs/[clubId]/opponents/[opponentClubId]/page.tsx",
    ];
    for (const path of redirects) {
      const content = readFile(path);
      expect(content, `${path} should not import services`).not.toMatch(/from.*services/);
      expect(content, `${path} should not import repositories`).not.toMatch(/from.*repositories/);
    }
  });

  it("all pages pass clubId to data queries", () => {
    const pages = [
      readFile("app/clubs/[clubId]/matches/page.tsx"),
      readFile("app/clubs/[clubId]/players/page.tsx"),
      readFile("app/clubs/[clubId]/opponents/page.tsx"),
      readFile("app/clubs/[clubId]/overview/page.tsx"),
    ];
    for (const content of pages) {
      expect(content).toContain("clubId");
    }
  });
});
