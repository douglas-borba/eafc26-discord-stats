export const dynamic = "force-dynamic";

import { getMatches } from "@/lib/services/match-service";
import { MatchList } from "@/components/matches/match-list";

export default async function MatchesPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const result = await getMatches(clubId, 0, 50);

  return (
    <div className="lg:grid lg:grid-cols-[minmax(280px,360px)_minmax(0,1fr)] lg:gap-6">
      <div>
        <h1 className="text-xl font-bold text-text-primary mb-4">Arquivo de Partidas</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <MatchList matches={result.content} />
          {result.content.length === 0 && (
            <p className="text-sm text-muted mt-4 text-center">Nenhuma partida encontrada.</p>
          )}
        </div>
      </div>
      <div className="hidden lg:flex items-center justify-center">
        <p className="text-muted text-sm">Selecione uma partida para ver os detalhes.</p>
      </div>
    </div>
  );
}
