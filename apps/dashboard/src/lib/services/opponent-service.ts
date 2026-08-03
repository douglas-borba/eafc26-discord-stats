import { listOpponents, getOpponentHistory } from "@/lib/repositories/opponent-repository";
import type { OpponentSummary, OpponentHistory, PagedResult } from "@/lib/domain/types";

export async function getOpponents(
  clubId: string,
  page = 0,
  size = 50
): Promise<PagedResult<OpponentSummary>> {
  const clampedSize = Math.min(Math.max(size, 1), 100);
  const clampedPage = Math.max(page, 0);
  return listOpponents(clubId, clampedPage, clampedSize);
}

export async function getOpponent(
  clubId: string,
  opponentClubId: string
): Promise<OpponentHistory | null> {
  return getOpponentHistory(clubId, opponentClubId);
}
