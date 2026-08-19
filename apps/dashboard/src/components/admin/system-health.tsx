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
  const [resetting, setResetting] = useState(false);

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
        <>
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
          {health.canonicalReadDiagnostics && (
            <CanonicalReadDiagnosticsPanel
              diagnostics={health.canonicalReadDiagnostics}
              flags={health.runtimeFlags}
              resetting={resetting}
              onReset={async () => {
                setResetting(true);
                setError(null);
                try {
                  const result = await adminRequest<{ canonicalReadDiagnostics: typeof health.canonicalReadDiagnostics }>(
                    "/api/admin/system/canonical-read-diagnostics/reset",
                    { method: "POST" },
                  );
                  setHealth((current) => current ? { ...current, canonicalReadDiagnostics: result.canonicalReadDiagnostics } : current);
                } catch (reason) {
                  setError(reason instanceof Error ? reason.message : "Não foi possível zerar os contadores.");
                } finally {
                  setResetting(false);
                }
              }}
            />
          )}
        </>
      )}
    </section>
  );
}

function CanonicalReadDiagnosticsPanel({
  diagnostics,
  flags,
  resetting,
  onReset,
}: {
  diagnostics: NonNullable<SystemHealth["canonicalReadDiagnostics"]>;
  flags?: SystemHealth["runtimeFlags"];
  resetting: boolean;
  onReset: () => Promise<void>;
}) {
  const operationRows = Object.entries(diagnostics.operations).filter(([, value]) => value.calls > 0);
  const originRows = Object.entries(diagnostics.origins).filter(([, value]) => value.calls > 0);
  return (
    <Panel className="mt-4">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="font-semibold text-text-primary">Diagnóstico de leituras PostgreSQL</h2>
          <p className="mt-1 text-sm text-muted">Contadores em memória desde o último início da aplicação. Os bytes representam dados retornados às consultas da aplicação e não o egress faturado pelo Supabase.</p>
        </div>
        <button type="button" disabled={resetting} onClick={() => { void onReset(); }} className="inline-flex min-h-10 items-center justify-center rounded-lg border border-border px-4 py-2 text-sm text-text-soft hover:bg-surface-raised disabled:opacity-50">
          {resetting ? "Zerando…" : "Zerar contadores"}
        </button>
      </div>
      <dl className="mb-4 grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Instância" value={diagnostics.instanceId} />
        <Metric label="Período" value={`${formatDate(diagnostics.startedAt)} → ${diagnostics.lastUpdatedAt ? formatDate(diagnostics.lastUpdatedAt) : "agora"}`} />
        <Metric label="Chamadas" value={String(diagnostics.total.calls)} />
        <Metric label="Registros retornados" value={String(diagnostics.total.rows)} />
        <Metric label="Dados retornados" value={formatBytes(diagnostics.total.estimatedReturnedBytes)} />
        {flags && <Metric label="Runtime" value={`LLM: ${flag(flags.llmEnabled)} · Mirror: ${flag(flags.postgresMirrorEnabled)} · Sync: ${flag(flags.postgresSyncEnabled)}`} />}
      </dl>
      <ReadTable title="Por operação" rows={operationRows} />
      <ReadTable title="Por origem" rows={originRows} className="mt-5" />
    </Panel>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-muted">{label}</dt><dd className="mt-1 break-all text-text-soft">{value}</dd></div>;
}

function ReadTable({ title, rows, className = "" }: { title: string; rows: [string, { calls: number; rows: number; estimatedReturnedBytes: number }][]; className?: string }) {
  return <div className={className}>
    <h3 className="mb-2 text-sm font-medium text-text-primary">{title}</h3>
    {rows.length === 0 ? <p className="text-sm text-muted">Nenhuma leitura registrada neste período.</p> : (
      <div className="overflow-x-auto">
        <table className="w-full min-w-[520px] text-left text-sm">
          <thead className="border-b border-border text-muted"><tr><th className="pb-2 font-medium">{title === "Por operação" ? "Operação" : "Origem"}</th><th className="pb-2 text-right font-medium">Chamadas</th><th className="pb-2 text-right font-medium">Registros</th><th className="pb-2 text-right font-medium">Dados retornados</th></tr></thead>
          <tbody>{rows.map(([name, metric]) => <tr key={name} className="border-b border-border/60"><td className="py-2 text-text-soft">{name}</td><td className="py-2 text-right text-text-soft">{metric.calls}</td><td className="py-2 text-right text-text-soft">{metric.rows}</td><td className="py-2 text-right text-text-soft">{formatBytes(metric.estimatedReturnedBytes)}</td></tr>)}</tbody>
        </table>
      </div>
    )}
  </div>;
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

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function flag(value: boolean) { return value ? "Ativo" : "Desativado"; }
