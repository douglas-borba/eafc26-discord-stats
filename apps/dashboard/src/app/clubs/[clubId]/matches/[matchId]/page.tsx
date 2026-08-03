export const dynamic = "force-dynamic";

import { getMatches, getMatch } from "@/lib/services/match-service";
import { MatchList } from "@/components/matches/match-list";
import { MatchDetailView } from "@/components/matches/match-detail-view";
import { notFound } from "next/navigation";

export default async function MatchDetailPage({
  params,
}: {
  params: Promise<{ clubId: string; matchId: string }>;
}) {
  const { clubId, matchId } = await params;
  const [matchResult, detail] = await Promise.all([
    getMatches(clubId, 0, 50),
    getMatch(clubId, matchId),
  ]);

  if (!detail) notFound();

  return (
    <div className="lg:grid lg:grid-cols-[minmax(280px,360px)_minmax(0,1fr)] lg:gap-6">
      <div className="hidden lg:block">
        <h1 className="text-xl font-bold text-text-primary mb-4">Arquivo de Partidas</h1>
        <div className="lg:sticky lg:top-[26px] lg:max-h-[calc(100vh-52px)] lg:overflow-y-auto">
          <MatchList matches={matchResult.content} />
        </div>
      </div>
      <div>
        <MatchDetailView match={detail} />
      </div>
    </div>
  );
}
