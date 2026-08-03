export const dynamic = "force-dynamic";

import { getPlayers } from "@/lib/services/player-service";
import { PlayerList } from "@/components/players/player-list";

export default async function PlayersPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const result = await getPlayers(clubId, 0, 100);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(260px,340px)_minmax(0,1fr)] lg:gap-6">
      <div>
        <h1 className="text-xl font-bold text-text-primary mb-4">Jogadores</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <PlayerList players={result.content} />
        </div>
      </div>
      <div className="hidden lg:flex items-center justify-center">
        <p className="text-muted text-sm">Selecione um jogador para ver o perfil.</p>
      </div>
    </div>
  );
}
