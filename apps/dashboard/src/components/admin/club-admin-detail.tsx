"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { ArrowLeft, RefreshCw, Trash2, ExternalLink, Copy, Check } from "lucide-react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import type { AdminClub, ClubOperationalStatus } from "@/lib/admin/types";
import { AdminFeedback } from "./admin-feedback";

export function ClubAdminDetail({ clubId }: { clubId: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [club, setClub] = useState<AdminClub | null>(null);
  const [status, setStatus] = useState<ClubOperationalStatus | null>(null);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(
    searchParams.get("created") === "1" ? "Clube cadastrado com sucesso." : null,
  );
  const [refreshing, setRefreshing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async (showFeedback = false) => {
    setLoading(true);
    setError(null);
    try {
      const [clubData, statusData] = await Promise.all([
        adminRequest<AdminClub>(`/api/admin/clubs/${clubId}`),
        adminRequest<ClubOperationalStatus>(`/api/admin/clubs/${clubId}/status`),
      ]);
      setClub(clubData);
      setStatus(statusData);
      if (showFeedback) setSuccess("Status atualizado.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar o clube.");
    } finally {
      setLoading(false);
    }
  }, [clubId]);

  useEffect(() => { void load(); }, [load]);

  async function saveWebhook(event: FormEvent) {
    event.preventDefault();
    if (!webhookUrl.trim()) { setError("Informe uma nova URL de webhook."); return; }
    await mutate(async () => {
      await adminRequest<AdminClub>(`/api/admin/clubs/${clubId}/discord`, {
        method: "PUT", body: JSON.stringify({ webhookUrl: webhookUrl.trim() }),
      });
      setWebhookUrl("");
      setSuccess("Webhook configurado.");
    });
  }

  async function removeWebhook() {
    await mutate(async () => {
      await adminRequest<AdminClub>(`/api/admin/clubs/${clubId}/discord`, { method: "DELETE" });
      setSuccess("Webhook removido.");
    });
  }

  async function removeClub() {
    setBusy(true);
    setError(null);
    setSuccess(null);
    setConfirmRemove(false);
    try {
      await adminRequest<void>(`/api/admin/clubs/${clubId}`, { method: "DELETE" });
      router.push("/admin/clubs?removed=1");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível remover o clube.");
      setBusy(false);
    }
  }

  async function mutate(operation: () => Promise<void>) {
    setBusy(true); setError(null); setSuccess(null);
    try { await operation(); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Não foi possível concluir a operação."); }
    finally { setBusy(false); }
  }

  if (loading && !club) return <Panel><p role="status" className="text-muted">Carregando clube…</p></Panel>;
  if (!club) return <div>{error && <AdminFeedback message={error} />}</div>;

  return (
    <section>
      <Link href="/admin/clubs" className="mb-5 inline-flex items-center gap-1.5 text-sm text-muted hover:text-text-primary"><ArrowLeft className="h-4 w-4" /> Clubes monitorados</Link>
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-text-primary">{club.displayName}</h1>
          <p className="mt-1 font-mono text-xs text-muted">
            ClubId {club.clubId} · {club.platform}
            {club.isDefault && <span className="ml-2 text-accent">principal</span>}
          </p>
        </div>
        <div className="flex gap-2">
          <a href={`/${clubId}`} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-text-soft hover:bg-surface-raised">
            <ExternalLink className="h-4 w-4" /> Abrir dashboard
          </a>
          <button
            type="button"
            onClick={() => {
              void navigator.clipboard.writeText(`${window.location.origin}/${clubId}`);
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            }}
            className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-text-soft hover:bg-surface-raised"
          >
            {copied ? <><Check className="h-4 w-4 text-win" /> Link copiado.</> : <><Copy className="h-4 w-4" /> Copiar link</>}
          </button>
          <button type="button" disabled={refreshing} onClick={() => { setRefreshing(true); void load(true).finally(() => setRefreshing(false)); }} className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-text-soft hover:bg-surface-raised disabled:opacity-50"><RefreshCw className={`h-4 w-4${refreshing ? " animate-spin" : ""}`} /> {refreshing ? "Atualizando…" : "Atualizar status"}</button>
        </div>
      </div>
      <div className="mb-4 space-y-3">{error && <AdminFeedback message={error} />}{success && <AdminFeedback message={success} tone="success" />}</div>

      {confirmRemove && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Panel className="w-full max-w-md space-y-4">
            <h2 className="text-lg font-semibold text-text-primary">Remover clube</h2>
            <p className="text-sm text-text-soft">
              Tem certeza de que deseja remover <strong>{club.displayName}</strong>?
            </p>
            <p className="text-sm text-muted">
              O monitoramento e o webhook serão removidos. O histórico de partidas existente será preservado.
            </p>
            <div className="flex gap-3 pt-2">
              <button type="button" onClick={() => setConfirmRemove(false)} className="min-h-10 flex-1 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised">
                Cancelar
              </button>
              <button type="button" disabled={busy} onClick={() => void removeClub()} className="min-h-10 flex-1 rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:bg-loss/90 disabled:opacity-50">
                {busy ? "Removendo…" : "Remover clube"}
              </button>
            </div>
          </Panel>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel>
          <h2 className="font-semibold text-text-primary">Operação</h2>
          <dl className="mt-4 grid grid-cols-2 gap-4 text-sm">
            <Status label="Monitoramento" value={club.monitoringEnabled ? "Ativo" : "Inativo"} />
            <Status label="Polling" value={status?.pollingStatus ?? "—"} />
            <Status label="Aquisição" value={status?.acquisitionStatus ?? "—"} />
            <Status label="Último sucesso" value={formatDate(status?.lastSuccessAt)} />
            <Status label="Última partida" value={status?.latestMatchId ?? "—"} />
            <Status label="Erro recente" value={status?.lastError ?? "Nenhum"} />
          </dl>
        </Panel>

        <Panel>
          <h2 className="font-semibold text-text-primary">Discord</h2>
          <p className="mt-1 text-sm text-muted">Status: {club.discordConfigured ? "Configurado" : "Não configurado"}</p>
          <form onSubmit={saveWebhook} className="mt-4 space-y-3">
            <label className="block"><span className="mb-1.5 block text-sm text-text-soft">Nova URL de webhook</span><input type="url" value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder="https://discord.com/api/webhooks/…" className="min-h-10 w-full rounded-lg border border-border bg-surface-raised px-3 py-2 text-text-primary outline-none focus:border-accent" /></label>
            <button disabled={busy || !webhookUrl.trim()} className="min-h-10 w-full rounded-lg bg-accent-strong px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{club.discordConfigured ? "Substituir webhook" : "Configurar webhook"}</button>
          </form>
          {club.discordConfigured && <button type="button" disabled={busy} onClick={() => void removeWebhook()} className="mt-3 inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-loss/40 px-4 py-2 text-sm font-medium text-loss hover:bg-loss/10 disabled:opacity-50"><Trash2 className="h-4 w-4" /> Remover webhook</button>}
          <p className="mt-4 text-xs text-muted">O webhook atual nunca é exibido. Para trocar, informe uma nova URL.</p>
        </Panel>
      </div>

      {!club.isDefault && (
        <Panel className="mt-6 border-loss/30">
          <h2 className="font-semibold text-loss">Zona de perigo</h2>
          <p className="mt-1 text-sm text-muted">A remoção exclui o clube do monitoramento. O histórico de partidas e estatísticas existentes serão preservados.</p>
          <button type="button" disabled={busy} onClick={() => setConfirmRemove(true)} className="mt-4 inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:bg-loss/90 disabled:opacity-50">
            <Trash2 className="h-4 w-4" /> {busy ? "Removendo…" : "Remover clube"}
          </button>
        </Panel>
      )}
    </section>
  );
}

function Status({ label, value }: { label: string; value: string }) { return <div><dt className="text-xs text-muted">{label}</dt><dd className="mt-1 break-words text-text-soft">{value}</dd></div>; }
function formatDate(value?: string | null) { return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—"; }
