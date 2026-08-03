import { Panel } from "@/components/ui/panel";
import type { Overview } from "@/lib/domain/types";

export function OverviewCards({ overview }: { overview: Overview }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <Panel>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Partidas</p>
        <p className="text-2xl font-bold">{overview.totalMatches}</p>
      </Panel>
      <Panel>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Vitórias</p>
        <p className="text-2xl font-bold text-win">{overview.wins}</p>
        <p className="text-xs text-muted mt-0.5">{overview.winRate}%</p>
      </Panel>
      <Panel>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Gols</p>
        <p className="text-2xl font-bold">{overview.goalsFor}</p>
        <p className="text-xs text-muted mt-0.5">{overview.goalsAgainst} sofridos</p>
      </Panel>
      <Panel>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Saldo</p>
        <p className={`text-2xl font-bold ${overview.goalDifference > 0 ? "text-win" : overview.goalDifference < 0 ? "text-loss" : "text-draw"}`}>
          {overview.goalDifference > 0 ? "+" : ""}{overview.goalDifference}
        </p>
      </Panel>
    </div>
  );
}

export function RecordStrip({ overview }: { overview: Overview }) {
  const total = overview.totalMatches || 1;
  const winPct = (overview.wins / total) * 100;
  const drawPct = (overview.draws / total) * 100;
  const lossPct = (overview.losses / total) * 100;

  return (
    <Panel className="mt-3">
      <div className="flex gap-4 items-center text-sm mb-3">
        <span className="text-win font-semibold">{overview.wins}V</span>
        <span className="text-draw font-semibold">{overview.draws}E</span>
        <span className="text-loss font-semibold">{overview.losses}D</span>
      </div>
      <div className="flex h-2 rounded-full overflow-hidden bg-border">
        <div className="bg-win transition-all" style={{ width: `${winPct}%` }} />
        <div className="bg-draw transition-all" style={{ width: `${drawPct}%` }} />
        <div className="bg-loss transition-all" style={{ width: `${lossPct}%` }} />
      </div>
    </Panel>
  );
}
