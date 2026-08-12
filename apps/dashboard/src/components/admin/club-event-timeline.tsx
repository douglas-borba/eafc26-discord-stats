"use client";

import { useCallback, useEffect, useState } from "react";
import { Panel } from "@/components/ui/panel";
import { adminRequest } from "@/lib/admin/browser-client";
import {
  normalizeOperationalEvents,
  type OperationalEvent,
  type OperationalEventsResponse,
} from "@/lib/admin/types";

export function ClubEventTimeline({ clubId }: { clubId: string }) {
  const [events, setEvents] = useState<OperationalEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const payload = await adminRequest<OperationalEventsResponse>(`/api/admin/clubs/${clubId}/events`);
      setEvents(normalizeOperationalEvents(payload?.events));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar eventos.");
    } finally {
      setLoading(false);
    }
  }, [clubId]);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <Panel><p className="text-sm text-muted">Carregando eventos…</p></Panel>;
  if (error) return <Panel><p className="text-sm text-loss">{error}</p></Panel>;
  if (events.length === 0) return <Panel><p className="text-sm text-muted">Nenhum evento registrado.</p></Panel>;

  return (
    <Panel>
      <h2 className="mb-4 font-semibold text-text-primary">Timeline de eventos</h2>
      <div className="space-y-2">
        {events.map((event) => (
          <div key={event.id} className="flex items-start gap-3 rounded-lg border border-border/50 px-3 py-2">
            <EventDot status={event.status} />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-medium text-text-soft">{event.eventType ?? "Evento operacional"}</span>
                <span className="text-xs text-muted">{event.phase ?? "Fase não informada"}</span>
                {event.matchId && <span className="font-mono text-xs text-muted">#{event.matchId}</span>}
                {event.durationMs != null && <span className="text-xs text-muted">{event.durationMs}ms</span>}
              </div>
              {event.message && <p className="mt-0.5 text-xs text-text-soft break-words">{event.message}</p>}
              {event.errorCode && <p className="mt-0.5 text-xs text-muted">Código: {event.errorCode}</p>}
            </div>
            <time className="shrink-0 text-xs text-muted">{formatTime(event.createdAt)}</time>
          </div>
        ))}
      </div>
    </Panel>
  );
}

function EventDot({ status }: { status: string | null }) {
  const color = {
    SUCCESS: "bg-win",
    FAILURE: "bg-loss",
    WARNING: "bg-yellow-400",
    INFO: "bg-accent",
  }[status ?? "unknown"] ?? "bg-muted";
  return <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${color}`} />;
}

function formatTime(iso: string | null) {
  if (!iso) return "Data indisponível";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "Data indisponível";

  try {
    return new Intl.DateTimeFormat("pt-BR", { timeStyle: "medium" }).format(date);
  } catch {
    return "Data indisponível";
  }
}
