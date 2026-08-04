/**
 * Overview Latest Match Panel
 *
 * Small summary panel showing the most recent match result.
 * Replicates the Thymeleaf "Última Partida" panel exactly.
 */

import { Panel } from "@/components/ui/panel";
import type { MatchSummaryPresentation } from "@/lib/services/match-card-service";

interface Props {
  presentation: MatchSummaryPresentation | null | undefined;
}

const resultBadgeStyles = {
  WIN: {
    background: "#238636",
    color: "#fff",
  },
  DRAW: {
    background: "#6e7681",
    color: "#fff",
  },
  LOSS: {
    background: "#da3633",
    color: "#fff",
  },
};

export function OverviewLatestMatch({ presentation }: Props) {
  if (!presentation) {
    return (
      <Panel>
        <div className="px-[0.85rem] py-[0.55rem] bg-black/20 border-b border-[#21262d]">
          <h3 className="text-[0.62rem] font-bold uppercase tracking-[0.05em] text-accent">
            Última Partida
          </h3>
        </div>
        <div className="px-[0.85rem] py-[0.65rem]">
          <div className="text-center py-[0.85rem] text-muted text-[0.78rem]">
            <div className="text-[1.3rem] mb-1 opacity-60">⚽</div>
            <div>Carregando...</div>
          </div>
        </div>
      </Panel>
    );
  }

  const badgeStyle = resultBadgeStyles[presentation.outcome.type];

  return (
    <Panel className="match-panel">
      <div className="px-[0.85rem] py-[0.55rem] bg-black/20 border-b border-[#21262d]">
        <h3 className="text-[0.62rem] font-bold uppercase tracking-[0.05em] text-accent">
          Última Partida
        </h3>
      </div>
      <div className="px-[0.85rem] py-[0.65rem]">
        {/* Match Score Row */}
        <div className="flex items-center justify-center gap-2 mb-[0.4rem]">
          <div className="text-[0.75rem] font-semibold text-[#c9d1d9] max-w-[95px] text-center leading-[1.15]">
            {presentation.ourName}
          </div>
          <div className="text-[1.2rem] font-[800] text-white flex items-center gap-[0.2rem]">
            <span>{presentation.ourScore}</span>
            <span className="text-[0.85rem] text-[#6e7681]">×</span>
            <span>{presentation.oppScore}</span>
          </div>
          <div className="text-[0.75rem] font-semibold text-[#c9d1d9] max-w-[95px] text-center leading-[1.15]">
            {presentation.oppName}
          </div>
        </div>

        {/* Match Info */}
        <div className="flex items-center justify-center gap-[0.4rem] flex-wrap text-[0.68rem] text-muted">
          <span
            className="inline-block rounded-lg text-[0.55rem] font-bold uppercase tracking-[0.03em]"
            style={{
              padding: "0.1rem 0.4rem",
              ...badgeStyle,
            }}
          >
            {presentation.outcome.label}
          </span>
          <span>{presentation.date}</span>
        </div>

        {/* Divider */}
        <div
          className="h-px my-2"
          style={{
            background: "linear-gradient(90deg, transparent, #30363d, transparent)",
          }}
        />

        {/* Note: Monitoring status and publication status removed - requires backend API */}
        <div className="text-[0.68rem] text-muted text-center">
          Partida processada
        </div>
      </div>
    </Panel>
  );
}
