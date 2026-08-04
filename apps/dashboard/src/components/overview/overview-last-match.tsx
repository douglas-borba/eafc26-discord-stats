import { formatDateTime, ratingColor } from "@/lib/utils";
import type { MatchDetail } from "@/lib/domain/types";

const badgeStyle = {
  WIN: "bg-[rgba(63,185,80,0.25)] text-[#3fb950]",
  DRAW: "bg-[rgba(139,148,158,0.25)] text-[#8b949e]",
  LOSS: "bg-[rgba(248,81,73,0.25)] text-[#f85149]",
} as const;

const badgeLabel = { WIN: "VITÓRIA", DRAW: "EMPATE", LOSS: "DERROTA" } as const;

export function OverviewLastMatch({ match, clubId }: { match: MatchDetail; clubId: string }) {
  const topPlayers = [...match.players]
    .filter((p) => p.rating != null)
    .sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0))
    .slice(0, 3);

  const goalScorers = match.players.filter((p) => p.goals > 0);
  const assistMakers = match.players.filter((p) => p.assists > 0);

  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Última Partida</p>
      <a href={`/clubs/${clubId}/matches?match=${match.matchId}`} className="block">
        <div className="max-w-[420px] mx-auto rounded-xl border border-border overflow-hidden bg-gradient-to-br from-[#1a1f26] via-[#161b22] to-[#12161c] hover:border-accent/30 transition-colors">
          {/* Card header — gradient bg, result badge, scoreline, date */}
          <div className="px-5 py-4 text-center" style={{ background: "linear-gradient(135deg, rgba(88,166,255,.08), transparent)" }}>
            <span className={`inline-block px-3 py-0.5 rounded-md text-[0.7rem] font-bold uppercase tracking-wide mb-2 ${badgeStyle[match.outcome]}`}>
              {badgeLabel[match.outcome]}
            </span>
            <div className="flex items-center justify-center gap-3 my-1">
              <span className="text-[0.85rem] font-medium text-text-primary max-w-[110px] text-center leading-tight">
                {match.ourClubName ?? "Nós"}
              </span>
              <div className="flex items-center gap-1 text-[1.75rem] font-[800] text-white">
                <span>{match.ourScore}</span>
                <span className="text-muted text-lg font-normal">&times;</span>
                <span>{match.opponentScore}</span>
              </div>
              <span className="text-[0.85rem] font-medium text-text-primary max-w-[110px] text-center leading-tight">
                {match.opponentClubName ?? "Adversário"}
              </span>
            </div>
            <p className="text-[0.7rem] text-muted mt-1">{formatDateTime(match.playedAt)}</p>
          </div>

          {/* Card body */}
          <div className="px-5 pb-4">
            {/* Goals & assists */}
            {(goalScorers.length > 0 || assistMakers.length > 0) && (
              <>
                <div className="h-px my-3" style={{ background: "linear-gradient(90deg, transparent, var(--border) 20%, var(--border) 80%, transparent)" }} />
                <div className="grid grid-cols-[1fr_auto_1fr] gap-0">
                  <div className="pr-3">
                    <p className="text-[0.65rem] font-bold uppercase tracking-wider text-muted mb-1">Gols</p>
                    <div className="flex flex-col gap-0.5">
                      {goalScorers.map((p) => (
                        <p key={p.playerId} className="text-[0.82rem]">
                          <span className="text-text-primary font-medium">{p.displayName ?? p.platformName}</span>
                          <span className="text-muted text-[0.75rem] ml-1">({p.goals})</span>
                        </p>
                      ))}
                    </div>
                  </div>
                  <div className="w-px" style={{ background: "linear-gradient(180deg, transparent 0%, var(--border) 20%, var(--border) 80%, transparent 100%)" }} />
                  <div className="pl-3">
                    <p className="text-[0.65rem] font-bold uppercase tracking-wider text-muted mb-1">Assistências</p>
                    <div className="flex flex-col gap-0.5">
                      {assistMakers.map((p) => (
                        <p key={p.playerId} className="text-[0.82rem]">
                          <span className="text-text-primary font-medium">{p.displayName ?? p.platformName}</span>
                          <span className="text-muted text-[0.75rem] ml-1">({p.assists})</span>
                        </p>
                      ))}
                    </div>
                  </div>
                </div>
              </>
            )}

            {/* Top 3 highlights */}
            {topPlayers.length > 0 && (
              <>
                <div className="h-px my-3" style={{ background: "linear-gradient(90deg, transparent, var(--border) 20%, var(--border) 80%, transparent)" }} />
                <div>
                  <p className="text-[0.65rem] font-bold uppercase tracking-wider text-muted mb-1">Destaques</p>
                  <div className="flex flex-col gap-1">
                    {topPlayers.map((p, i) => (
                      <div key={p.playerId} className="flex items-center gap-2 text-[0.85rem]">
                        <span className="w-5 text-center text-[0.9rem] shrink-0">{["\u{1F947}", "\u{1F948}", "\u{1F949}"][i]}</span>
                        <span className="text-text-primary font-medium">
                          {p.displayName ?? p.platformName}
                          {p.manOfTheMatch && <span className="ml-1 text-highlight">{"⭐"}</span>}
                        </span>
                        <span className={`ml-auto font-semibold ${ratingColor(p.rating!)}`}>
                          {p.rating!.toFixed(1)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            {/* Awards */}
            {(match.awards.craque || match.awards.bagre || match.awards.xerife) && (
              <>
                <div className="h-px my-3" style={{ background: "linear-gradient(90deg, transparent, var(--border) 20%, var(--border) 80%, transparent)" }} />
                <div className="space-y-1">
                  {match.awards.craque && (
                    <div className="flex items-center gap-2 text-[0.85rem]">
                      <span>{"⭐"}</span>
                      <span className="text-muted">Craque:</span>
                      <span className="text-text-primary font-medium">{match.awards.craque.winnerName}</span>
                    </div>
                  )}
                  {match.awards.bagre && (
                    <div className="flex items-center gap-2 text-[0.85rem]">
                      <span>{"📉"}</span>
                      <span className="text-muted">Bagre:</span>
                      <span className="text-text-primary font-medium">{match.awards.bagre.winnerName}</span>
                    </div>
                  )}
                  {match.awards.xerife && (
                    <div className="flex items-center gap-2 text-[0.85rem]">
                      <span>{"🛡️"}</span>
                      <span className="text-muted">Xerife:</span>
                      <span className="text-text-primary font-medium">{match.awards.xerife.winnerName}</span>
                    </div>
                  )}
                </div>
              </>
            )}

            {/* Stories */}
            {match.stories.length > 0 && (
              <>
                <div className="h-px my-3" style={{ background: "linear-gradient(90deg, transparent, var(--border) 20%, var(--border) 80%, transparent)" }} />
                <div className="space-y-1">
                  {match.stories.slice(0, 3).map((s, i) => (
                    <p key={i} className="text-xs text-muted">{s.narrativeKey}</p>
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Card footer */}
          <div className="px-3 py-2 text-center text-[0.7rem] text-muted bg-black/25 border-t border-border">
            EA FC 26
          </div>
        </div>
      </a>
    </div>
  );
}
