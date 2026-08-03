import { createServerSupabase } from "@/lib/supabase/server";
import type { Overview, ClubSummary } from "@/lib/domain/types";

export async function listClubs(): Promise<ClubSummary[]> {
  const supabase = createServerSupabase();
  const { data, error } = await supabase
    .from("dashboard_matches")
    .select("club_id, our_club_name")
    .order("club_id");

  if (error) throw error;

  const clubMap = new Map<string, { clubId: string; clubName: string | null; count: number }>();
  for (const row of data ?? []) {
    const existing = clubMap.get(row.club_id);
    if (existing) {
      existing.count++;
      if (row.our_club_name) existing.clubName = row.our_club_name;
    } else {
      clubMap.set(row.club_id, {
        clubId: row.club_id,
        clubName: row.our_club_name,
        count: 1,
      });
    }
  }

  return Array.from(clubMap.values()).map((c) => ({
    clubId: c.clubId,
    clubName: c.clubName,
    totalMatches: c.count,
  }));
}

export async function getOverview(clubId: string): Promise<Overview | null> {
  const supabase = createServerSupabase();
  const { data, error } = await supabase
    .from("dashboard_matches")
    .select("outcome, our_score, opponent_score, our_club_name, played_at")
    .eq("club_id", clubId);

  if (error) throw error;
  if (!data || data.length === 0) return null;

  let wins = 0, draws = 0, losses = 0, goalsFor = 0, goalsAgainst = 0;
  let clubName: string | null = null;
  let lastMatchAt: string | null = null;

  for (const row of data) {
    if (row.outcome === "WIN") wins++;
    else if (row.outcome === "DRAW") draws++;
    else if (row.outcome === "LOSS") losses++;
    goalsFor += row.our_score ?? 0;
    goalsAgainst += row.opponent_score ?? 0;
    if (row.our_club_name) clubName = row.our_club_name;
    if (!lastMatchAt || row.played_at > lastMatchAt) lastMatchAt = row.played_at;
  }

  const total = data.length;
  const winRate = total > 0 ? Math.round((wins / total) * 10000) / 100 : 0;

  return {
    clubId,
    clubName,
    totalMatches: total,
    wins,
    draws,
    losses,
    goalsFor,
    goalsAgainst,
    goalDifference: goalsFor - goalsAgainst,
    winRate,
    lastMatchAt,
  };
}
