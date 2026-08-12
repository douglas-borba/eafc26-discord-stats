import type { PublicationHistoryRecord } from "./types";

export type PublicationStatusTone = "success" | "neutral" | "warning" | "danger";

export interface PublicationStatusPresentation {
  label: string;
  description?: string;
  tone: PublicationStatusTone;
  actionLabel: string;
  disabled: boolean;
}

/**
 * Presentation-only translation of the persisted publication state. The
 * underlying state stays visible to the backend and is never changed here.
 */
export function publicationStatus(record?: PublicationHistoryRecord): PublicationStatusPresentation {
  if (!record) {
    return { label: "Sem histórico de publicação", tone: "neutral", actionLabel: "Enviar ao Discord", disabled: false };
  }

  switch (record.state) {
    case "DELIVERED":
      return { label: "Enviado", description: "Publicado no Discord", tone: "success", actionLabel: "Reenviar", disabled: false };
    case "BASELINED":
      return baselineStatus(record.baselineReason);
    case "FAILED_TRANSIENT":
      return { label: "Falha temporária", description: "A entrega não foi concluída", tone: "warning", actionLabel: "Tentar novamente", disabled: false };
    case "FAILED_PERMANENT":
      return { label: "Falha permanente", description: "O Discord recusou a entrega", tone: "danger", actionLabel: "Forçar envio", disabled: false };
    case "DELIVERY_UNCERTAIN":
      return { label: "Entrega incerta", description: "O Discord pode já ter recebido esta partida", tone: "warning", actionLabel: "Reenviar com cuidado", disabled: false };
    case "DELIVERING":
      return { label: "Enviando", description: "Uma entrega está em andamento", tone: "warning", actionLabel: "Enviando…", disabled: true };
    default:
      return { label: "Estado de publicação desconhecido", tone: "neutral", actionLabel: "Enviar ao Discord", disabled: false };
  }
}

function baselineStatus(reason: string | null): PublicationStatusPresentation {
  return reason === "FIRST_RUN"
    ? {
        label: "Não publicado — baseline inicial",
        description: "Registrada na inicialização e não enviada automaticamente",
        tone: "neutral",
        actionLabel: "Enviar ao Discord",
        disabled: false,
      }
    : reason === "NO_DESTINATION"
      ? {
          label: "Não enviado",
          description: "Discord indisponível na ocasião",
          tone: "warning",
          actionLabel: "Enviar agora",
          disabled: false,
        }
      : {
          label: "Não publicado",
          description: "Motivo do baseline indisponível",
          tone: "neutral",
          actionLabel: "Enviar ao Discord",
          disabled: false,
        };
}
