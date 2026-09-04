import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("observation phrase reconciliation UI", () => {
  const explorer = read("components/admin/advanced-stats-explorer.tsx");
  const route = read("app/api/admin/explorer/clubs/[clubId]/matches/[matchId]/players/[playerId]/observations/reconcile/route.ts");

  it("reuses conservative textual suggestions without automatic mutation", () => {
    expect(explorer).toContain("findLiveFeedbackSuggestions(item.phrase, knownPhrases, 1)");
    expect(explorer).toContain("Possível frase conhecida");
    expect(explorer).toContain("setPendingReconciliation({ source: item, targetPhrase: suggestion.phrase })");
    expect(explorer).toContain("Confirmar reconciliação");
  });

  it("shows the selected source, target, player-match evidence and explicit confirmation", () => {
    expect(explorer).toContain("Frase atual:");
    expect(explorer).toContain("Frase de destino:");
    expect(explorer).toContain("Partida:");
    expect(explorer).toContain("Jogador:");
    expect(explorer).toContain("Observado:");
    expect(explorer).toContain("Confirmar reconciliação");
  });

  it("contains collisions locally and never sums evidence counts", () => {
    expect(explorer).toContain("TARGET_ALREADY_EXISTS");
    expect(explorer).toContain("Nenhuma evidência foi alterada.");
    expect(explorer).toContain("Contagens nunca são somadas");
  });

  it("refreshes observations only after a confirmed successful reconciliation and keeps Compare", () => {
    expect(explorer).toContain('if (result.status === "SUCCESS")');
    expect(explorer).toContain("await load()");
    expect(explorer).toContain(">Compare</button>");
  });

  it("uses an authenticated BFF route and never exposes the internal token", () => {
    expect(route).toContain("proxyAdminRequest");
    expect(route).toContain("export async function POST");
    expect(route).toContain("encodeURIComponent(clubId)");
    expect(route).toContain("encodeURIComponent(matchId)");
    expect(route).toContain("encodeURIComponent(playerId)");
    expect(explorer).not.toContain("ADMIN_INTERNAL_TOKEN");
  });
});
