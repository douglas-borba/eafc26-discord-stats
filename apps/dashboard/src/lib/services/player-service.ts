import { listPlayers, getPlayerProfile } from "@/lib/repositories/player-repository";
import type { PlayerSummary, PlayerProfile, PagedResult } from "@/lib/domain/types";

export async function getPlayers(
  clubId: string,
  page = 0,
  size = 50,
  name?: string,
  sortBy?: string,
  sortDir?: string
): Promise<PagedResult<PlayerSummary>> {
  const clampedSize = Math.min(Math.max(size, 1), 100);
  const clampedPage = Math.max(page, 0);
  return listPlayers(clubId, clampedPage, clampedSize, name, sortBy, sortDir);
}

export async function getPlayer(
  clubId: string,
  playerId: string
): Promise<PlayerProfile | null> {
  return getPlayerProfile(clubId, playerId);
}
