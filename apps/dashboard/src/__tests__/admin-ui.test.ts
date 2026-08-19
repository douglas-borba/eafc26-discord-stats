import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { formatBytes } from "@/components/admin/system-health";

const root = resolve(process.cwd(), "src");
const read = (path: string) => readFileSync(resolve(root, path), "utf8");

describe("admin route group structure", () => {
  it("renders temporary in-memory PostgreSQL read diagnostics with clear byte units and reset control", () => {
    const systemHealth = read("components/admin/system-health.tsx");
    expect(systemHealth).toContain("Diagnóstico de leituras PostgreSQL");
    expect(systemHealth).toContain("Contadores em memória desde o último início da aplicação");
    expect(systemHealth).toContain("Zerar contadores");
    expect(systemHealth).toContain("Por operação");
    expect(systemHealth).toContain("Por origem");
    expect(systemHealth).toContain("/api/admin/system/canonical-read-diagnostics/reset");
  });

  it("formats diagnostic byte values for human reading", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1_536)).toBe("1.5 KB");
    expect(formatBytes(2 * 1024 * 1024)).toBe("2.00 MB");
  });
  it("login page is outside the protected route group and uses password auth", () => {
    const login = read("app/admin/login/page.tsx");
    expect(login).toContain('"use client"');
    expect(login).toContain("signInWithPassword");
    expect(login).toContain('type="email"');
    expect(login).toContain('type="password"');
    expect(login).not.toContain("signInWithOtp");
    expect(login).not.toContain("emailRedirectTo");
    expect(login).not.toContain("magic");
  });

  it("no auth/callback route exists (magic link removed)", () => {
    const fs = require("node:fs");
    expect(fs.existsSync(resolve(root, "app/auth/callback/route.ts"))).toBe(false);
    expect(fs.existsSync(resolve(root, "app/auth"))).toBe(false);
  });

  it("protected layout lives inside the route group and calls requireAdmin", () => {
    const layout = read("app/admin/(protected)/layout.tsx");
    expect(layout).toContain("requireAdmin");
    expect(layout).toContain('redirect("/admin/login")');
  });

  it("keeps explicit operational controls scoped to the current club with isolated local feedback", () => {
    const detail = read("components/admin/club-admin-detail.tsx");
    expect(detail).toContain("Executar polling agora");
    expect(detail).toContain("Testar EA");
    expect(detail).toContain("Testar Discord");
    expect(detail).toContain("/api/admin/clubs/${clubId}/${path}");
    expect(detail).toContain('loadingLabel="Executando…"');
    expect(detail).toContain('loadingLabel="Testando EA…"');
    expect(detail).toContain('loadingLabel="Testando Discord…"');
    expect(detail).toContain("Polling concluído em");
    expect(detail).toContain("EA disponível");
    expect(detail).toContain("Mensagem de teste entregue com sucesso.");
    expect(detail).toContain("operationFeedback");
    expect(detail).toContain("operationsInFlight");
    expect(detail).toContain("operationsInFlight.ea === true");
    expect(detail).toContain("operationsInFlight.discord === true");
    expect(detail).toContain('result.status === "busy"');
    expect(detail).toContain("Não foi possível concluir a operação.");
    expect(detail).not.toContain('setSuccess(kind === "poll"');
  });

  it("no layout.tsx exists at the admin root that would guard login", () => {
    const fs = require("node:fs");
    expect(fs.existsSync(resolve(root, "app/admin/layout.tsx"))).toBe(false);
  });

  it("all protected admin pages are inside (protected) and not at admin root", () => {
    const fs = require("node:fs");
    expect(fs.existsSync(resolve(root, "app/admin/(protected)/clubs/page.tsx"))).toBe(true);
    expect(fs.existsSync(resolve(root, "app/admin/(protected)/clubs/new/page.tsx"))).toBe(true);
    expect(fs.existsSync(resolve(root, "app/admin/(protected)/clubs/[clubId]/page.tsx"))).toBe(true);
    expect(fs.existsSync(resolve(root, "app/admin/clubs/page.tsx"))).toBe(false);
  });
});

