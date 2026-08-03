import { listMatches, getMatchDetail } from "@/lib/repositories/match-repository";
import type { MatchSummary, MatchDetail, PagedResult } from "@/lib/domain/types";

export async function getMatches(
  clubId: string,
  page = 0,
  size = 20,
  filters?: { opponentClubId?: string; from?: string; to?: string; matchType?: string }
): Promise<PagedResult<MatchSummary>> {
  const clampedSize = Math.min(Math.max(size, 1), 100);
  const clampedPage = Math.max(page, 0);
  return listMatches(clubId, clampedPage, clampedSize, filters);
}

export async function getMatch(clubId: string, matchId: string): Promise<MatchDetail | null> {
  return getMatchDetail(clubId, matchId);
}
