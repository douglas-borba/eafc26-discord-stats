export const dynamic = "force-dynamic";

import { getPlayers, getPlayer } from "@/lib/services/player-service";
import { PlayersShell } from "@/components/players/players-shell";
import { getClub } from "@/lib/repositories/overview-repository";
import { UpgradePrompt } from "@/components/club/trial-notice";

export default async function PlayersPage({
  params,
  searchParams,
}: {
  params: Promise<{ clubId: string }>;
  searchParams: Promise<{ player?: string }>;
}) {
  const { clubId } = await params;
  const { player: selectedPlayerId } = await searchParams;
  const club = await getClub(clubId);
  if (club.accessStatus !== "ACTIVE") return <UpgradePrompt />;
  const result = await getPlayers(clubId, 0, 100, undefined, "averageRating", "DESC");

  const effectivePlayerId = selectedPlayerId ?? result.content[0]?.playerId ?? null;
  const profile = effectivePlayerId ? await getPlayer(clubId, effectivePlayerId) : null;

  return (
    <PlayersShell
      players={result.content}
      selectedPlayerId={effectivePlayerId}
      profile={profile}
      clubId={clubId}
      clubName={club.displayName}
      showDetailOnMobile={Boolean(selectedPlayerId)}
    />
  );
}
