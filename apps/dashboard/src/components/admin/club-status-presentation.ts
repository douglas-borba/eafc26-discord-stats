import type { AdminClub } from "@/lib/admin/types";

export function accessStatusPresentation(accessStatus: AdminClub["accessStatus"]) {
  switch (accessStatus) {
    case "ACTIVE": return { label: "Ativo", badgeClass: "bg-win/15 text-win" };
    case "TRIAL": return { label: "Teste", badgeClass: "bg-yellow-400/15 text-yellow-400" };
    default: return { label: accessStatus, badgeClass: "bg-surface-raised text-text-soft" };
  }
}

export function monitoringLabel(club: AdminClub): string {
  if (club.accessStatus === "TRIAL" && !club.monitoringEnabled) return "Desativado durante o período de teste";
  return club.monitoringEnabled ? "Ativo" : "Desativado";
}

export function pollingLabel(status?: string): string {
  switch (status) {
    case "RUNNING": return "Em andamento";
    case "IDLE": return "Aguardando";
    case "DISABLED": return "Desativado";
    default: return status ?? "—";
  }
}

export function acquisitionLabel(status?: string): string {
  switch (status) {
    case "IDLE": return "Nenhuma aquisição em andamento";
    case "FETCHING":
    case "PROCESSING":
    case "PERSISTING":
    case "CACHING":
    case "DELIVERING": return "Em andamento";
    case "COMPLETED": return "Concluída";
    case "FAILED": return "Falhou";
    default: return status ?? "—";
  }
}
