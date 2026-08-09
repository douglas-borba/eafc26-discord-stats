import { createServerSupabase } from "@/lib/supabase/server";
import type { PlayerSummary, PlayerProfile, PlayerMatch, PagedResult } from "@/lib/domain/types";

export async function listPlayers(
  clubId: string,
  page: number,
  size: number,
  name?: string,
  sortBy?: string,
  sortDir?: string
): Promise<PagedResult<PlayerSummary>> {
  const supabase = createServerSupabase();

  const { data: matchIds, error: matchError } = await supabase
    .from("dashboard_matches")
    .select("match_id")
    .eq("club_id", clubId);
  if (matchError) throw matchError;

  const ids = (matchIds ?? []).map((r) => r.match_id as string);
  if (ids.length === 0) {
    return { content: [], page, size, totalElements: 0, totalPages: 0 };
  }

  let query = supabase
    .from("dashboard_player_stats")
    .select("player_id, platform_name, pro_name, rating, goals, assists, man_of_the_match, red_cards")
    .eq("club_id", clubId)
    .in("match_id", ids);

  if (name) {
    query = query.or(`platform_name.ilike.%${name}%,pro_name.ilike.%${name}%`);
  }

  const { data, error } = await query;
  if (error) throw error;

  const playerMap = new Map<string, PlayerSummary>();
  for (const row of data ?? []) {
    const pid = row.player_id as string;
    const existing = playerMap.get(pid);
    if (existing) {
      existing.matchesPlayed++;
      existing.totalGoals += row.goals ?? 0;
      existing.totalAssists += row.assists ?? 0;
      if (row.man_of_the_match) existing.manOfTheMatchCount++;
      existing.redCardCount += row.red_cards ?? 0;
      if (row.rating != null) {
        const prev = existing.averageRating ?? 0;
        existing.averageRating = Math.round(((prev * (existing.matchesPlayed - 1) + row.rating) / existing.matchesPlayed) * 100) / 100;
      }
      if (row.pro_name) existing.proName = row.pro_name;
      if (row.platform_name) existing.platformName = row.platform_name;
      existing.displayName = existing.proName ?? existing.platformName;
    } else {
      playerMap.set(pid, {
        playerId: pid,
        displayName: row.pro_name ?? row.platform_name ?? null,
        platformName: row.platform_name ?? null,
        proName: row.pro_name ?? null,
        matchesPlayed: 1,
        totalGoals: row.goals ?? 0,
        totalAssists: row.assists ?? 0,
        averageRating: row.rating != null ? Math.round(row.rating * 100) / 100 : null,
        manOfTheMatchCount: row.man_of_the_match ? 1 : 0,
        redCardCount: row.red_cards ?? 0,
      });
    }
  }

  const players = Array.from(playerMap.values());

  const col = sortBy ?? "matchesPlayed";
  const dir = sortDir === "ASC" ? -1 : 1;
  players.sort((a, b) => {
    const av = (a as unknown as Record<string, unknown>)[col] ?? 0;
    const bv = (b as unknown as Record<string, unknown>)[col] ?? 0;
    if (av < bv) return dir;
    if (av > bv) return -dir;
    if (a.matchesPlayed !== b.matchesPlayed) return b.matchesPlayed - a.matchesPlayed;
    const nameA = (a.displayName ?? a.platformName ?? a.playerId).toLocaleLowerCase("pt-BR");
    const nameB = (b.displayName ?? b.platformName ?? b.playerId).toLocaleLowerCase("pt-BR");
    return nameA.localeCompare(nameB, "pt-BR");
  });

  const totalElements = players.length;
  const start = page * size;
  const content = players.slice(start, start + size);

  return {
    content,
    page,
    size,
    totalElements,
    totalPages: size > 0 ? Math.ceil(totalElements / size) : 0,
  };
}

export async function getPlayerProfile(
  clubId: string,
  playerId: string
): Promise<PlayerProfile | null> {
  const supabase = createServerSupabase();

  const { data: matchIds, error: matchError } = await supabase
    .from("dashboard_matches")
    .select("match_id")
    .eq("club_id", clubId);
  if (matchError) throw matchError;

  const ids = (matchIds ?? []).map((r) => r.match_id as string);
  if (ids.length === 0) return null;

  const { data, error } = await supabase
    .from("dashboard_player_stats")
    .select("*")
    .eq("club_id", clubId)
    .eq("player_id", playerId)
    .in("match_id", ids);
  if (error) throw error;
  if (!data || data.length === 0) return null;

  let displayName: string | null = null;
  let platformName: string | null = null;
  let proName: string | null = null;
  let totalGoals = 0, totalAssists = 0, totalShots = 0;
  let totalPassesCompleted = 0, totalPassesAttempted = 0;
  let totalTacklesCompleted = 0, totalTacklesAttempted = 0;
  let momCount = 0, redCardCount = 0;
  let ratingSum = 0, ratingCount = 0;

  for (const row of data) {
    if (row.pro_name) proName = row.pro_name;
    if (row.platform_name) platformName = row.platform_name;
    totalGoals += row.goals ?? 0;
    totalAssists += row.assists ?? 0;
    totalShots += row.shots ?? 0;
    totalPassesCompleted += row.passes_completed ?? 0;
    totalPassesAttempted += row.passes_attempted ?? 0;
    totalTacklesCompleted += row.tackles_completed ?? 0;
    totalTacklesAttempted += row.tackles_attempted ?? 0;
    if (row.man_of_the_match) momCount++;
    redCardCount += row.red_cards ?? 0;
    if (row.rating != null) {
      ratingSum += Number(row.rating);
      ratingCount++;
    }
  }

  displayName = proName ?? platformName;

  const recentMatchIds = data
    .sort((a, b) => (b.played_at as string).localeCompare(a.played_at as string))
    .slice(0, 20)
    .map((r) => r.match_id as string);

  const { data: matchData } = await supabase
    .from("dashboard_matches")
    .select("match_id, played_at, opponent_club_name, our_score, opponent_score, outcome")
    .eq("club_id", clubId)
    .in("match_id", recentMatchIds)
    .order("played_at", { ascending: false });

  const matchMap = new Map<string, Record<string, unknown>>();
  for (const m of matchData ?? []) matchMap.set(m.match_id as string, m);

  const recentMatches: PlayerMatch[] = recentMatchIds.map((mid) => {
    const m = matchMap.get(mid);
    const stat = data.find((d) => d.match_id === mid);
    return {
      matchId: mid,
      playedAt: (m?.played_at as string) ?? "",
      opponentClubName: (m?.opponent_club_name as string | null) ?? null,
      ourScore: (m?.our_score as number) ?? 0,
      opponentScore: (m?.opponent_score as number) ?? 0,
      outcome: (m?.outcome as "WIN" | "DRAW" | "LOSS") ?? "DRAW",
      rating: stat?.rating != null ? Number(stat.rating) : null,
      goals: (stat?.goals as number) ?? 0,
      assists: (stat?.assists as number) ?? 0,
    };
  }).filter((m) => m.playedAt !== "");

  return {
    playerId,
    displayName,
    platformName,
    proName,
    matchesPlayed: data.length,
    totalGoals,
    totalAssists,
    totalShots,
    averageRating: ratingCount > 0 ? Math.round((ratingSum / ratingCount) * 100) / 100 : null,
    manOfTheMatchCount: momCount,
    redCardCount,
    totalPassesCompleted,
    totalPassesAttempted,
    totalTacklesCompleted,
    totalTacklesAttempted,
    recentMatches,
  };
}
