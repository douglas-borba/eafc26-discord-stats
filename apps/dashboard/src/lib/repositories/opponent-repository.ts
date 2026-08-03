import { createServerSupabase } from "@/lib/supabase/server";
import type { OpponentSummary, OpponentHistory, MatchSummary, PagedResult } from "@/lib/domain/types";

export async function listOpponents(
  clubId: string,
  page: number,
  size: number
): Promise<PagedResult<OpponentSummary>> {
  const supabase = createServerSupabase();
  const { data, error } = await supabase
    .from("dashboard_matches")
    .select("opponent_club_id, opponent_club_name, outcome, our_score, opponent_score, played_at")
    .eq("club_id", clubId);

  if (error) throw error;

  const map = new Map<string, OpponentSummary>();
  for (const row of data ?? []) {
    const oid = row.opponent_club_id as string;
    const existing = map.get(oid);
    if (existing) {
      existing.matchesPlayed++;
      if (row.outcome === "WIN") existing.wins++;
      else if (row.outcome === "DRAW") existing.draws++;
      else if (row.outcome === "LOSS") existing.losses++;
      existing.goalsFor += row.our_score ?? 0;
      existing.goalsAgainst += row.opponent_score ?? 0;
      if (row.opponent_club_name) existing.clubName = row.opponent_club_name;
      if (!existing.lastPlayedAt || (row.played_at as string) > existing.lastPlayedAt) {
        existing.lastPlayedAt = row.played_at as string;
      }
    } else {
      map.set(oid, {
        clubId: oid,
        clubName: row.opponent_club_name ?? null,
        matchesPlayed: 1,
        wins: row.outcome === "WIN" ? 1 : 0,
        draws: row.outcome === "DRAW" ? 1 : 0,
        losses: row.outcome === "LOSS" ? 1 : 0,
        goalsFor: row.our_score ?? 0,
        goalsAgainst: row.opponent_score ?? 0,
        lastPlayedAt: row.played_at as string | null,
      });
    }
  }

  const opponents = Array.from(map.values()).sort((a, b) => b.matchesPlayed - a.matchesPlayed || a.clubId.localeCompare(b.clubId));
  const totalElements = opponents.length;
  const start = page * size;
  const content = opponents.slice(start, start + size);

  return {
    content,
    page,
    size,
    totalElements,
    totalPages: size > 0 ? Math.ceil(totalElements / size) : 0,
  };
}

export async function getOpponentHistory(
  clubId: string,
  opponentClubId: string
): Promise<OpponentHistory | null> {
  const supabase = createServerSupabase();
  const { data, error } = await supabase
    .from("dashboard_matches")
    .select("match_id, played_at, club_id, our_club_name, opponent_club_id, opponent_club_name, our_score, opponent_score, outcome, match_type")
    .eq("club_id", clubId)
    .eq("opponent_club_id", opponentClubId)
    .order("played_at", { ascending: false });

  if (error) throw error;
  if (!data || data.length === 0) return null;

  let wins = 0, draws = 0, losses = 0, goalsFor = 0, goalsAgainst = 0;
  let clubName: string | null = null;

  const matches: MatchSummary[] = data.map((row) => {
    if (row.outcome === "WIN") wins++;
    else if (row.outcome === "DRAW") draws++;
    else if (row.outcome === "LOSS") losses++;
    goalsFor += row.our_score ?? 0;
    goalsAgainst += row.opponent_score ?? 0;
    if (row.opponent_club_name) clubName = row.opponent_club_name;

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
  });

  return {
    clubId: opponentClubId,
    clubName,
    matchesPlayed: data.length,
    wins,
    draws,
    losses,
    goalsFor,
    goalsAgainst,
    matches,
  };
}
