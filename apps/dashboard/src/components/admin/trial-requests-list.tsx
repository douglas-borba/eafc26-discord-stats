"use client";

import { useEffect, useState } from "react";
import { AdminRequestError, adminRequest } from "@/lib/admin/browser-client";
import { Panel } from "@/components/ui/panel";

type Request = {
  id: number;
  clubName: string;
  requesterName: string;
  contact: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  clubId?: string | null;
  createdAt: string;
};
type Candidate = { clubId: string; displayName: string; platform: string };
type ApprovalResult = {
  status: "approved";
  clubId: string;
  clubState: "TRIAL" | "ACTIVE";
  snapshot: "ready" | "unavailable" | "in_progress" | "not_required";
  message: string;
};

export function TrialRequestsList() {
  const [items, setItems] = useState<Request[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [search, setSearch] = useState<Record<number, Candidate[]>>({});
  const [busy, setBusy] = useState<number | null>(null);

  const load = async () => {
    try {
      setItems(await adminRequest<Request[]>("/api/admin/trial-requests"));
    } catch (error) {
      setError(messageFor(error, "Não foi possível carregar as solicitações."));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  async function find(id: number, query: string) {
    if (!query.trim()) return;
    try {
      const candidates = await adminRequest<Candidate[]>(`/api/admin/clubs/search?query=${encodeURIComponent(query)}`);
      setSearch((current) => ({ ...current, [id]: candidates }));
    } catch (error) {
      setError(messageFor(error, "Não foi possível buscar clubes na EA."));
    }
  }

  async function approve(item: Request, club: Candidate) {
    if (!confirm(`Associar ${item.clubName} ao clube EA ${club.displayName} (${club.clubId}) e iniciar o teste?`)) return;
    setBusy(item.id);
    setError(null);
    setNotice(null);
    try {
      const result = await adminRequest<ApprovalResult>(`/api/admin/trial-requests/${item.id}/approve`, {
        method: "POST",
        body: JSON.stringify({ clubId: club.clubId, displayName: club.displayName, platform: club.platform }),
      });
      setNotice(result.message);
      await load();
    } catch (error) {
      setError(messageFor(error, "Não foi possível aprovar a solicitação."));
    } finally {
      setBusy(null);
    }
  }

  async function reject(item: Request) {
    if (!confirm(`Rejeitar a solicitação de ${item.clubName}?`)) return;
    setBusy(item.id);
    setError(null);
    setNotice(null);
    try {
      await adminRequest(`/api/admin/trial-requests/${item.id}/reject`, { method: "POST" });
      await load();
    } catch (error) {
      setError(messageFor(error, "Não foi possível rejeitar a solicitação."));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section>
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-[.14em] text-accent">Comercial</p>
        <h1 className="text-2xl font-semibold text-text-primary">Solicitações de teste</h1>
      </div>
      {notice && <p className="mb-3 text-sm text-success">{notice}</p>}
      {error && <p className="mb-3 text-sm text-loss">{error}</p>}
      <div className="space-y-3">
        {items.map((item) => (
          <Panel key={item.id}>
            <div className="flex justify-between gap-4">
              <div>
                <h2 className="font-semibold text-text-primary">{item.clubName}</h2>
                <p className="text-sm text-text-soft">{item.requesterName} · {item.contact}</p>
                {item.status === "APPROVED" && item.clubId && <p className="mt-1 text-xs text-muted">Clube EA: {item.clubId}</p>}
                <p className="mt-1 text-xs text-muted">{new Date(item.createdAt).toLocaleString("pt-BR")}</p>
              </div>
              <span className="text-xs text-muted">{item.status}</span>
            </div>
            {item.status === "PENDING" && (
              <div className="mt-4 space-y-2">
                <div className="flex gap-2">
                  <input
                    aria-label={`Buscar clube para ${item.clubName}`}
                    className="min-w-0 flex-1 rounded border border-border bg-surface-raised px-3 py-2 text-sm"
                    placeholder="Buscar clube na EA"
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void find(item.id, event.currentTarget.value);
                      }
                    }}
                  />
                  <button className="rounded border border-border px-3 text-sm" onClick={(event) => {
                    const input = event.currentTarget.previousElementSibling as HTMLInputElement;
                    void find(item.id, input.value);
                  }}>Buscar</button>
                  <button disabled={busy === item.id} className="rounded border border-loss/50 px-3 text-sm text-loss" onClick={() => void reject(item)}>Rejeitar</button>
                </div>
                {search[item.id]?.map((club) => (
                  <button key={club.clubId} className="mr-2 rounded bg-accent/10 px-3 py-2 text-sm text-accent" disabled={busy === item.id} onClick={() => void approve(item, club)}>
                    {club.displayName} · {club.clubId}
                  </button>
                ))}
              </div>
            )}
          </Panel>
        ))}
      </div>
    </section>
  );
}

function messageFor(error: unknown, fallback: string): string {
  return error instanceof AdminRequestError ? error.message : fallback;
}
