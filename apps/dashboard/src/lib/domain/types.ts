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
  label?: string | null;
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
  wins: number;
  draws: number;
  losses: number;
  ratedMatchCount: number;
  bagreCount: number;
  xerifeCount: number;
  xRay: PlayerXRay | null;
  recentMatches: PlayerMatch[];
}

export interface PlayerMetricPeriod {
  appearances: number;
  ratedAppearances: number;
  averageRating: number | null;
  goals: number;
  assists: number;
  directContributions: number;
  goalsPerMatch: number;
  assistsPerMatch: number;
  directContributionsPerMatch: number;
  passAccuracy: number | null;
  tackleEfficiency: number | null;
  finishingConversion: number | null;
  passesCompleted: number;
  passAttempts: number;
  tacklesCompleted: number;
  tackleAttempts: number;
  shots: number;
}

export interface PlayerXRay {
  currentForm: {
    state: "FORMING" | "RECENT_ONLY" | "COMPARED";
    recent: PlayerMetricPeriod | null;
    previous: PlayerMetricPeriod | null;
    differences: {
      averageRating: number | null;
      goalsPerMatch: number;
      assistsPerMatch: number;
      directContributionsPerMatch: number;
      passAccuracyPoints: number | null;
      tackleEfficiencyPoints: number | null;
      finishingConversionPoints: number | null;
    } | null;
  };
  trend: {
    status: "RISING" | "STABLE" | "FALLING" | "FORMING";
    recentRating: number | null;
    baselineRating: number | null;
    ratingDelta: number | null;
    metrics: Array<{
      type: "RATING" | "GOALS_PER_MATCH" | "ASSISTS_PER_MATCH" | "DIRECT_CONTRIBUTIONS_PER_MATCH";
      recentValue: number;
      baselineValue: number;
      delta: number;
    }>;
  };
  consistency: {
    state: "INSUFFICIENT_SAMPLE" | "AVAILABLE";
    ratedAppearances: number;
    averageRating: number | null;
    ratingStandardDeviation: number | null;
    ratingsAtLeastEight: number;
    ratingsAtLeastEightRate: number | null;
    ratingsAtLeastNine: number;
    ratingsAtLeastNineRate: number | null;
    ratingTenMatches: number;
  };
  attack: { goals:number; goalsPerMatch:number; shots:number; shotsPerMatch:number; finishingConversion:number | null };
  creation: { assists:number; assistsPerMatch:number; passesAttempted:number; passesCompleted:number; passAccuracy:number | null; directContributions:number; directContributionsPerMatch:number };
  defense: { tacklesAttempted:number; tacklesCompleted:number; tackleEfficiency:number | null; tacklesCompletedPerMatch:number };
  advancedCoverage: { eligibleAppearances:number; fullAppearances:number; partialAppearances:number; unavailableAppearances:number; coverage:"UNAVAILABLE"|"PARTIAL"|"FULL" };
  oneOnOne: { coveredAppearances:number; dribblesCompleted:number; opponentsBeaten:number } | null;
  recognitions: {
    craques:number;
    bagres:number;
    xerifes:number;
    eligibleAppearances:number;
    craqueRate:number | null;
    bagreRate:number | null;
    xerifeRate:number | null;
  };
  records: {
    mostGoalsInMatch: PlayerSingleMatchRecord | null;
    mostAssistsInMatch: PlayerSingleMatchRecord | null;
    mostDirectContributionsInMatch: PlayerSingleMatchRecord | null;
    scoringStreak:number;
    assistStreak:number;
    directContributionStreak:number;
    ratingTenMatches:number;
  };
  analysis: {
    summary:string;
    strengths:Array<{
      category:"OFFENSIVE_PRODUCTION"|"FINISHING"|"CREATION"|"PASSING"|"TACKLING";
      label:string;
      evidence:PlayerAnalysisEvidence;
      rule:string;
      message:string;
    }>;
    improvement: {
      state:"FOUND"|"INSUFFICIENT_EVIDENCE";
      opportunity: {
        source:"RECENT_REGRESSION"|"STRUCTURAL_LOW_EFFICIENCY";
        area:"RATING"|"GOALS"|"ASSISTS"|"DIRECT_CONTRIBUTIONS"|"PASSING"|"TACKLING"|"FINISHING";
        label:string;
        evidence:PlayerAnalysisEvidence;
        rule:string;
        message:string;
      } | null;
      message:string;
    };
  };
}

export interface PlayerAnalysisEvidence {
  value:number;
  unit:"RATING"|"PER_MATCH"|"PERCENTAGE"|"COUNT";
  numerator:number | null;
  denominator:number | null;
  appearances:number;
  baselineValue:number | null;
  delta:number | null;
  baselineNumerator:number | null;
  baselineDenominator:number | null;
  baselineAppearances:number | null;
}

export interface PlayerSingleMatchRecord {
  value:number;
  matchId:string;
  playedAt:string;
  opponentClubName:string | null;
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

export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
