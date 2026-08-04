import { Panel } from "@/components/ui/panel";
import { RatingBar } from "@/components/ui/rating-bar";
import type { PlayerSummary } from "@/lib/domain/types";

export function OverviewTopPlayers({ players, clubId }: { players: PlayerSummary[]; clubId: string }) {
  return (
    <Panel>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Top Jogadores</p>
      <div className="space-y-2">
        {players.map((p) => (
          <a
            key={p.playerId}
            href={`/clubs/${clubId}/players?player=${p.playerId}`}
            className="flex items-center gap-3 py-1 hover:text-accent transition-colors"
          >
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-text-primary truncate">
                {p.displayName ?? p.platformName ?? p.playerId}
                {p.manOfTheMatchCount > 0 && <span className="ml-1 text-highlight text-xs">⭐{p.manOfTheMatchCount}</span>}
              </p>
              <p className="text-xs text-muted">{p.matchesPlayed} jogos &middot; {p.totalGoals}G {p.totalAssists}A</p>
            </div>
            <div className="w-24 shrink-0">
              {p.averageRating != null && <RatingBar rating={p.averageRating} />}
            </div>
          </a>
        ))}
      </div>
    </Panel>
  );
}
