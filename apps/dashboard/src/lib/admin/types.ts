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
}

export interface AdminApiError {
  error: string;
  message: string;
}
