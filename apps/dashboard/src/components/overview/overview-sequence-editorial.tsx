import type { SequenceEditorial } from "@/lib/services/sequence-editorial-service";

interface Props {
  editorial: SequenceEditorial;
}

const outcomeLabels = { wins: "V", draws: "E", losses: "D" } as const;
const outcomeColors = { wins: "#3fb950", draws: "#8b949e", losses: "#f85149" } as const;

export function OverviewSequenceEditorial({ editorial }: Props) {
  const { stats } = editorial;

  return (
    <div className="flex flex-col gap-4">
      {/* Title block */}
      <div>
        <p className="text-[0.62rem] font-bold uppercase tracking-[0.08em] text-[#58a6ff] mb-1.5">
          Panorama
        </p>
        <h2 className="text-[1rem] font-bold text-[#e6edf3] leading-tight">
          {editorial.title}
        </h2>
      </div>

      {/* Narrative */}
      <p className="text-[0.8rem] text-[#9da5b0] leading-[1.6]">
        {editorial.narrative}
      </p>

      {stats.matchCount > 0 && (
        <>
          {/* V/E/D row */}
          <div className="flex items-center gap-4">
            {(["wins", "draws", "losses"] as const).map((key) => (
              <div
                key={key}
                className="flex items-baseline gap-1"
              >
                <span
                  className="text-[1.15rem] font-bold tabular-nums"
                  style={{ color: outcomeColors[key] }}
                >
                  {stats[key]}
                </span>
                <span className="text-[0.62rem] font-semibold uppercase text-[#6e7681]">
                  {outcomeLabels[key]}
                </span>
              </div>
            ))}
          </div>

          {/* Stats */}
          <div className="flex flex-col gap-[0.35rem] text-[0.75rem]">
            <StatRow label="Gols marcados" value={String(stats.goalsScored)} />
            <StatRow label="Gols sofridos" value={String(stats.goalsConceded)} />
            <StatRow
              label="Saldo"
              value={`${stats.goalDifference > 0 ? "+" : ""}${stats.goalDifference}`}
              color={stats.goalDifference > 0 ? "#3fb950" : stats.goalDifference < 0 ? "#f85149" : "#6e7681"}
            />
            <StatRow label="Média de gols" value={stats.avgGoalsScored} />
          </div>

          {/* Top performers */}
          {(editorial.topScorer || editorial.topHighlight) && (
            <div className="flex flex-col gap-1.5 text-[0.75rem] pt-1 border-t border-[#21262d]">
              {editorial.topScorer && (
                <div className="flex items-center gap-1.5">
                  <span className="text-[0.8rem]">⚽</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topScorer.name}</span>
                    {" "}{editorial.topScorer.goals} {editorial.topScorer.goals === 1 ? "gol" : "gols"}
                  </span>
                </div>
              )}
              {editorial.topHighlight && (
                <div className="flex items-center gap-1.5">
                  <span className="text-[0.8rem]">🥇</span>
                  <span className="text-[#9da5b0]">
                    <span className="font-medium text-[#c9d1d9]">{editorial.topHighlight.name}</span>
                    {" "}{editorial.topHighlight.appearances}× destaque
                  </span>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="flex justify-between items-baseline">
      <span className="text-[#6e7681]">{label}</span>
      <span className="font-semibold tabular-nums" style={{ color: color ?? "#c9d1d9" }}>
        {value}
      </span>
    </div>
  );
}