describe("multi-club administration UI", () => {
  it("presents the actual trial approval outcome and approved EA club identity", () => {
    const trialRequests = read("components/admin/trial-requests-list.tsx");
    expect(trialRequests).toContain('snapshot: "ready" | "unavailable" | "in_progress" | "not_required"');
    expect(trialRequests).toContain("AdminRequestError");
    expect(trialRequests).toContain("Clube EA:");
    expect(trialRequests).toContain("const result = await adminRequest<ApprovalResult>");
    expect(trialRequests).toContain("setNotice(result.message)");
  });

  it("lists operational summaries and supports club removal", () => {
    const source = read("components/admin/club-admin-list.tsx");
    const presentation = read("components/admin/club-status-presentation.ts");
    expect(source).toContain("/api/admin/clubs");
    expect(source).toContain("/status");
    expect(presentation).toContain("monitoringEnabled");
    expect(source).toContain('method: "DELETE"');
    expect(source).toContain("Remover clube");
    expect(source).toContain("Discord");
    expect(source).toContain("Última atividade");
    expect(source).toContain("accessStatusPresentation");
    expect(source).toContain("Última aquisição");
    expect(source).not.toContain('club.monitoringEnabled ? "Ativo" : "Inativo"');
    expect(presentation).toContain('case "TRIAL": return { label: "Teste"');
    expect(presentation).toContain("Desativado durante o período de teste");
    expect(presentation).toContain('case "FAILED": return "Falhou"');
  });

  it("keeps access, monitoring, and acquisition presentation separate in club details", () => {
    const detail = read("components/admin/club-admin-detail.tsx");
    expect(detail).toContain('label="Monitoramento"');
    expect(detail).toContain('label="Polling"');
    expect(detail).toContain('label="Última aquisição"');
    expect(detail).toContain("monitoringLabel(club)");
    expect(detail).toContain("acquisitionLabel(status?.acquisitionStatus)");
  });

  it("requires selecting an EA search result before registration", () => {
    const source = read("components/admin/new-club-form.tsx");
    expect(source).toContain("/api/admin/clubs/search?query=");
    expect(source).toContain("setSelected(candidate)");
    expect(source).toContain("Selecione um clube retornado pela busca da EA");
    expect(source).toContain("clubId: selected.clubId");
    expect(source).toContain("Este clube já está cadastrado.");
    expect(source).toContain("Ativar monitoramento imediatamente");
    expect(source).toContain("rankCandidates");
    expect(source).toContain("fallbackPrefix");
    expect(source).toContain("Não encontramos uma correspondência exata");
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
    expect(read("app/admin/(protected)/clubs/page.tsx")).toContain("ClubAdminList");
    expect(read("app/admin/(protected)/clubs/new/page.tsx")).toContain("NewClubForm");
    expect(read("app/admin/(protected)/clubs/[clubId]/page.tsx")).toContain("ClubAdminDetail");
    expect(read("components/admin/club-admin-detail.tsx")).toContain("Clube cadastrado com sucesso.");
    expect(read("components/layout/sidebar-nav.tsx")).toContain('href="/admin/clubs"');
  });

  it("requires explicit confirmation before sending one selected canonical match to Discord", () => {
    const detail = read("components/admin/club-admin-detail.tsx");
    expect(detail).toContain("Últimas partidas");
    expect(detail).toContain("Enviar ao Discord");
    expect(detail).toContain("setConfirmPublication(match)");
    expect(detail).toContain("force-publish");
    expect(detail).toContain("Partida enviada ao Discord.");
    expect(detail).not.toContain("forcePublish(match)");
    expect(detail).toContain("Esta partida já consta como enviada ao Discord. Reenviar pode gerar uma mensagem duplicada.");
    expect(detail).toContain("Não é possível confirmar se o Discord recebeu esta partida. Reenviar pode gerar duplicação.");
    expect(detail).toContain("O sistema não possui histórico suficiente para confirmar se esta partida já foi publicada no Discord. Reenviar pode gerar uma mensagem duplicada.");
  });
});
