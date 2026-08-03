import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate, ratingColor } from "@/lib/utils";
import type { PlayerProfile } from "@/lib/domain/types";

export function PlayerProfileView({ profile }: { profile: PlayerProfile }) {
  const passRate = profile.totalPassesAttempted > 0
    ? Math.round((profile.totalPassesCompleted / profile.totalPassesAttempted) * 100)
    : 0;
  const tackleRate = profile.totalTacklesAttempted > 0
    ? Math.round((profile.totalTacklesCompleted / profile.totalTacklesAttempted) * 100)
    : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-text-primary">
          {profile.displayName ?? profile.platformName ?? profile.playerId}
        </h2>
        {profile.proName && profile.platformName && profile.proName !== profile.platformName && (
          <p className="text-sm text-muted mt-0.5">{profile.platformName}</p>
        )}
      </div>

      {/* Record badges */}
      <div className="flex gap-2">
        <RecordBadge label="V" value={0} color="bg-win" />
        <RecordBadge label="E" value={0} color="bg-draw" />
        <RecordBadge label="D" value={0} color="bg-loss" />
        <span className="text-sm text-muted self-center ml-2">{profile.matchesPlayed} partidas</span>
      </div>

      {/* Stat grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatItem label="Partidas" value={profile.matchesPlayed} />
        <StatItem label="Média" value={profile.averageRating?.toFixed(2) ?? "—"} />
        <StatItem label="Gols" value={profile.totalGoals} />
        <StatItem label="Assistências" value={profile.totalAssists} />
        <StatItem label="Craques" value={profile.manOfTheMatchCount} emoji="⭐" />
        <StatItem label="Finalizações" value={profile.totalShots} />
        <StatItem label="Passes" value={`${passRate}%`} subtitle={`${profile.totalPassesCompleted}/${profile.totalPassesAttempted}`} />
        <StatItem label="Desarmes" value={`${tackleRate}%`} subtitle={`${profile.totalTacklesCompleted}/${profile.totalTacklesAttempted}`} />
      </div>

      {/* Recent matches */}
      {profile.recentMatches.length > 0 && (
        <div>
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-3">Últimas Partidas</h3>
          <div className="space-y-1">
            {profile.recentMatches.map((m) => (
              <Panel key={m.matchId} className="flex items-center gap-3 py-3 px-4">
                <OutcomeBadge outcome={m.outcome} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-text-primary truncate">
                    vs {m.opponentClubName ?? "Adversário"}
                  </p>
                  <p className="text-xs text-muted">{formatDate(m.playedAt)}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm font-bold">{m.goals}G {m.assists}A</p>
                  {m.rating != null && (
                    <p className={`text-xs font-semibold ${ratingColor(m.rating)}`}>{m.rating.toFixed(1)}</p>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <p className="text-lg font-black">{(m as { ourScore: number }).ourScore}×{(m as { opponentScore: number }).opponentScore}</p>
                </div>
              </Panel>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function RecordBadge({ label, color }: { label: string; value: number; color: string }) {
  return (
    <span className={`inline-flex items-center justify-center w-7 h-7 rounded text-xs font-bold text-white ${color}`}>
      {label}
    </span>
  );
}

function StatItem({ label, value, emoji, subtitle }: { label: string; value: string | number; emoji?: string; subtitle?: string }) {
  return (
    <Panel>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">{label} {emoji}</p>
      <p className="text-xl font-bold text-text-primary">{value}</p>
      {subtitle && <p className="text-xs text-muted mt-0.5">{subtitle}</p>}
    </Panel>
  );
}
