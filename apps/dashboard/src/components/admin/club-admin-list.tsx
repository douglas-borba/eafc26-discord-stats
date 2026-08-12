"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Plus, Settings2, Trash2, ExternalLink, Copy, Check } from "lucide-react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import type { AdminClub, ClubOperationalStatus } from "@/lib/admin/types";
import { AdminFeedback } from "./admin-feedback";

export function ClubAdminList() {
  const [clubs, setClubs] = useState<AdminClub[]>([]);
  const [statuses, setStatuses] = useState<Record<string, ClubOperationalStatus>>({});
  const [loading, setLoading] = useState(true);
  const [busyClub, setBusyClub] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<AdminClub | null>(null);
  const [copiedClub, setCopiedClub] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminRequest<AdminClub[]>("/api/admin/clubs");
      const ordered = [...data].sort((a, b) => a.clubId.localeCompare(b.clubId));
      setClubs(ordered);
      const settled = await Promise.allSettled(
        ordered.map((club) => adminRequest<ClubOperationalStatus>(`/api/admin/clubs/${club.clubId}/status`)),
      );
      setStatuses(Object.fromEntries(settled.flatMap((result) => result.status === "fulfilled" ? [[result.value.clubId, result.value]] : [])));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar os clubes.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function removeClub(club: AdminClub) {
    setBusyClub(club.clubId);
    setError(null);
    setSuccess(null);
    setConfirmRemove(null);
    try {
      await adminRequest<void>(`/api/admin/clubs/${club.clubId}`, { method: "DELETE" });
      setClubs((current) => current.filter((item) => item.clubId !== club.clubId));
      setSuccess(`${club.displayName} foi removido. O histórico de partidas foi preservado.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível remover o clube.");
    } finally {
      setBusyClub(null);
    }
  }

  return (
    <section>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-[0.14em] text-accent">Plataforma</p>
          <h1 className="text-2xl font-semibold text-text-primary">Clubes monitorados</h1>
          <p className="mt-1 text-sm text-muted">Gerencie aquisição e publicação sem reiniciar a aplicação.</p>
        </div>
        <Link href="/admin/clubs/new" className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-accent-strong px-4 py-2 text-sm font-semibold text-white hover:bg-accent">
          <Plus className="h-4 w-4" /> Adicionar clube
        </Link>
      </div>

      <div className="mb-4 space-y-3">
        {error && <AdminFeedback message={error} />}
        {success && <AdminFeedback message={success} tone="success" />}
      </div>

      {confirmRemove && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Panel className="w-full max-w-md space-y-4">
            <h2 className="text-lg font-semibold text-text-primary">Remover clube</h2>
            <p className="text-sm text-text-soft">
              Tem certeza de que deseja remover <strong>{confirmRemove.displayName}</strong>?
            </p>
            <p className="text-sm text-muted">
              O monitoramento e o webhook serão removidos. O histórico de partidas existente será preservado.
            </p>
            <div className="flex gap-3 pt-2">
              <button type="button" onClick={() => setConfirmRemove(null)} className="min-h-10 flex-1 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised">
                Cancelar
              </button>
              <button type="button" disabled={busyClub === confirmRemove.clubId} onClick={() => void removeClub(confirmRemove)} className="min-h-10 flex-1 rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:bg-loss/90 disabled:opacity-50">
                {busyClub === confirmRemove.clubId ? "Removendo…" : "Remover clube"}
              </button>
            </div>
          </Panel>
        </div>
      )}

      {loading ? (
        <Panel><p role="status" className="text-sm text-muted">Carregando clubes…</p></Panel>
      ) : clubs.length === 0 ? (
        <Panel className="text-center"><p className="text-text-soft">Nenhum clube cadastrado.</p></Panel>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {clubs.map((club) => {
            const status = statuses[club.clubId];
            return (
              <Panel key={club.clubId} className="flex flex-col gap-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="truncate text-lg font-semibold text-text-primary">{club.displayName}</h2>
                    <p className="mt-0.5 font-mono text-xs text-muted">
                      ClubId {club.clubId} · {club.platform}
                      {club.isDefault && <span className="ml-2 text-accent">principal</span>}
                    </p>
                  </div>
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${healthBadgeColor(status?.healthIndicator)}`}>
                    <span className={`h-2 w-2 rounded-full ${healthDotColor(status?.healthIndicator)}`} />
                    {club.monitoringEnabled ? "Ativo" : "Inativo"}
                  </span>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div><dt className="text-xs text-muted">Discord</dt><dd className="mt-0.5 text-text-soft">{discordStatusLabel(club)}</dd></div>
                  <div><dt className="text-xs text-muted">Operação</dt><dd className="mt-0.5 text-text-soft">{status ? `${status.pollingStatus} · ${status.acquisitionStatus}` : "Carregando…"}</dd></div>
                  <div className="col-span-2"><dt className="text-xs text-muted">Última atividade</dt><dd className="mt-0.5 text-text-soft">{formatActivity(status)}</dd></div>
                </dl>
                <div className="mt-auto flex flex-col gap-2 sm:flex-row">
                  {!club.isDefault && (
                    <button type="button" disabled={busyClub === club.clubId} onClick={() => setConfirmRemove(club)} className="inline-flex min-h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-loss/40 px-3 py-2 text-sm font-medium text-loss hover:bg-loss/10 disabled:opacity-50">
                      <Trash2 className="h-4 w-4" /> {busyClub === club.clubId ? "Removendo…" : "Remover clube"}
                    </button>
                  )}
                  <Link href={`/admin/clubs/${club.clubId}`} className="inline-flex min-h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-border px-3 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised">
                    <Settings2 className="h-4 w-4" /> Detalhes
                  </Link>
                </div>
                <div className="flex gap-2">
                  <a href={`/${club.clubId}`} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-9 flex-1 items-center justify-center gap-2 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-text-soft hover:bg-surface-raised">
                    <ExternalLink className="h-3.5 w-3.5" /> Abrir dashboard
                  </a>
                  <button
                    type="button"
                    onClick={() => {
                      void navigator.clipboard.writeText(`${window.location.origin}/${club.clubId}`);
                      setCopiedClub(club.clubId);
                      setTimeout(() => setCopiedClub((prev) => prev === club.clubId ? null : prev), 2000);
                    }}
                    className="inline-flex min-h-9 flex-1 items-center justify-center gap-2 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-text-soft hover:bg-surface-raised"
                  >
                    {copiedClub === club.clubId ? <><Check className="h-3.5 w-3.5 text-win" /> Link copiado.</> : <><Copy className="h-3.5 w-3.5" /> Copiar link</>}
                  </button>
                </div>
              </Panel>
            );
          })}
        </div>
      )}
    </section>
  );
}

function discordStatusLabel(club: AdminClub) {
  if (club.discordConfigured) return "Configurado";
  if (club.discordReferencePresent) return "Reconfiguração necessária";
  return "Não configurado";
}

function formatActivity(status?: ClubOperationalStatus) {
  const value = status?.lastSuccessAt ?? status?.lastPollAt;
  if (!value) return "Nenhuma atividade registrada";
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function healthBadgeColor(indicator?: string) {
  switch (indicator) {
    case "healthy": return "bg-win/15 text-win";
    case "warning": return "bg-yellow-500/15 text-yellow-400";
    case "error": return "bg-loss/15 text-loss";
    default: return "bg-surface-raised text-muted";
  }
}

function healthDotColor(indicator?: string) {
  switch (indicator) {
    case "healthy": return "bg-win";
    case "warning": return "bg-yellow-400";
    case "error": return "bg-loss";
    default: return "bg-muted";
  }
}
