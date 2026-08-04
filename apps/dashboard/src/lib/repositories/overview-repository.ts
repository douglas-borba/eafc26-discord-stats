import { createServerSupabase } from "@/lib/supabase/server";
import type { ClubSummary } from "@/lib/domain/types";

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
