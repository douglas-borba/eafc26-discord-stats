import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDateTime, ratingColor, outcomeColor } from "@/lib/utils";
import type { MatchDetail } from "@/lib/domain/types";

export function OverviewLastMatch({ match, clubId }: { match: MatchDetail; clubId: string }) {
  const topPlayers = [...match.players]
    .filter((p) => p.rating != null)
    .sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0))
    .slice(0, 3);

  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Última Partida</p>
      <a href={`/clubs/${clubId}/matches?match=${match.matchId}`} className="block">
        <Panel className="hover:border-accent/30 transition-colors max-w-[420px] mx-auto">
          {/* Result & score */}
          <div className="text-center pb-4 border-b border-border/50">
            <div className="flex items-center justify-center gap-2 mb-3">
              <OutcomeBadge outcome={match.outcome} className="w-7 h-7 text-xs" />
              <span className={`text-sm font-semibold ${outcomeColor(match.outcome)}`}>
                {match.outcome === "WIN" ? "Vitória" : match.outcome === "DRAW" ? "Empate" : "Derrota"}
              </span>
            </div>
            <div className="flex items-center justify-center gap-4">
              <span className="text-sm text-text-soft">{match.ourClubName ?? "Nós"}</span>
              <span className="text-3xl font-black">
                {match.ourScore} <span className="text-muted font-light">&times;</span> {match.opponentScore}
              </span>
              <span className="text-sm text-text-soft">{match.opponentClubName ?? "Adversário"}</span>
            </div>
            <p className="text-xs text-muted mt-2">{formatDateTime(match.playedAt)}</p>
          </div>

          {/* Goals & assists */}
          {(match.players.some((p) => p.goals > 0) || match.players.some((p) => p.assists > 0)) && (
            <div className="grid grid-cols-2 gap-3 py-4 border-b border-border/50">
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">Gols</p>
                {match.players.filter((p) => p.goals > 0).map((p) => (
                  <p key={p.playerId} className="text-xs text-text-soft">
                    {p.displayName ?? p.platformName} ({p.goals})
                  </p>
                ))}
              </div>
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-1">Assistências</p>
                {match.players.filter((p) => p.assists > 0).map((p) => (
                  <p key={p.playerId} className="text-xs text-text-soft">
                    {p.displayName ?? p.platformName} ({p.assists})
                  </p>
                ))}
              </div>
            </div>
          )}

          {/* Top 3 highlights */}
          {topPlayers.length > 0 && (
            <div className="py-4 border-b border-border/50">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted mb-2">Destaques</p>
              {topPlayers.map((p, i) => (
                <div key={p.playerId} className="flex items-center justify-between py-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted w-4">{["🥇", "🥈", "🥉"][i]}</span>
                    <span className="text-sm text-text-primary">
                      {p.displayName ?? p.platformName}
                      {p.manOfTheMatch && <span className="ml-1 text-highlight">⭐</span>}
                    </span>
                  </div>
                  <span className={`text-sm font-semibold ${ratingColor(p.rating!)}`}>
                    {p.rating!.toFixed(1)}
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* Awards */}
          <div className="pt-4 space-y-2">
            {match.awards.craque && (
              <div className="flex items-center gap-2 text-sm">
                <span>⭐</span>
                <span className="text-muted">Craque:</span>
                <span className="text-text-primary font-medium">{match.awards.craque.winnerName}</span>
              </div>
            )}
            {match.awards.bagre && (
              <div className="flex items-center gap-2 text-sm">
                <span>📉</span>
                <span className="text-muted">Bagre:</span>
                <span className="text-text-primary font-medium">{match.awards.bagre.winnerName}</span>
              </div>
            )}
            {match.awards.xerife && (
              <div className="flex items-center gap-2 text-sm">
                <span>🛡️</span>
                <span className="text-muted">Xerife:</span>
                <span className="text-text-primary font-medium">{match.awards.xerife.winnerName}</span>
              </div>
            )}
            {match.stories.length > 0 && (
              <div className="pt-2 space-y-1">
                {match.stories.slice(0, 3).map((s, i) => (
                  <p key={i} className="text-xs text-muted">
                    {s.narrativeKey}
                  </p>
                ))}
              </div>
            )}
          </div>
        </Panel>
      </a>
    </div>
  );
}
