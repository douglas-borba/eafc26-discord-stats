import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("multi-club administration UI", () => {
  it("lists operational summaries and supports monitoring changes", () => {
    const source = read("components/admin/club-admin-list.tsx");
    expect(source).toContain("/api/admin/clubs");
    expect(source).toContain("/status");
    expect(source).toContain("monitoringEnabled");
    expect(source).toContain('method: "PATCH"');
    expect(source).toContain("Discord");
    expect(source).toContain("Última atividade");
  });

  it("requires selecting an EA search result before registration", () => {
    const source = read("components/admin/new-club-form.tsx");
    expect(source).toContain("/api/admin/clubs/search?query=");
    expect(source).toContain("setSelected(candidate)");
    expect(source).toContain("Selecione um clube retornado pela busca da EA");
    expect(source).toContain("clubId: selected.clubId");
    expect(source).toContain("Este clube já está cadastrado.");
    expect(source).toContain("Ativar monitoramento imediatamente");
  });

  it("keeps webhook optional and never attempts to retrieve its current value", () => {
    const newClub = read("components/admin/new-club-form.tsx");
    const detail = read("components/admin/club-admin-detail.tsx");
    expect(newClub).toContain("(opcional)");
    expect(newClub).toContain("if (webhookUrl.trim())");
    expect(detail).toContain("O webhook atual nunca é exibido");
    expect(detail).toContain('method: "DELETE"');
    expect(detail).not.toContain("discordWebhookSecretReference");
  });

  it("shows loading success error and mobile-friendly card layouts", () => {
    const list = read("components/admin/club-admin-list.tsx");
    const form = read("components/admin/new-club-form.tsx");
    expect(list).toContain("Carregando clubes");
    expect(list).toContain("AdminFeedback");
    expect(list).toContain("md:grid-cols-2");
    expect(form).toContain("Buscando…");
    expect(form).toContain("Cadastrando…");
    expect(form).toContain("sm:flex-row");
  });

  it("provides admin list create and detail pages without changing sports routes", () => {
    expect(read("app/admin/clubs/page.tsx")).toContain("ClubAdminList");
    expect(read("app/admin/clubs/new/page.tsx")).toContain("NewClubForm");
    expect(read("app/admin/clubs/[clubId]/page.tsx")).toContain("ClubAdminDetail");
    expect(read("components/admin/club-admin-detail.tsx")).toContain("Clube cadastrado com sucesso.");
    expect(read("components/layout/sidebar-nav.tsx")).toContain('href="/admin/clubs"');
  });
});
