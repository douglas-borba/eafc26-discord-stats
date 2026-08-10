export const dynamic = "force-dynamic";

import { getPlayers, getPlayer } from "@/lib/services/player-service";
import { PlayersShell } from "@/components/players/players-shell";
import { getClub } from "@/lib/repositories/overview-repository";

export default async function PlayersPage({
  params,
  searchParams,
}: {
  params: Promise<{ clubId: string }>;
  searchParams: Promise<{ player?: string }>;
}) {
  const { clubId } = await params;
  const { player: selectedPlayerId } = await searchParams;
  const [club, result] = await Promise.all([getClub(clubId), getPlayers(clubId, 0, 100, undefined, "averageRating", "DESC")]);

  const effectivePlayerId = selectedPlayerId ?? result.content[0]?.playerId ?? null;
  const profile = effectivePlayerId ? await getPlayer(clubId, effectivePlayerId) : null;

  return (
    <PlayersShell
      players={result.content}
      selectedPlayerId={effectivePlayerId}
      profile={profile}
      clubId={clubId}
      clubName={club.displayName}
    />
  );
}
