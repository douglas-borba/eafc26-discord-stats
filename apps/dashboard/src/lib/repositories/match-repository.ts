import { createServerSupabase } from "@/lib/supabase/server";
import type { MatchSummary, MatchDetail, MatchPlayer, MatchAwards, Story, PagedResult } from "@/lib/domain/types";

interface MatchFilters {
  opponentClubId?: string;
  from?: string;
  to?: string;
  matchType?: string;
}

export async function listMatches(
  clubId: string,
  page: number,
  size: number,
  filters?: MatchFilters
): Promise<PagedResult<MatchSummary>> {
  const supabase = createServerSupabase();
  const from = page * size;
  const to = from + size - 1;

  let query = supabase
    .from("dashboard_matches")
    .select(
      "match_id, played_at, club_id, our_club_name, opponent_club_id, opponent_club_name, our_score, opponent_score, outcome, match_type",
      { count: "exact" }
    )
    .eq("club_id", clubId)
    .order("played_at", { ascending: false })
    .order("match_id", { ascending: true })
    .range(from, to);

  if (filters?.opponentClubId) query = query.eq("opponent_club_id", filters.opponentClubId);
  if (filters?.from) query = query.gte("played_at", filters.from);
  if (filters?.to) query = query.lte("played_at", filters.to);
  if (filters?.matchType) query = query.eq("match_type", filters.matchType);

  const { data, count, error } = await query;
  if (error) throw error;

  const totalElements = count ?? 0;
  const content: MatchSummary[] = (data ?? []).map(toMatchSummary);

  return {
    content,
    page,
    size,
    totalElements,
    totalPages: size > 0 ? Math.ceil(totalElements / size) : 0,
  };
}

export async function getMatchDetail(clubId: string, matchId: string): Promise<MatchDetail | null> {
  const supabase = createServerSupabase();
  const { data, error } = await supabase
    .from("dashboard_match_detail")
    .select("payload")
    .eq("match_id", matchId)
    .eq("club_id", clubId)
    .single();

  if (error) {
    if (error.code === "PGRST116") return null;
    throw error;
  }

  return parseCanonicalPayload(data.payload, matchId);
}

function toMatchSummary(row: Record<string, unknown>): MatchSummary {
  return {
    matchId: row.match_id as string,
    playedAt: row.played_at as string,
    ourClubId: row.club_id as string,
    ourClubName: row.our_club_name as string | null,
    opponentClubId: row.opponent_club_id as string | null,
    opponentClubName: row.opponent_club_name as string | null,
    ourScore: row.our_score as number,
    opponentScore: row.opponent_score as number,
    outcome: row.outcome as "WIN" | "DRAW" | "LOSS",
    matchType: row.match_type as string | null,
  };
}

/* eslint-disable @typescript-eslint/no-explicit-any */
function parseCanonicalPayload(payload: any, matchId: string): MatchDetail {
  const interp = payload.interpretation;
  const result = interp.result;
  const perspectiveClubId = interp.perspectiveClubId;
  const participants = payload.footballMatch?.participants ?? [];

  const ourParticipant = participants.find((p: any) => p.club?.id === perspectiveClubId);
  const opponentParticipant = participants.find((p: any) => p.club?.id !== perspectiveClubId);

  const players: MatchPlayer[] = (ourParticipant?.players ?? []).map((p: any) => ({
    playerId: p.player?.id ?? "",
    displayName: p.player?.proName ?? p.player?.platformName ?? null,
    platformName: p.player?.platformName ?? null,
    proName: p.player?.proName ?? null,
    rating: p.rating ?? null,
    goals: p.attacking?.goals ?? 0,
    assists: p.attacking?.assists ?? 0,
    shots: p.attacking?.shots ?? 0,
    passesCompleted: p.passing?.completed ?? 0,
    passesAttempted: p.passing?.attempted ?? 0,
    tacklesCompleted: p.defending?.tacklesCompleted ?? 0,
    tacklesAttempted: p.defending?.tacklesAttempted ?? 0,
    redCards: p.discipline?.redCards ?? 0,
    manOfTheMatch: p.eaRecognition?.manOfTheMatch ?? false,
  }));

  const resolvePlayerName = (id: string | null): string | null => {
    if (!id) return null;
    const p = (ourParticipant?.players ?? []).find((pl: any) => pl.player?.id === id);
    return p?.player?.proName ?? p?.player?.platformName ?? null;
  };

  const mapAward = (decision: any): { winnerId: string | null; winnerName: string | null; reason: string | null } | null => {
    if (!decision?.winnerId) return null;
    return {
      winnerId: decision.winnerId,
      winnerName: resolvePlayerName(decision.winnerId),
      reason: decision.reason ?? null,
    };
  };

  const awards: MatchAwards = {
    craque: mapAward(interp.awards?.craque),
    bagre: mapAward(interp.awards?.bagre),
    xerife: mapAward(interp.awards?.xerife),
  };

  const stories: Story[] = (payload.stories?.stories ?? []).map((s: any) => ({
    type: s.type ?? "",
    priority: s.priority ?? "",
    narrativeKey: s.narrativeKey ?? "",
    content: s.content ?? {},
    involvedPlayers: (s.involvedPlayers ?? []).map((id: string) => id),
  }));

  return {
    matchId,
    playedAt: payload.footballMatch?.playedAt ?? "",
    ourClubId: perspectiveClubId ?? "",
    ourClubName: ourParticipant?.club?.name ?? null,
    opponentClubId: result?.opponentClub ?? null,
    opponentClubName: opponentParticipant?.club?.name ?? null,
    ourScore: result?.ourScore ?? 0,
    opponentScore: result?.opponentScore ?? 0,
    outcome: result?.outcome ?? "DRAW",
    matchType: payload.footballMatch?.competition?.name ?? null,
    players,
    awards,
    stories,
  };
}
/* eslint-enable @typescript-eslint/no-explicit-any */
