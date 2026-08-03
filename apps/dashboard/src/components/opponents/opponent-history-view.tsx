import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate } from "@/lib/utils";
import type { OpponentHistory } from "@/lib/domain/types";

export function OpponentHistoryView({ history }: { history: OpponentHistory }) {
  const goalDiff = history.goalsFor - history.goalsAgainst;

  return (
    <div className="space-y-6">
      {/* Versus header */}
      <Panel className="text-center py-6">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-2">Retrospecto contra</p>
        <h2 className="text-2xl font-bold text-text-primary">{history.clubName ?? history.clubId}</h2>
        <p className="text-sm text-muted mt-1">{history.matchesPlayed} confrontos</p>
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
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Resultado</p>
          <div className="flex gap-2 mt-1">
            <span className="text-win font-bold">{history.wins}V</span>
            <span className="text-draw font-bold">{history.draws}E</span>
            <span className="text-loss font-bold">{history.losses}D</span>
          </div>
        </Panel>
      </div>

      {/* Match history */}
      <div>
        <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Histórico</h3>
        <div className="space-y-1">
          {history.matches.map((m) => (
            <Panel key={m.matchId} className="flex items-center gap-3 py-3 px-4">
              <OutcomeBadge outcome={m.outcome} />
              <div className="flex-1 min-w-0">
                <p className="text-sm text-text-primary">{formatDate(m.playedAt)}</p>
                {m.matchType && <p className="text-xs text-muted">{m.matchType}</p>}
              </div>
              <p className="text-lg font-black shrink-0">{m.ourScore} × {m.opponentScore}</p>
            </Panel>
          ))}
        </div>
      </div>
    </div>
  );
}
