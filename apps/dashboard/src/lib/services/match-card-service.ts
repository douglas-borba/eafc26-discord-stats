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
  completionStatus?: "COMPLETED" | "DNF" | "UNKNOWN";
  dnfClubId?: string | null;
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
  behindThePlay?: BehindThePlaySection | null;
  oneOnOne?: OneOnOneSection | null;
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
  title?: string;
}

export interface XerifeSection {
  name: string;
  tacklesMade: number;
  tackleAttempts: number;
  successRate: number;
  phrase: string;
  interceptions?: number;
}

export interface BehindThePlaySection {
  name: string;
  secondAssists: number;
  throughPasses: number;
  phrase: string;
}

export interface OneOnOneSection {
  name: string;
  beats: number;
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

type CanonicalMatchSummary = {
  matchId: string;
  playedAt: string;
  dateLabel: string;
  competition: string | null;
  ourClub: { id: string; name: string; score: number };
  opponentClub: { id: string; name: string; score: number };
  outcome: { code: "WIN" | "DRAW" | "LOSS"; label: string; icon: string };
  completionStatus?: "COMPLETED" | "DNF" | "UNKNOWN";
  dnfClubId?: string | null;
};

type CanonicalMatchList = { matches: CanonicalMatchSummary[] };

const OUTCOME_META: Record<string, { emoji: string; label: string; color: number }> = {
  WIN: { emoji: "✅", label: "Vitória", color: 0x3fb950 },
  DRAW: { emoji: "🤝", label: "Empate", color: 0xd29922 },
  LOSS: { emoji: "❌", label: "Derrota", color: 0xf85149 },
};

function canonicalToBasicPresentation(m: CanonicalMatchSummary): MatchSummaryPresentation {
  const meta = OUTCOME_META[m.outcome.code] ?? OUTCOME_META.DRAW;
  return {
    ourName: m.ourClub.name,
    oppName: m.opponentClub.name,
    ourScore: m.ourClub.score,
    oppScore: m.opponentClub.score,
    outcome: { emoji: meta.emoji, label: meta.label, color: meta.color, type: m.outcome.code },
    date: m.dateLabel,
    timestamp: m.playedAt,
    matchId: m.matchId,
    completionStatus: m.completionStatus,
    dnfClubId: m.dnfClubId,
    goals: null,
    assists: null,
    highlights: null,
    craque: null,
    offensiveNarratives: [],
    bagre: null,
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
    behindThePlay: null,
    oneOnOne: null,
  };
}

export async function getOverviewCards(
  clubId: string,
  limit: number = 10,
): Promise<MatchSummaryPresentation[]> {
  const { clubPath, fetchSports } = await import("@/lib/api/sports-client");
  const canonical = await fetchSports<CanonicalMatchList>(clubPath(clubId, "/overview/matches"));
  const matches = canonical.matches.slice(0, limit);
  if (matches.length === 0) return [];

  const matchIds = matches.map((m) => m.matchId);
  const supabase = createServerSupabase();
  const { data: editorialRows } = await supabase
    .from("dashboard_editorial_presentations")
    .select("match_id, presentation")
    .eq("club_id", clubId)
    .in("match_id", matchIds);

  const editorialMap = new Map<string, MatchSummaryPresentation>();
  if (editorialRows) {
    for (const row of editorialRows) {
      if (row.presentation) editorialMap.set(row.match_id as string, row.presentation as MatchSummaryPresentation);
    }
  }

  return matches.map((m) => editorialMap.get(m.matchId) ?? canonicalToBasicPresentation(m));
}
