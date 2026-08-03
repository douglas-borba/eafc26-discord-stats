import { Panel } from "@/components/ui/panel";
import { OutcomeBadge } from "@/components/ui/outcome-badge";
import { formatDate, ratingColor, outcomeColor } from "@/lib/utils";
import type { PlayerProfile } from "@/lib/domain/types";

export function PlayerProfileView({ profile, clubId }: { profile: PlayerProfile; clubId: string }) {
  const passRate = profile.totalPassesAttempted > 0
    ? Math.round((profile.totalPassesCompleted / profile.totalPassesAttempted) * 100)
    : 0;
  const tackleRate = profile.totalTacklesAttempted > 0
    ? Math.round((profile.totalTacklesCompleted / profile.totalTacklesAttempted) * 100)
    : 0;

  const wins = profile.recentMatches.filter((m) => m.outcome === "WIN").length;
  const draws = profile.recentMatches.filter((m) => m.outcome === "DRAW").length;
  const losses = profile.recentMatches.filter((m) => m.outcome === "LOSS").length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Perfil histórico</p>
        <h2 className="text-[28px] font-bold text-text-primary leading-tight">
          {profile.displayName ?? profile.platformName ?? profile.playerId}
        </h2>
        {profile.proName && profile.platformName && profile.proName !== profile.platformName && (
          <p className="text-sm text-muted mt-0.5">{profile.platformName}</p>
        )}
      </div>

      {/* Record badges */}
      <div className="flex items-center gap-2">
        <RecordBadge label="V" value={wins} color="bg-win" />
        <RecordBadge label="E" value={draws} color="bg-draw" />
        <RecordBadge label="D" value={losses} color="bg-loss" />
        <span className="text-sm text-muted ml-2">{profile.matchesPlayed} partidas avaliadas</span>
      </div>

      {/* Stat grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatItem label="Partidas" value={profile.matchesPlayed} />
        <StatItem label="Média" value={profile.averageRating?.toFixed(2) ?? "—"} highlight={profile.averageRating != null ? ratingColor(profile.averageRating) : undefined} />
        <StatItem label="Gols" value={profile.totalGoals} />
        <StatItem label="Assistências" value={profile.totalAssists} />
        <StatItem label="Craques" value={profile.manOfTheMatchCount} emoji="⭐" />
        <StatItem label="Bagres" value={profile.redCardCount > 0 ? "—" : "—"} />
        <StatItem label="Xerifes" value="—" />
        <StatItem label="Vermelhos" value={profile.redCardCount} />
        <StatItem label="Finalizações" value={profile.totalShots} />
        <StatItem label="Passes" value={`${passRate}%`} subtitle={`${profile.totalPassesCompleted}/${profile.totalPassesAttempted}`} />
        <StatItem label="Desarmes" value={`${tackleRate}%`} subtitle={`${profile.totalTacklesCompleted}/${profile.totalTacklesAttempted}`} />
      </div>

      {/* Recent matches */}
      {profile.recentMatches.length > 0 && (
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">Últimas Partidas</p>
          <h3 className="text-lg font-bold text-text-primary mb-3">Desempenho Recente</h3>
          <div className="space-y-1">
            {profile.recentMatches.map((m) => (
              <a
                key={m.matchId}
                href={`/clubs/${clubId}/matches?match=${m.matchId}`}
                className="block"
              >
                <Panel className="flex items-center gap-3 py-3 px-4 hover:bg-surface-raised/50 transition-colors">
                  <OutcomeBadge outcome={m.outcome} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-text-primary truncate">
                      vs {m.opponentClubName ?? "Adversário"}
                    </p>
                    <p className="text-xs text-muted">{formatDate(m.playedAt)}</p>
                  </div>
                  <div className="text-right shrink-0 space-y-0.5">
                    <p className="text-xs">
                      <span className="font-semibold">{m.goals}G</span>{" "}
                      <span className="font-semibold">{m.assists}A</span>
                    </p>
                    {m.rating != null && (
                      <p className={`text-xs font-semibold ${ratingColor(m.rating)}`}>{m.rating.toFixed(1)}</p>
                    )}
                  </div>
                  <div className="text-right shrink-0">
                    <p className={`text-lg font-black ${outcomeColor(m.outcome)}`}>
                      {m.ourScore}&times;{m.opponentScore}
                    </p>
                  </div>
                </Panel>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function RecordBadge({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="flex items-center gap-1">
      <span className={`inline-flex items-center justify-center w-7 h-7 rounded text-xs font-bold text-white ${color}`}>
        {label}
      </span>
      <span className="text-sm font-semibold text-text-soft">{value}</span>
    </div>
  );
}

function StatItem({ label, value, emoji, subtitle, highlight }: {
  label: string;
  value: string | number;
  emoji?: string;
  subtitle?: string;
  highlight?: string;
}) {
  return (
    <Panel>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted mb-1">
        {label} {emoji}
      </p>
      <p className={`text-xl font-bold ${highlight ?? "text-text-primary"}`}>{value}</p>
      {subtitle && <p className="text-xs text-muted mt-0.5">{subtitle}</p>}
    </Panel>
  );
}
