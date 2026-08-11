"use client";

import { useCallback, useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import type { SystemHealth } from "@/lib/admin/types";
import { AdminFeedback } from "./admin-feedback";

export function SystemHealthView() {
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      setHealth(await adminRequest<SystemHealth>("/api/admin/system/health"));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar o health.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <Panel><p className="text-sm text-muted">Carregando health…</p></Panel>;

  return (
    <section>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-[0.14em] text-accent">Sistema</p>
          <h1 className="text-2xl font-semibold text-text-primary">Health do sistema</h1>
        </div>
        <button
          type="button"
          disabled={refreshing}
          onClick={() => { setRefreshing(true); void load().finally(() => setRefreshing(false)); }}
          className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-text-soft hover:bg-surface-raised disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4${refreshing ? " animate-spin" : ""}`} />
          {refreshing ? "Atualizando…" : "Atualizar"}
        </button>
      </div>

      {error && <div className="mb-4"><AdminFeedback message={error} /></div>}

      {health && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <HealthCard
            title="Aplicação"
            status={health.application.status}
            items={[
              ["Iniciada em", formatDate(health.application.startedAt)],
              ["Uptime", formatUptime(health.application.uptimeSeconds)],
            ]}
          />
          <HealthCard
            title="Postgres"
            status={health.postgres.status}
            items={[
              ...(health.postgres.latencyMs != null ? [["Latência", `${health.postgres.latencyMs}ms`] as const] : []),
              ...(health.postgres.error ? [["Erro", health.postgres.error] as const] : []),
            ]}
          />
          <HealthCard
            title="EA Gateway"
            status={health.eaGateway.status}
            items={[
              ...(health.eaGateway.latencyMs != null ? [["Latência", `${health.eaGateway.latencyMs}ms`] as const] : []),
              ...(health.eaGateway.statusCode != null ? [["HTTP", String(health.eaGateway.statusCode)] as const] : []),
              ...(health.eaGateway.message ? [["Mensagem", health.eaGateway.message] as const] : []),
              ...(health.eaGateway.error ? [["Erro", health.eaGateway.error] as const] : []),
            ]}
          />
          <HealthCard
            title="Scheduler"
            status={health.scheduler.status}
            items={[
              ...(health.scheduler.mostRecentPollAt ? [["Último ciclo", formatDate(health.scheduler.mostRecentPollAt)] as const] : []),
              ...(health.scheduler.monitoredClubCount != null ? [["Clubes monitorados", String(health.scheduler.monitoredClubCount)] as const] : []),
              ...(health.scheduler.reason ? [["Detalhe", health.scheduler.reason] as const] : []),
            ]}
          />
          <HealthCard
            title="Build"
            status="INFO"
            items={[
              ["Commit", health.build.commitSha?.slice(0, 8) ?? "—"],
              ["Branch", health.build.branch ?? "—"],
            ]}
          />
        </div>
      )}
    </section>
  );
}

function HealthCard({ title, status, items }: { title: string; status: string; items: readonly (readonly [string, string])[] }) {
  return (
    <Panel>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold text-text-primary">{title}</h2>
        <StatusBadge status={status} />
      </div>
      <dl className="space-y-2 text-sm">
        {items.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-2">
            <dt className="text-muted">{label}</dt>
            <dd className="text-right text-text-soft break-all">{value}</dd>
          </div>
        ))}
      </dl>
    </Panel>
  );
}

function StatusBadge({ status }: { status: string }) {
  const color = statusColor(status);
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${color}`}>
      <span className={`h-2 w-2 rounded-full ${statusDot(status)}`} />
      {status}
    </span>
  );
}

function statusColor(status: string) {
  switch (status) {
    case "UP": case "HEALTHY": return "bg-win/15 text-win";
    case "DOWN": case "GATEWAY_DOWN": return "bg-loss/15 text-loss";
    case "STALE": case "EA_UPSTREAM_DOWN": return "bg-yellow-500/15 text-yellow-400";
    default: return "bg-surface-raised text-muted";
  }
}

function statusDot(status: string) {
  switch (status) {
    case "UP": case "HEALTHY": return "bg-win";
    case "DOWN": case "GATEWAY_DOWN": return "bg-loss";
    case "STALE": case "EA_UPSTREAM_DOWN": return "bg-yellow-400";
    default: return "bg-muted";
  }
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "medium" }).format(new Date(iso));
}

function formatUptime(seconds: number) {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}
