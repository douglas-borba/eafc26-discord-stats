export interface AdminClub {
  clubId: string;
  displayName: string;
  platform: string;
  monitoringEnabled: boolean;
  discordConfigured: boolean;
  discordReferencePresent?: boolean;
  discordDestinationResolvable?: boolean;
  isDefault?: boolean;
}

export interface AdminMatchSummary {
  matchId: string;
  playedAt: string;
  dateLabel?: string;
  competition: string | null;
  ourClub: { id: string; name: string; score: number };
  opponentClub: { id: string; name: string; score: number };
}

export interface AdminMatchListResponse {
  matches: AdminMatchSummary[];
}

export interface AdminPublicationHistoryResponse {
  records: PublicationHistoryRecord[];
}

export interface ForcePublishResponse {
  status: "success" | "failed";
  message: string;
  outcome: string;
}

export interface ClubSearchCandidate {
  clubId: string;
  displayName: string;
  platform: string;
  currentDivision: number | null;
}

export interface ClubOperationalStatus {
  clubId: string;
  monitoringEnabled: boolean;
  acquisitionStatus: string;
  pollingStatus: string;
  lastPollAt: string | null;
  lastSuccessAt: string | null;
  lastError: string | null;
  latestMatchId: string | null;
  latestMatchTimestamp: string | null;
  discordConfigured: boolean;
  lastDiscordSuccess: string | null;
  lastDiscordError: string | null;
  healthIndicator: "healthy" | "warning" | "error" | "idle";
}

export interface SystemHealth {
  overall?: "UP" | "DEGRADED";
  application: { status: string; startedAt: string; uptimeSeconds: number };
  postgres: { status: string; latencyMs?: number; error?: string };
  eaGateway: { status: string; latencyMs?: number; statusCode?: number; message?: string; error?: string };
  scheduler: { status: string; mostRecentPollAt?: string; monitoredClubCount?: number; reason?: string };
  build: { commitSha: string | null; branch: string | null };
}

export interface OperationalEvent {
  id: number | null;
  matchId: string | null;
  eventType: string | null;
  phase: string | null;
  status: string | null;
  message: string | null;
  errorCode: string | null;
  durationMs: number | null;
  createdAt: string | null;
}

/** Exact response shape returned by GET /api/admin/clubs/{clubId}/events. */
export interface OperationalEventsResponse {
  events: OperationalEvent[];
}

/**
 * Keeps a malformed diagnostics response contained in the timeline instead of
 * allowing an unexpected persisted value to break the enclosing admin page.
 */
export function normalizeOperationalEvents(events: unknown): OperationalEvent[] {
  if (!Array.isArray(events)) return [];

  return events.flatMap((event): OperationalEvent[] => {
    if (!event || typeof event !== "object") return [];
    const value = event as Record<string, unknown>;
    return [{
      id: typeof value.id === "number" ? value.id : null,
      matchId: asNullableString(value.matchId),
      eventType: asNullableString(value.eventType),
      phase: asNullableString(value.phase),
      status: asNullableString(value.status),
      message: asNullableString(value.message),
      errorCode: asNullableString(value.errorCode),
      durationMs: typeof value.durationMs === "number" ? value.durationMs : null,
      createdAt: asNullableString(value.createdAt),
    }];
  });
}

function asNullableString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

export interface PublicationHistoryRecord {
  matchId: string;
  state: string;
  updatedAt: number;
  attemptCount: number;
  lastAttemptAt: number | null;
  lastError: string | null;
  lastHttpStatus: number | null;
  baselineReason: "FIRST_RUN" | "NO_DESTINATION" | null;
}

export interface AdminApiError {
  error: string;
  message: string;
}
