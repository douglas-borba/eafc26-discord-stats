import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate, outcomeColor } from "@/lib/utils";
import type { OpponentHistory } from "@/lib/domain/types";

export function OpponentHistoryView({ history, clubId }: { history: OpponentHistory; clubId: string }) {
  const goalDiff = history.goalsFor - history.goalsAgainst;
  const total = history.matchesPlayed || 1;
  const winRate = Math.round((history.wins / total) * 100);
  const lastMatch = history.matches[0] ?? null;

  return (
    <div className="space-y-6">
      {/* Versus header */}
      <Panel className="text-center py-6">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-2">Histórico do confronto</p>
        <h2 className="text-2xl font-bold text-text-primary">
          {history.clubName ?? history.clubId}
        </h2>
        <p className="text-sm text-muted mt-1">{history.matchesPlayed} confrontos</p>
        <div className="flex justify-center gap-4 mt-3">
          <span className="text-2xl font-black text-win">{history.wins}</span>
          <span className="text-2xl font-black text-draw">{history.draws}</span>
          <span className="text-2xl font-black text-loss">{history.losses}</span>
        </div>
        <div className="flex justify-center gap-4 text-xs text-muted">
          <span>Vitórias</span>
          <span>Empates</span>
          <span>Derrotas</span>
        </div>
      </Panel>

      {/* Summary strip */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Panel>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Gols Pró</p>
          <p className="text-2xl font-bold">{history.goalsFor}</p>
        </Panel>
        <Panel>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Gols Contra</p>
          <p className="text-2xl font-bold">{history.goalsAgainst}</p>
        </Panel>
        <Panel>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Saldo</p>
          <p className={`text-2xl font-bold ${goalDiff > 0 ? "text-win" : goalDiff < 0 ? "text-loss" : "text-draw"}`}>
            {goalDiff > 0 ? "+" : ""}{goalDiff}
          </p>
        </Panel>
        <Panel>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Aproveitamento</p>
          <p className="text-2xl font-bold">{winRate}%</p>
        </Panel>
      </div>

      {/* Last match */}
      {lastMatch && (
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Último confronto</p>
          <h3 className="text-lg font-bold text-text-primary mb-3">Último Confronto Registrado</h3>
          <a href={`/clubs/${clubId}/matches?match=${lastMatch.matchId}`} className="block">
            <Panel className="hover:bg-surface-raised/50 transition-colors">
              <div className="flex items-center gap-4">
                <OutcomeBadge outcome={lastMatch.outcome} className="w-8 h-8 text-sm" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-text-primary">
                    {lastMatch.ourClubName ?? "Nós"} vs {lastMatch.opponentClubName ?? "Adversário"}
                  </p>
                  <p className="text-xs text-muted">{formatDate(lastMatch.playedAt)} &middot; {lastMatch.matchType ?? "Partida"}</p>
                </div>
                <p className={`text-2xl font-black ${outcomeColor(lastMatch.outcome)}`}>
                  {lastMatch.ourScore} &times; {lastMatch.opponentScore}
                </p>
              </div>
            </Panel>
          </a>
        </div>
      )}

      {/* Full history */}
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Confrontos</p>
        <h3 className="text-lg font-bold text-text-primary mb-3">Histórico Completo</h3>
        <div className="space-y-1">
          {history.matches.map((m) => (
            <a
              key={m.matchId}
              href={`/clubs/${clubId}/matches?match=${m.matchId}`}
              className="block"
            >
              <Panel className="flex items-center gap-3 py-3 px-4 hover:bg-surface-raised/50 transition-colors">
                <OutcomeBadge outcome={m.outcome} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-text-primary">{formatDate(m.playedAt)}</p>
                  {m.matchType && <p className="text-xs text-muted">{m.matchType}</p>}
                </div>
                <p className={`text-lg font-black shrink-0 ${outcomeColor(m.outcome)}`}>
                  {m.ourScore} &times; {m.opponentScore}
                </p>
              </Panel>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
