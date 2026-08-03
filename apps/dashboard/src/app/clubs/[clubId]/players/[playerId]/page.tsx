export const dynamic = "force-dynamic";

import { getPlayers, getPlayer } from "@/lib/services/player-service";
import { PlayerList } from "@/components/players/player-list";
import { PlayerProfileView } from "@/components/players/player-profile-view";
import { notFound } from "next/navigation";

export default async function PlayerProfilePage({
  params,
}: {
  params: Promise<{ clubId: string; playerId: string }>;
}) {
  const { clubId, playerId } = await params;
  const [playersResult, profile] = await Promise.all([
    getPlayers(clubId, 0, 100),
    getPlayer(clubId, playerId),
  ]);

  if (!profile) notFound();

  return (
    <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] lg:gap-6">
      <div className="hidden lg:block">
        <h1 className="text-xl font-bold text-text-primary mb-4">Jogadores</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <PlayerList players={playersResult.content} />
        </div>
      </div>
      <div>
        <PlayerProfileView profile={profile} />
      </div>
    </div>
  );
}
