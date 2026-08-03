import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate } from "@/lib/utils";
import type { MatchSummary } from "@/lib/domain/types";

export function OverviewRecentForm({ matches, clubId }: { matches: MatchSummary[]; clubId: string }) {
  const recent = matches.slice(0, 5);
  const currentStreak = computeStreak(matches);

  return (
    <Panel>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-2">Forma Recente</p>
      <div className="flex gap-1.5 mb-3">
        {recent.map((m) => (
          <OutcomeBadge key={m.matchId} outcome={m.outcome} className="w-7 h-7 text-[10px]" />
        ))}
      </div>
      {currentStreak && (
        <p className="text-xs text-muted">
          Sequência atual: <span className="font-semibold text-text-soft">{currentStreak}</span>
        </p>
      )}
      <div className="mt-3 space-y-1">
        {recent.map((m) => (
          <a
            key={m.matchId}
            href={`/clubs/${clubId}/matches?match=${m.matchId}`}
            className="flex items-center justify-between text-xs py-1 hover:text-accent transition-colors"
          >
            <span className="text-muted">{formatDate(m.playedAt)}</span>
            <span className="text-text-soft">
              {m.ourScore}&times;{m.opponentScore} vs {m.opponentClubName ?? "Adv."}
            </span>
          </a>
        ))}
      </div>
    </Panel>
  );
}

function computeStreak(matches: MatchSummary[]): string | null {
  if (matches.length === 0) return null;
  const first = matches[0].outcome;
  let count = 0;
  for (const m of matches) {
    if (m.outcome !== first) break;
    count++;
  }
  const label = first === "WIN" ? "vitória" : first === "DRAW" ? "empate" : "derrota";
  const plural = count > 1 ? "s" : "";
  return `${count} ${label}${plural}`;
}
