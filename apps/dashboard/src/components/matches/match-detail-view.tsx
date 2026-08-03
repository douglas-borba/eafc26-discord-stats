import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDateTime, ratingColor } from "@/lib/utils";
import type { MatchDetail } from "@/lib/domain/types";

const awardMeta: Record<string, { emoji: string; label: string; accent: string }> = {
  craque: { emoji: "⭐", label: "Craque", accent: "border-l-highlight" },
  bagre: { emoji: "📉", label: "Bagre", accent: "border-l-development" },
  xerife: { emoji: "🛡️", label: "Xerife", accent: "border-l-defense" },
};

const storyMeta: Record<string, { emoji: string; accent: string }> = {
  DECISIVE: { emoji: "🎯", accent: "border-l-win" },
  CONSTANT_THREAT: { emoji: "⚡", accent: "border-l-pressure" },
  NEAR_MISS: { emoji: "🍍", accent: "border-l-near-miss" },
  RED_CARD: { emoji: "🟥", accent: "border-l-discipline" },
  PASS_PRECISION: { emoji: "🎯", accent: "border-l-precision" },
  LOST_MAIL: { emoji: "📮", accent: "border-l-loss" },
  GOALKEEPER: { emoji: "🧤", accent: "border-l-defense" },
};

export function MatchDetailView({ match }: { match: MatchDetail }) {
  return (
    <div className="space-y-6">
      {/* Hero */}
      <Panel className="text-center py-8">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-4">
          {match.matchType ?? "Partida"} · {formatDateTime(match.playedAt)}
        </p>
        <div className="flex items-center justify-center gap-6">
          <div className="text-right">
            <p className="text-sm text-text-soft mb-1">{match.ourClubName ?? "Nós"}</p>
            <p className="text-5xl font-black">{match.ourScore}</p>
          </div>
          <div className="flex flex-col items-center gap-1">
            <OutcomeBadge outcome={match.outcome} className="w-8 h-8 text-sm" />
            <span className="text-xs text-muted">×</span>
          </div>
          <div className="text-left">
            <p className="text-sm text-text-soft mb-1">{match.opponentClubName ?? "Adversário"}</p>
            <p className="text-5xl font-black">{match.opponentScore}</p>
          </div>
        </div>
      </Panel>

      {/* Awards */}
      {(match.awards.craque || match.awards.bagre || match.awards.xerife) && (
        <div>
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Personagens</h3>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(["craque", "bagre", "xerife"] as const).map((key) => {
              const award = match.awards[key];
              if (!award) return null;
              const meta = awardMeta[key];
              return (
                <Panel key={key} className={`border-l-[3px] ${meta.accent}`}>
                  <p className="text-lg mb-1">{meta.emoji}</p>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-muted">{meta.label}</p>
                  <p className="text-base font-bold text-text-primary mt-1">{award.winnerName ?? "—"}</p>
                  {award.reason && <p className="text-xs text-muted mt-1">{award.reason}</p>}
                </Panel>
              );
            })}
          </div>
        </div>
      )}

      {/* Stories */}
      {match.stories.length > 0 && (
        <div>
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">História</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {match.stories.map((s, i) => {
              const meta = storyMeta[s.type] ?? { emoji: "📝", accent: "border-l-border" };
              return (
                <Panel key={i} className={`border-l-[3px] ${meta.accent}`}>
                  <p className="text-lg mb-1">{meta.emoji}</p>
                  <p className="text-sm text-text-soft">{s.narrativeKey}</p>
                </Panel>
              );
            })}
          </div>
        </div>
      )}

      {/* Player table */}
      <div>
        <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Jogador por Jogador</h3>
        <Panel className="overflow-x-auto p-0">
          {/* Desktop table */}
          <table className="w-full text-sm hidden sm:table">
            <thead>
              <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wider text-muted">
                <th className="px-4 py-3">Jogador</th>
                <th className="px-3 py-3 text-center">Nota</th>
                <th className="px-3 py-3 text-center">G</th>
                <th className="px-3 py-3 text-center">A</th>
                <th className="px-3 py-3 text-center">Fin.</th>
                <th className="px-3 py-3 text-center">Passes</th>
                <th className="px-3 py-3 text-center">Desarmes</th>
                <th className="px-3 py-3 text-center">CV</th>
              </tr>
            </thead>
            <tbody>
              {match.players.map((p) => (
                <tr key={p.playerId} className="border-b border-border/50 hover:bg-surface-raised/50">
                  <td className="px-4 py-2.5 font-medium">
                    {p.displayName ?? p.platformName ?? p.playerId}
                    {p.manOfTheMatch && <span className="ml-1.5 text-highlight" title="Man of the Match">⭐</span>}
                  </td>
                  <td className={`px-3 py-2.5 text-center font-semibold ${p.rating ? ratingColor(p.rating) : "text-muted"}`}>
                    {p.rating?.toFixed(1) ?? "—"}
                  </td>
                  <td className="px-3 py-2.5 text-center">{p.goals || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.assists || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.shots || "—"}</td>
                  <td className="px-3 py-2.5 text-center">{p.passesCompleted}/{p.passesAttempted}</td>
                  <td className="px-3 py-2.5 text-center">{p.tacklesCompleted}/{p.tacklesAttempted}</td>
                  <td className="px-3 py-2.5 text-center">{p.redCards || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Mobile cards */}
          <div className="sm:hidden divide-y divide-border">
            {match.players.map((p) => (
              <div key={p.playerId} className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-text-primary">
                    {p.displayName ?? p.platformName ?? p.playerId}
                    {p.manOfTheMatch && <span className="ml-1 text-highlight">⭐</span>}
                  </span>
                  <span className={`font-semibold ${p.rating ? ratingColor(p.rating) : "text-muted"}`}>
                    {p.rating?.toFixed(1) ?? "—"}
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-2 text-xs text-center text-muted">
                  <div><span className="block font-semibold text-text-soft">{p.goals}</span>Gols</div>
                  <div><span className="block font-semibold text-text-soft">{p.assists}</span>Assist.</div>
                  <div><span className="block font-semibold text-text-soft">{p.shots}</span>Fin.</div>
                  <div><span className="block font-semibold text-text-soft">{p.passesCompleted}/{p.passesAttempted}</span>Passes</div>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}
