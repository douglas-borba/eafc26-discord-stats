"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Activity, Plus, Settings2 } from "lucide-react";
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

  async function toggleMonitoring(club: AdminClub) {
    setBusyClub(club.clubId);
    setError(null);
    setSuccess(null);
    try {
      const updated = await adminRequest<AdminClub>(`/api/admin/clubs/${club.clubId}/monitoring`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !club.monitoringEnabled }),
      });
      setClubs((current) => current.map((item) => item.clubId === updated.clubId ? updated : item));
      setSuccess(`${updated.displayName}: monitoramento ${updated.monitoringEnabled ? "ativado" : "desativado"}.`);
      const status = await adminRequest<ClubOperationalStatus>(`/api/admin/clubs/${club.clubId}/status`);
      setStatuses((current) => ({ ...current, [club.clubId]: status }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível alterar o monitoramento.");
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
                    <p className="mt-0.5 font-mono text-xs text-muted">ClubId {club.clubId} · {club.platform}</p>
                  </div>
                  <span className={club.monitoringEnabled ? "rounded-full bg-win/15 px-2.5 py-1 text-xs font-medium text-win" : "rounded-full bg-surface-raised px-2.5 py-1 text-xs font-medium text-muted"}>
                    {club.monitoringEnabled ? "Ativo" : "Inativo"}
                  </span>
                </div>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div><dt className="text-xs text-muted">Discord</dt><dd className="mt-0.5 text-text-soft">{club.discordConfigured ? "Configurado" : "Não configurado"}</dd></div>
                  <div><dt className="text-xs text-muted">Operação</dt><dd className="mt-0.5 text-text-soft">{status ? `${status.pollingStatus} · ${status.acquisitionStatus}` : "Carregando…"}</dd></div>
                  <div className="col-span-2"><dt className="text-xs text-muted">Última atividade</dt><dd className="mt-0.5 text-text-soft">{formatActivity(status)}</dd></div>
                </dl>
                <div className="mt-auto flex flex-col gap-2 sm:flex-row">
                  <button type="button" disabled={busyClub === club.clubId} onClick={() => void toggleMonitoring(club)} className="inline-flex min-h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-border px-3 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised disabled:opacity-50">
                    <Activity className="h-4 w-4" /> {busyClub === club.clubId ? "Salvando…" : club.monitoringEnabled ? "Desativar" : "Ativar"}
                  </button>
                  <Link href={`/admin/clubs/${club.clubId}`} className="inline-flex min-h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-border px-3 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised">
                    <Settings2 className="h-4 w-4" /> Detalhes
                  </Link>
                </div>
              </Panel>
            );
          })}
        </div>
      )}
    </section>
  );
}

function formatActivity(status?: ClubOperationalStatus) {
  const value = status?.lastSuccessAt ?? status?.lastPollAt;
  if (!value) return "Nenhuma atividade registrada";
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
