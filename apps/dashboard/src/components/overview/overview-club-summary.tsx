import type { SequenceEditorial } from "@/lib/services/sequence-editorial-service";

interface Props {
  editorial: SequenceEditorial;
  clubName: string;
  className?: string;
}

const outcomeLabels = { wins: "V", draws: "E", losses: "D" } as const;
const outcomeColors = { wins: "#3fb950", draws: "#8b949e", losses: "#f85149" } as const;
const resultDotColors = { WIN: "#3fb950", DRAW: "#d29922", LOSS: "#f85149" } as const;

export function OverviewClubSummary({ editorial, clubName, className }: Props) {
  const { stats } = editorial;

  if (stats.matchCount === 0) return null;

  return (
    <div className={className}>
      {/* Recent form dots */}
      <div className="club-summary-form">
        <p className="club-summary-label">
          Últimas {editorial.matchDetails.length} partida{editorial.matchDetails.length !== 1 ? "s" : ""}
        </p>
        <div className="club-summary-dots">
          {editorial.matchDetails.map((match) => (
            <div
              key={match.matchId}
              className="club-summary-dot"
              style={{ backgroundColor: resultDotColors[match.outcome] }}
              title={`${match.opponent} ${match.ourScore}×${match.oppScore}`}
            />
          ))}
        </div>
        <div className="club-summary-record">
          {(["wins", "draws", "losses"] as const).map((key, i) => (
            <span key={key} className="club-summary-record-item">
              {i > 0 && <span className="club-summary-record-sep">·</span>}
              <span className="club-summary-record-num" style={{ color: outcomeColors[key] }}>
                {stats[key]}
              </span>
              <span className="club-summary-record-label">{outcomeLabels[key]}</span>
            </span>
          ))}
        </div>
      </div>

      {/* Momento do Clube */}
      <div className="club-summary-moment">
        <p className="club-summary-moment-tag">Momento do Clube</p>
        <h3 className="club-summary-moment-title">{editorial.title}</h3>
        <p className="club-summary-moment-narrative">
          {editorial.aiNarrative ?? editorial.narrative}
        </p>
      </div>

      {/* Stats */}
      <div className="club-summary-stats">
        <SummaryStatRow label="Gols marcados" value={String(stats.goalsScored)} />
        <SummaryStatRow label="Gols sofridos" value={String(stats.goalsConceded)} />
        <SummaryStatRow
          label="Saldo"
          value={`${stats.goalDifference > 0 ? "+" : ""}${stats.goalDifference}`}
          color={stats.goalDifference > 0 ? "#3fb950" : stats.goalDifference < 0 ? "#f85149" : undefined}
        />
        <SummaryStatRow label="Média de gols" value={stats.avgGoalsScored} />
        <SummaryStatRow
          label="Aproveitamento"
          value={`${stats.pointsPercentage}%`}
          color={parseFloat(stats.pointsPercentage) >= 60 ? "#3fb950" : parseFloat(stats.pointsPercentage) >= 40 ? "#d29922" : "#f85149"}
        />
        {editorial.currentStreak && (
          <SummaryStatRow label="Sequência atual" value={editorial.currentStreak.label} />
        )}
      </div>

      {/* Destaques */}
      {(editorial.topScorer || editorial.topAssister || editorial.topHighlight || editorial.topRatedPlayer) && (
        <div className="club-summary-highlights">
          {editorial.topScorer && (
            <div className="club-summary-highlight-row">
              <span className="club-summary-highlight-icon">⚽</span>
              <span className="club-summary-highlight-text">
                <span className="club-summary-highlight-name">{editorial.topScorer.name}</span>
                <span className="club-summary-highlight-sep"> — </span>
                {editorial.topScorer.goals} {editorial.topScorer.goals === 1 ? "gol" : "gols"}
              </span>
            </div>
          )}
          {editorial.topAssister && (
            <div className="club-summary-highlight-row">
              <span className="club-summary-highlight-icon">🎯</span>
              <span className="club-summary-highlight-text">
                <span className="club-summary-highlight-name">{editorial.topAssister.name}</span>
                <span className="club-summary-highlight-sep"> — </span>
                {editorial.topAssister.assists} {editorial.topAssister.assists === 1 ? "assistência" : "assistências"}
              </span>
            </div>
          )}
          {editorial.topHighlight && (
            <div className="club-summary-highlight-row">
              <span className="club-summary-highlight-icon">🥇</span>
              <span className="club-summary-highlight-text">
                <span className="club-summary-highlight-name">{editorial.topHighlight.name}</span>
                <span className="club-summary-highlight-sep"> — </span>
                destaque {editorial.topHighlight.appearances}×
              </span>
            </div>
          )}
          {editorial.topRatedPlayer && (
            <div className="club-summary-highlight-row">
              <span className="club-summary-highlight-icon">⭐</span>
              <span className="club-summary-highlight-text">
                <span className="club-summary-highlight-name">{editorial.topRatedPlayer.name}</span>
                <span className="club-summary-highlight-sep"> — </span>
                média {editorial.topRatedPlayer.avgRating}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function SummaryStatRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="club-summary-stat-row">
      <span className="club-summary-stat-label">{label}</span>
      <span className="club-summary-stat-value" style={color ? { color } : undefined}>{value}</span>
    </div>
  );
}
