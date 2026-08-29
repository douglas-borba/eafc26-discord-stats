import { describe, expect, it } from "vitest";
import { publicationStatus } from "@/lib/admin/publication-status";

const record = (state: string, baselineReason: string | null = null) => ({
  matchId: "match-1",
  state,
  updatedAt: 0,
  attemptCount: 0,
  lastAttemptAt: null,
  lastError: null,
  lastHttpStatus: null,
  baselineReason,
});

describe("publicationStatus", () => {
  it("translates delivered matches into a safe resend status", () => {
    expect(publicationStatus(record("DELIVERED"))).toMatchObject({ label: "Enviado", actionLabel: "Reenviar", tone: "success" });
  });

  it("represents an initial baseline as unverifiable publication history", () => {
    const initialBaseline = publicationStatus(record("BASELINED", "FIRST_RUN"));

    expect(initialBaseline).toMatchObject({
      label: "Histórico anterior",
      description: "Publicação anterior não verificável pelo sistema atual",
      actionLabel: "Reenviar",
      tone: "neutral",
    });
    expect(initialBaseline.label).not.toContain("Não publicado");
  });

  it("keeps a missing Discord destination distinct from the initial baseline", () => {
    expect(publicationStatus(record("BASELINED", "NO_DESTINATION"))).toMatchObject({ label: "Não enviado", description: "Discord indisponível na ocasião", actionLabel: "Enviar agora", tone: "warning" });
  });

  it("presents durable pending and retry exhaustion as distinct operational states", () => {
    expect(publicationStatus(record("PENDING"))).toMatchObject({
      label: "Aguardando publicação",
      description: "A entrega será processada automaticamente",
      actionLabel: "Enviar agora",
      tone: "neutral",
    });
    expect(publicationStatus(record("RETRY_EXHAUSTED"))).toMatchObject({
      label: "Recuperação automática agendada",
      description: "O sistema retomará a entrega em intervalo reduzido",
      actionLabel: "Reenviar",
      tone: "warning",
    });
  });

  it.each([
    ["FAILED_TRANSIENT", "Falha temporária", "Tentar novamente", "warning"],
    ["FAILED_PERMANENT", "Falha permanente", "Forçar envio", "danger"],
    ["DELIVERY_UNCERTAIN", "Entrega incerta", "Reenviar com cuidado", "warning"],
  ])("presents %s without changing its persisted state", (state, label, actionLabel, tone) => {
    expect(publicationStatus(record(state))).toMatchObject({ label, actionLabel, tone, disabled: false });
  });

  it("disables action only while a delivery is actually in progress", () => {
    expect(publicationStatus(record("DELIVERING"))).toMatchObject({ label: "Enviando", disabled: true });
  });

  it("handles absent and unknown publication data defensively", () => {
    expect(publicationStatus()).toMatchObject({ label: "Sem histórico de publicação", tone: "neutral" });
    expect(publicationStatus(record("FUTURE_STATE", "FUTURE_REASON"))).toMatchObject({ label: "Estado de publicação desconhecido", tone: "neutral" });
    expect(publicationStatus(record("BASELINED", "FUTURE_REASON"))).toMatchObject({ label: "Não publicado", tone: "neutral" });
  });
});
