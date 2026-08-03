export const dynamic = "force-dynamic";

import { getOverview } from "@/lib/repositories/overview-repository";
import { getMatches, getMatch } from "@/lib/services/match-service";
import { getPlayers } from "@/lib/services/player-service";
import { OverviewCards, RecordStrip } from "@/components/overview/overview-cards";
import { OverviewLastMatch } from "@/components/overview/overview-last-match";
import { OverviewRecentForm } from "@/components/overview/overview-recent-form";
import { OverviewTopPlayers } from "@/components/overview/overview-top-players";
import { notFound } from "next/navigation";

export default async function OverviewPage({ params }: { params: Promise<{ clubId: string }> }) {
  const { clubId } = await params;
  const [overview, recentMatches, topPlayers] = await Promise.all([
    getOverview(clubId),
    getMatches(clubId, 0, 10),
    getPlayers(clubId, 0, 5, undefined, "averageRating", "DESC"),
  ]);
  if (!overview) notFound();

  const latestMatchId = recentMatches.content[0]?.matchId;
  const latestDetail = latestMatchId ? await getMatch(clubId, latestMatchId) : null;

  return (
    <div className="lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-6">
      {/* Left column */}
      <div className="space-y-4">
        <div className="mb-2">
          <h1 className="text-xl font-bold text-text-primary">Visão Geral</h1>
          {overview.clubName && <p className="text-sm text-muted mt-0.5">{overview.clubName}</p>}
        </div>

        <OverviewCards overview={overview} />
        <RecordStrip overview={overview} />

        {/* Recent form */}
        {recentMatches.content.length > 0 && (
          <OverviewRecentForm matches={recentMatches.content} clubId={clubId} />
        )}

        {/* Top players */}
        {topPlayers.content.length > 0 && (
          <OverviewTopPlayers players={topPlayers.content} clubId={clubId} />
        )}
      </div>

      {/* Right column: last match card */}
      <div className="mt-6 lg:mt-0 lg:sticky lg:top-[18px] lg:self-start">
        {latestDetail ? (
          <OverviewLastMatch match={latestDetail} clubId={clubId} />
        ) : (
          <div className="flex items-center justify-center h-64 text-muted text-sm">
            Nenhuma partida processada ainda.
          </div>
        )}
      </div>
    </div>
  );
}
