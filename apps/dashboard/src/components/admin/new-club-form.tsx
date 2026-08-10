"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ArrowLeft, Search } from "lucide-react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import type { AdminClub, ClubSearchCandidate } from "@/lib/admin/types";
import { AdminFeedback } from "./admin-feedback";

export function NewClubForm() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ClubSearchCandidate[]>([]);
  const [selected, setSelected] = useState<ClubSearchCandidate | null>(null);
  const [monitoringEnabled, setMonitoringEnabled] = useState(true);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setSearching(true);
    setError(null);
    setSelected(null);
    try {
      const found = await adminRequest<ClubSearchCandidate[]>(`/api/admin/clubs/search?query=${encodeURIComponent(query.trim())}`);
      setResults(found);
      if (found.length === 0) setError("Nenhum clube encontrado com esse nome.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível buscar clubes.");
      setResults([]);
    } finally {
      setSearching(false);
    }
  }

  async function register(event: FormEvent) {
    event.preventDefault();
    if (!selected) {
      setError("Selecione um clube retornado pela busca da EA.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const registered = await adminRequest<AdminClub[]>("/api/admin/clubs");
      if (registered.some((club) => club.clubId === selected.clubId)) {
        setError("Este clube já está cadastrado.");
        return;
      }
      const club = await adminRequest<AdminClub>("/api/admin/clubs", {
        method: "POST",
        body: JSON.stringify({
          clubId: selected.clubId,
          displayName: selected.displayName,
          platform: selected.platform,
          monitoringEnabled,
        }),
      });
      if (webhookUrl.trim()) {
        await adminRequest<AdminClub>(`/api/admin/clubs/${club.clubId}/discord`, {
          method: "PUT",
          body: JSON.stringify({ webhookUrl: webhookUrl.trim() }),
        });
      }
      router.push(`/admin/clubs/${club.clubId}?created=1`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível cadastrar o clube.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="mx-auto max-w-3xl">
      <Link href="/admin/clubs" className="mb-5 inline-flex items-center gap-1.5 text-sm text-muted hover:text-text-primary">
        <ArrowLeft className="h-4 w-4" /> Clubes monitorados
      </Link>
      <h1 className="text-2xl font-semibold text-text-primary">Adicionar clube</h1>
      <p className="mt-1 text-sm text-muted">Busque na EA e selecione a identidade oficial do clube.</p>

      <Panel className="mt-6">
        <form onSubmit={search} className="flex flex-col gap-3 sm:flex-row">
          <label className="flex-1">
            <span className="mb-1.5 block text-sm font-medium text-text-soft">Nome do clube</span>
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ex.: BRASIL 2030" className="min-h-10 w-full rounded-lg border border-border bg-surface-raised px-3 py-2 text-text-primary outline-none focus:border-accent" />
          </label>
          <button disabled={searching || !query.trim()} className="mt-auto inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-accent-strong px-4 py-2 font-semibold text-white disabled:opacity-50">
            <Search className="h-4 w-4" /> {searching ? "Buscando…" : "Buscar"}
          </button>
        </form>
      </Panel>

      {error && <div className="mt-4"><AdminFeedback message={error} /></div>}

      {results.length > 0 && (
        <div className="mt-5 space-y-2" aria-label="Resultados da busca">
          <h2 className="text-sm font-semibold text-text-soft">Selecione o clube correto</h2>
          {results.map((candidate) => (
            <button key={candidate.clubId} type="button" onClick={() => { setSelected(candidate); setError(null); }} className={selected?.clubId === candidate.clubId ? "flex w-full items-center justify-between rounded-lg border border-accent bg-accent/10 p-4 text-left" : "flex w-full items-center justify-between rounded-lg border border-border bg-surface p-4 text-left hover:bg-surface-raised"}>
              <span><strong className="block text-text-primary">{candidate.displayName}</strong><span className="mt-1 block font-mono text-xs text-muted">ClubId {candidate.clubId}</span></span>
              <span className="text-xs text-muted">{candidate.platform}</span>
            </button>
          ))}
        </div>
      )}

      {selected && (
        <Panel className="mt-6">
          <form onSubmit={register} className="space-y-5">
            <div>
              <h2 className="text-lg font-semibold text-text-primary">Confirmar cadastro</h2>
              <dl className="mt-3 grid gap-3 sm:grid-cols-3">
                <div><dt className="text-xs text-muted">Nome</dt><dd className="mt-1 text-text-soft">{selected.displayName}</dd></div>
                <div><dt className="text-xs text-muted">ClubId</dt><dd className="mt-1 font-mono text-text-soft">{selected.clubId}</dd></div>
                <div><dt className="text-xs text-muted">Plataforma</dt><dd className="mt-1 text-text-soft">{selected.platform}</dd></div>
              </dl>
            </div>
            <label className="flex min-h-10 items-center gap-3 text-sm text-text-soft">
              <input type="checkbox" checked={monitoringEnabled} onChange={(event) => setMonitoringEnabled(event.target.checked)} className="h-4 w-4 accent-accent" />
              Ativar monitoramento imediatamente
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-medium text-text-soft">Discord webhook <span className="font-normal text-muted">(opcional)</span></span>
              <input type="url" value={webhookUrl} onChange={(event) => setWebhookUrl(event.target.value)} placeholder="https://discord.com/api/webhooks/…" className="min-h-10 w-full rounded-lg border border-border bg-surface-raised px-3 py-2 text-text-primary outline-none focus:border-accent" />
            </label>
            <button disabled={saving} className="min-h-11 w-full rounded-lg bg-win-strong px-4 py-2.5 font-semibold text-white hover:bg-win disabled:opacity-50">
              {saving ? "Cadastrando…" : "Cadastrar clube"}
            </button>
          </form>
        </Panel>
      )}
    </section>
  );
}
