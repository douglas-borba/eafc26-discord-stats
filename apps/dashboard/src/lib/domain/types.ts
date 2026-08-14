export interface Overview {
  clubId: string;
  clubName: string | null;
  totalMatches: number;
  wins: number;
  draws: number;
  losses: number;
  goalsFor: number;
  goalsAgainst: number;
  goalDifference: number;
  winRate: number;
  lastMatchAt: string | null;
}

export interface ClubSummary {
  clubId: string;
  clubName: string | null;
  totalMatches: number;
}

export interface MatchSummary {
  matchId: string;
  playedAt: string;
  ourClubId: string;
  ourClubName: string | null;
  opponentClubId: string | null;
  opponentClubName: string | null;
  ourScore: number;
  opponentScore: number;
  outcome: "WIN" | "DRAW" | "LOSS";
  matchType: string | null;
  completionStatus?: "COMPLETED" | "DNF" | "UNKNOWN";
  dnfClubId?: string | null;
}

export interface MatchDetail {
  matchId: string;
  playedAt: string;
  ourClubId: string;
  ourClubName: string | null;
  opponentClubId: string | null;
  opponentClubName: string | null;
  ourScore: number;
  opponentScore: number;
  outcome: "WIN" | "DRAW" | "LOSS";
  matchType: string | null;
  completionStatus?: "COMPLETED" | "DNF" | "UNKNOWN";
  dnfClubId?: string | null;
  players: MatchPlayer[];
  awards: MatchAwards;
  stories: Story[];
}

export interface MatchPlayer {
  playerId: string;
  displayName: string | null;
  platformName: string | null;
  proName: string | null;
  rating: number | null;
  goals: number;
  assists: number;
  shots: number;
  passesCompleted: number;
  passesAttempted: number;
  tacklesCompleted: number;
  tacklesAttempted: number;
  redCards: number;
  manOfTheMatch: boolean;
}

export interface MatchAwards {
  craque: Award | null;
  bagre: Award | null;
  xerife: Award | null;
}

export interface Award {
  winnerId: string | null;
  winnerName: string | null;
  reason: string | null;
}

export interface Story {
  type: string;
  priority: string;
  narrativeKey: string;
  content: Record<string, unknown>;
  involvedPlayers: string[];
}

export interface PlayerSummary {
  playerId: string;
  displayName: string | null;
  platformName: string | null;
  proName: string | null;
  matchesPlayed: number;
  totalGoals: number;
  totalAssists: number;
  averageRating: number | null;
  manOfTheMatchCount: number;
  redCardCount: number;
}

export interface PlayerProfile {
  playerId: string;
  displayName: string | null;
  platformName: string | null;
  proName: string | null;
  matchesPlayed: number;
  totalGoals: number;
  totalAssists: number;
  totalShots: number;
  averageRating: number | null;
  manOfTheMatchCount: number;
  redCardCount: number;
  totalPassesCompleted: number;
  totalPassesAttempted: number;
  totalTacklesCompleted: number;
  totalTacklesAttempted: number;
  recentMatches: PlayerMatch[];
}

export interface PlayerMatch {
  matchId: string;
  playedAt: string;
  opponentClubName: string | null;
  ourScore: number;
  opponentScore: number;
  outcome: "WIN" | "DRAW" | "LOSS";
  rating: number | null;
  goals: number;
  assists: number;
}

export interface OpponentSummary {
  clubId: string;
  clubName: string | null;
  matchesPlayed: number;
  wins: number;
  draws: number;
  losses: number;
  goalsFor: number;
  goalsAgainst: number;
  lastPlayedAt: string | null;
}

export interface OpponentHistory {
  clubId: string;
  clubName: string | null;
  matchesPlayed: number;
  wins: number;
  draws: number;
  losses: number;
  goalsFor: number;
  goalsAgainst: number;
  matches: MatchSummary[];
  currentRun: OpponentRun | null;
  runRecords: OpponentRunRecord[];
  playerLeaders: OpponentPlayerLeaders[];
}

export interface OpponentRun {
  type: string;
  label: string;
  count: number;
  matchIds: string[];
  tiedRuns: number;
}

export interface OpponentRunRecord {
  type: string;
  label: string;
  count: number;
  matchIds: string[];
  tiedRuns: number;
}

export interface OpponentPlayerLeaders {
  type: string;
  label: string;
  value: number;
  players: OpponentPlayer[];
}

export interface OpponentPlayer {
  playerId: string;
  name: string;
}

export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
