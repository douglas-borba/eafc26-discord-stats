export interface AdminClub {
  clubId: string;
  displayName: string;
  platform: string;
  monitoringEnabled: boolean;
  discordConfigured: boolean;
  isDefault?: boolean;
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
  id: number;
  clubId: string | null;
  matchId: string | null;
  eventType: string;
  phase: string | null;
  status: "SUCCESS" | "FAILURE" | "WARNING" | "INFO";
  message: string | null;
  errorCode: string | null;
  durationMs: number | null;
  createdAt: string;
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
