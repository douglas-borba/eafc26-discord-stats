/**
 * Match Card Service
 *
 * Fetches pre-built MatchSummaryPresentation from editorial read model.
 * Zero business logic - pure data fetching.
 * No dependency on Spring Boot local API - works on Vercel.
 */

import { createServerSupabase } from "@/lib/supabase/server";

export interface MatchCardResponse {
  status: string;
  message?: string;
  presentation?: MatchSummaryPresentation;
  simulated?: boolean;
}

export interface MatchSummaryPresentation {
  ourName: string;
  oppName: string;
  ourScore: number;
  oppScore: number;
  outcome: MatchOutcome;
  date: string;
  timestamp: string;
  matchId: string;
  goals: GoalsSection | null;
  assists: AssistsSection | null;
  highlights: HighlightsSection | null;
  craque: CraqueSection | null;
  offensiveNarratives: OffensiveNarrativeSection[];
  bagre: BagreSection | null;
  redCard: RedCardSection | null;
  xerife: XerifeSection | null;
  passePrecisao: PassePrecisaoSection | null;
  correioExtraviado: CorreioExtraviadoSection | null;
  muralha: MuralhaSection | null;
  allPlayers?: PlayerRating[] | null;
}

export interface PlayerRating {
  name: string;
  rating: string;
  played: boolean;
}

export interface MatchOutcome {
  emoji: string;
  label: string;
  color: number;
  type: "WIN" | "DRAW" | "LOSS";
}

export interface GoalsSection {
  scorers: PlayerGoal[];
}

export interface PlayerGoal {
  name: string;
  count: number;
}

export interface AssistsSection {
  assisters: PlayerAssist[];
}

export interface PlayerAssist {
  name: string;
  count: number;
}

export interface HighlightsSection {
  top3: TopPlayer[];
  teamAverage: string | null;
}

export interface TopPlayer {
  medal: string;
  name: string;
  rating: string;
}

export interface CraqueSection {
  name: string;
  reason: string;
  phrase: string;
}

export interface OffensiveNarrativeSection {
  name: string;
  shots: number;
  goals: number;
  title: string;
  emoji: string;
  message: string;
}

export interface RedCardSection {
  name: string;
  redCards: number;
  phrase: string;
}

export interface BagreSection {
  name: string;
  rating: string;
  reason: string;
  tackleStats: string | null;
  passStats: string | null;
  phrase: string;
}

export interface XerifeSection {
  name: string;
  tacklesMade: number;
  tackleAttempts: number;
  successRate: number;
  phrase: string;
}

export interface PassePrecisaoSection {
  name: string;
  passesMade: number;
  passAttempts: number;
  accuracy: number;
  phrase: string;
}

export interface CorreioExtraviadoSection {
  name: string;
  playerAccuracyPct: number;
  teamAccuracyPct: number;
  deltaPct: number;
  phrase: string;
}

export interface MuralhaSection {
  name: string;
  saves: number;
  goalsConceded: number;
  archetype: string;
  archetypeTitle: string;
  phrase: string;
}

/**
 * Fetches the latest match card for a club using only Supabase editorial read model.
 * No dependency on Spring Boot API - fully independent.
 * No business logic - pure presentation fetching.
 */
export async function getLatestMatchCard(clubId: string): Promise<MatchCardResponse> {
  const supabase = createServerSupabase();

  const { data, error } = await supabase
    .from("dashboard_editorial_presentations")
    .select("presentation")
    .eq("club_id", clubId)
    .order("played_at", { ascending: false })
    .limit(1)
    .single();

  if (error || !data?.presentation) {
    return {
      status: "no_matches",
      message: "Nenhuma partida processada ainda",
    };
  }

  return {
    status: "success",
    presentation: data.presentation as MatchSummaryPresentation,
  };
}

export async function getRecentMatchCards(
  clubId: string,
  limit: number = 3,
): Promise<MatchSummaryPresentation[]> {
  const supabase = createServerSupabase();

  const { data, error } = await supabase
    .from("dashboard_editorial_presentations")
    .select("presentation")
    .eq("club_id", clubId)
    .order("played_at", { ascending: false })
    .limit(limit);

  if (error || !data) {
    return [];
  }

  return data
    .map((row) => row.presentation as MatchSummaryPresentation | null)
    .filter((p): p is MatchSummaryPresentation => p != null);
}
