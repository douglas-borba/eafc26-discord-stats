"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { ArrowLeft, RefreshCw, Trash2, ExternalLink, Copy, Check } from "lucide-react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import type {
  AdminClub,
  AdminMatchListResponse,
  AdminMatchSummary,
  AdminPublicationHistoryResponse,
  ClubOperationalStatus,
  ForcePublishResponse,
  AdminOperationResponse,
  PublicationHistoryRecord,
} from "@/lib/admin/types";
import { AdminFeedback } from "./admin-feedback";
import { ClubEventTimeline } from "./club-event-timeline";

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
  const [showEvents, setShowEvents] = useState(false);
  const [recentMatches, setRecentMatches] = useState<AdminMatchSummary[]>([]);
  const [publicationRecords, setPublicationRecords] = useState<Record<string, PublicationHistoryRecord>>({});
  const [matchesError, setMatchesError] = useState<string | null>(null);
  const [confirmPublication, setConfirmPublication] = useState<AdminMatchSummary | null>(null);
  const [sendingMatchId, setSendingMatchId] = useState<string | null>(null);
  const [operation, setOperation] = useState<"poll" | "ea" | "discord" | null>(null);

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
      try {
        const [matchesData, publicationData] = await Promise.all([
          adminRequest<AdminMatchListResponse>(`/api/admin/clubs/${clubId}/matches`),
          adminRequest<AdminPublicationHistoryResponse>(`/api/admin/clubs/${clubId}/publication/history`),
        ]);
        setRecentMatches(matchesData.matches.slice(0, 10));
        setPublicationRecords(Object.fromEntries(publicationData.records.map((record) => [record.matchId, record])));
        setMatchesError(null);
      } catch (reason) {
        setMatchesError(reason instanceof Error ? reason.message : "Não foi possível carregar as últimas partidas.");
      }
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

  async function forcePublish(match: AdminMatchSummary) {
    setSendingMatchId(match.matchId);
    setConfirmPublication(null);
    setError(null);
    setSuccess(null);
    try {
      const result = await adminRequest<ForcePublishResponse>(
        `/api/admin/clubs/${clubId}/publication/${encodeURIComponent(match.matchId)}/force-publish`,
        { method: "POST" },
      );
      if (result.status !== "success") throw new Error(result.message || "Não foi possível enviar a partida ao Discord.");
      setPublicationRecords((current) => ({
        ...current,
        [match.matchId]: {
          matchId: match.matchId,
          state: "DELIVERED",
          updatedAt: Date.now() / 1000,
          attemptCount: (current[match.matchId]?.attemptCount ?? 0) + 1,
          lastAttemptAt: Date.now() / 1000,
          lastError: null,
          lastHttpStatus: null,
          baselineReason: null,
        },
      }));
      setSuccess("Partida enviada ao Discord.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível enviar a partida ao Discord.");
    } finally {
      setSendingMatchId(null);
    }
  }

  async function runOperation(kind: "poll" | "ea" | "discord") {
    setOperation(kind); setError(null); setSuccess(null);
    try {
      const path = kind === "poll" ? "poll" : kind === "ea" ? "ea/test" : "discord/test";
      const result = await adminRequest<AdminOperationResponse>(`/api/admin/clubs/${clubId}/${path}`, { method: "POST" });
      if (result.status === "failed") throw new Error(result.message ?? "A operação não foi concluída.");
      setSuccess(kind === "poll" ? "Polling concluído." : kind === "ea" ? "Teste da EA concluído." : "Teste do Discord concluído.");
      if (kind === "poll") void load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível concluir a operação.");
    } finally { setOperation(null); }
  }

  if (loading && !club) return <Panel><p role="status" className="text-muted">Carregando clube…</p></Panel>;
  if (!club) return <div>{error && <AdminFeedback message={error} />}</div>;

  return (
    <section>
      <Link href="/admin/clubs" className="mb-5 inline-flex items-center gap-1.5 text-sm text-muted hover:text-text-primary"><ArrowLeft className="h-4 w-4" /> Clubes monitorados</Link>
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex items-center gap-3">
          {status && <HealthDot indicator={status.healthIndicator} />}
          <div>
            <h1 className="text-2xl font-semibold text-text-primary">{club.displayName}</h1>
            <p className="mt-1 font-mono text-xs text-muted">
              ClubId {club.clubId} · {club.platform}
              {club.isDefault && <span className="ml-2 text-accent">principal</span>}
            </p>
          </div>
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

      {confirmPublication && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Panel className="w-full max-w-md space-y-4">
            <h2 className="text-lg font-semibold text-text-primary">Enviar ao Discord</h2>
            <p className="text-sm text-text-soft">
              Enviar <strong>{club.displayName} {confirmPublication.ourClub.score} × {confirmPublication.opponentClub.score} {confirmPublication.opponentClub.name}</strong> para o Discord?
            </p>
            <p className="text-sm text-muted">A ação é explícita e pode gerar uma mensagem duplicada.</p>
            <div className="flex gap-3 pt-2">
              <button type="button" onClick={() => setConfirmPublication(null)} className="min-h-10 flex-1 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised">Cancelar</button>
              <button type="button" onClick={() => void forcePublish(confirmPublication)} className="min-h-10 flex-1 rounded-lg bg-accent-strong px-4 py-2 text-sm font-semibold text-white hover:bg-accent">Enviar ao Discord</button>
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
            <Status label="Último polling" value={formatDate(status?.lastPollAt)} />
            <Status label="Último sucesso" value={formatDate(status?.lastSuccessAt)} />
            <Status label="Última partida" value={status?.latestMatchId ?? "—"} />
            <Status label="Erro recente" value={status?.lastError ?? "Nenhum"} tone={status?.lastError ? "error" : undefined} />
            <Status label="Saúde" value={healthLabel(status?.healthIndicator)} tone={status?.healthIndicator === "error" ? "error" : status?.healthIndicator === "warning" ? "warning" : undefined} />
          </dl>
        </Panel>

        <Panel>
          <h2 className="font-semibold text-text-primary">Discord</h2>
          <p className="mt-1 text-sm text-muted">Status: {discordStatusLabel(club)}</p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <Status label="Último envio OK" value={formatDate(status?.lastDiscordSuccess)} />
            <Status label="Último erro Discord" value={status?.lastDiscordError ?? "Nenhum"} tone={status?.lastDiscordError ? "error" : undefined} />
          </dl>
          <form onSubmit={saveWebhook} className="mt-4 space-y-3">
            <label className="block"><span className="mb-1.5 block text-sm text-text-soft">Nova URL de webhook</span><input type="url" value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder="https://discord.com/api/webhooks/…" className="min-h-10 w-full rounded-lg border border-border bg-surface-raised px-3 py-2 text-text-primary outline-none focus:border-accent" /></label>
            <button disabled={busy || !webhookUrl.trim()} className="min-h-10 w-full rounded-lg bg-accent-strong px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{club.discordConfigured ? "Substituir webhook" : "Configurar webhook"}</button>
          </form>
          {club.discordConfigured && <button type="button" disabled={busy} onClick={() => void removeWebhook()} className="mt-3 inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-loss/40 px-4 py-2 text-sm font-medium text-loss hover:bg-loss/10 disabled:opacity-50"><Trash2 className="h-4 w-4" /> Remover webhook</button>}
          <p className="mt-4 text-xs text-muted">O webhook atual nunca é exibido. Para trocar, informe uma nova URL.</p>
        </Panel>
      </div>

      <Panel className="mt-6">
        <h2 className="font-semibold text-text-primary">Operações</h2>
        <p className="mt-1 text-sm text-muted">Execute verificações pontuais deste clube. O polling usa o mesmo pipeline seguro do monitoramento.</p>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <button type="button" disabled={operation === "poll"} onClick={() => void runOperation("poll")} className="min-h-10 rounded-lg bg-accent-strong px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {operation === "poll" ? "Executando…" : "Executar polling agora"}
          </button>
          <button type="button" disabled={operation === "ea"} onClick={() => void runOperation("ea")} className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised disabled:opacity-50">
            {operation === "ea" ? "Testando…" : "Testar EA"}
          </button>
          <button type="button" disabled={operation === "discord"} onClick={() => void runOperation("discord")} className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium text-text-soft hover:bg-surface-raised disabled:opacity-50">
            {operation === "discord" ? "Testando…" : "Testar Discord"}
          </button>
        </div>
      </Panel>

      <Panel className="mt-6">
        <h2 className="font-semibold text-text-primary">Últimas partidas</h2>
        <p className="mt-1 text-sm text-muted">Escolha explicitamente uma partida persistida para reenviar ao Discord.</p>
        {matchesError ? (
          <p className="mt-4 text-sm text-loss">{matchesError}</p>
        ) : recentMatches.length === 0 ? (
          <p className="mt-4 text-sm text-muted">Nenhuma partida registrada.</p>
        ) : (
          <div className="mt-4 divide-y divide-border">
            {recentMatches.map((match) => {
              const publication = publicationRecords[match.matchId];
              const sending = sendingMatchId === match.matchId;
              return (
                <div key={match.matchId} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-medium text-text-primary">{match.ourClub.name} {match.ourClub.score} × {match.opponentClub.score} {match.opponentClub.name}</p>
                    <p className="mt-1 text-xs text-muted">{formatDate(match.playedAt)}{publication ? ` · Discord: ${publication.state}` : " · Discord: sem registro"}</p>
                  </div>
                  <button
                    type="button"
                    disabled={sendingMatchId !== null}
                    onClick={() => setConfirmPublication(match)}
                    className="inline-flex min-h-10 shrink-0 items-center justify-center rounded-lg border border-accent/50 px-3 py-2 text-sm font-medium text-accent hover:bg-accent/10 disabled:opacity-50"
                  >
                    {sending ? "Enviando…" : "Enviar ao Discord"}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </Panel>

      <div className="mt-6">
        <button
          type="button"
          onClick={() => setShowEvents((v) => !v)}
          className="mb-4 text-sm font-medium text-accent hover:text-accent/80"
        >
          {showEvents ? "Ocultar eventos" : "Mostrar timeline de eventos"}
        </button>
        {showEvents && <ClubEventTimeline clubId={clubId} />}
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

function discordStatusLabel(club: AdminClub) {
  if (club.discordConfigured) return "Configurado e disponível";
  if (club.discordReferencePresent) return "Precisa ser reconfigurado";
  return "Não configurado";
}

function HealthDot({ indicator }: { indicator: string }) {
  const color = { healthy: "bg-win", warning: "bg-yellow-400", error: "bg-loss", idle: "bg-muted" }[indicator] ?? "bg-muted";
  return <span className={`h-3 w-3 rounded-full ${color}`} title={healthLabel(indicator)} />;
}

function Status({ label, value, tone }: { label: string; value: string; tone?: "error" | "warning" }) {
  const cls = tone === "error" ? "text-loss" : tone === "warning" ? "text-yellow-400" : "text-text-soft";
  return <div><dt className="text-xs text-muted">{label}</dt><dd className={`mt-1 break-words ${cls}`}>{value}</dd></div>;
}

function healthLabel(indicator?: string) {
  switch (indicator) {
    case "healthy": return "Saudável";
    case "warning": return "Atenção";
    case "error": return "Erro";
    case "idle": return "Inativo";
    default: return "—";
  }
}

function formatDate(value?: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}
