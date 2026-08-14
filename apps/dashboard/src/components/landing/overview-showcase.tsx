import { OverviewClubSummary } from "@/components/overview/overview-club-summary";
import { OverviewMatchCard } from "@/components/overview/overview-match-card";
import {
  SHOWCASE_CLUB_NAME,
  SHOWCASE_EDITORIAL,
  SHOWCASE_CARDS,
} from "./landing-showcase-data";

/**
 * Compact club state for the hero — communicates continuous tracking,
 * not individual match depth. No scroll, no match cards.
 */
export function HeroClubSnapshot() {
  const { stats } = SHOWCASE_EDITORIAL;
  if (stats.matchCount === 0) return null;

  const resultDotColors = { WIN: "#3fb950", DRAW: "#d29922", LOSS: "#f85149" } as const;
  const outcomeColors = { wins: "#3fb950", draws: "#8b949e", losses: "#f85149" } as const;

  return (
    <div className="hero-snapshot" aria-label={`Acompanhamento real do clube ${SHOWCASE_CLUB_NAME}`}>
      <div className="hero-snapshot-header">
        <span className="hero-snapshot-club">{SHOWCASE_CLUB_NAME}</span>
        <span className="hero-snapshot-badge">Acompanhamento ativo</span>
      </div>

      <div className="hero-snapshot-body">
        {/* Result dots */}
        <div className="hero-snapshot-form">
          <p className="hero-snapshot-form-label">
            Últimas {SHOWCASE_EDITORIAL.matchDetails.length} partidas
          </p>
          <div className="hero-snapshot-dots">
            {SHOWCASE_EDITORIAL.matchDetails.map((match) => (
              <div
                key={match.matchId}
                className="hero-snapshot-dot"
                style={{ backgroundColor: resultDotColors[match.outcome] }}
                title={`${match.opponent} ${match.ourScore}×${match.oppScore}`}
              />
            ))}
          </div>
          <div className="hero-snapshot-record">
            {(["wins", "draws", "losses"] as const).map((key, i) => (
              <span key={key} className="hero-snapshot-record-item">
                {i > 0 && <span className="hero-snapshot-record-sep">·</span>}
                <span className="hero-snapshot-record-num" style={{ color: outcomeColors[key] }}>
                  {stats[key]}
                </span>
                <span className="hero-snapshot-record-label">
                  {key === "wins" ? "V" : key === "draws" ? "E" : "D"}
                </span>
              </span>
            ))}
          </div>
        </div>

        {/* Momento */}
        <div className="hero-snapshot-momento">
          <span className="hero-snapshot-momento-tag">Momento do Clube</span>
          <span className="hero-snapshot-momento-title">{SHOWCASE_EDITORIAL.title}</span>
        </div>

        {/* Key stats row */}
        <div className="hero-snapshot-stats">
          <div className="hero-snapshot-stat">
            <span className="hero-snapshot-stat-value">{stats.goalsScored}</span>
            <span className="hero-snapshot-stat-label">gols</span>
          </div>
          <div className="hero-snapshot-stat-sep" />
          <div className="hero-snapshot-stat">
            <span
              className="hero-snapshot-stat-value"
              style={{ color: stats.goalDifference > 0 ? "#3fb950" : stats.goalDifference < 0 ? "#f85149" : undefined }}
            >
              {stats.goalDifference > 0 ? "+" : ""}{stats.goalDifference}
            </span>
            <span className="hero-snapshot-stat-label">saldo</span>
          </div>
          <div className="hero-snapshot-stat-sep" />
          <div className="hero-snapshot-stat">
            <span
              className="hero-snapshot-stat-value"
              style={{ color: parseFloat(stats.pointsPercentage) >= 60 ? "#3fb950" : parseFloat(stats.pointsPercentage) >= 40 ? "#d29922" : "#f85149" }}
            >
              {stats.pointsPercentage}%
            </span>
            <span className="hero-snapshot-stat-label">aprov.</span>
          </div>
        </div>

        {/* Top players */}
        <div className="hero-snapshot-players">
          {SHOWCASE_EDITORIAL.topScorer && (
            <div className="hero-snapshot-player">
              <span className="hero-snapshot-player-icon">⚽</span>
              <span className="hero-snapshot-player-name">{SHOWCASE_EDITORIAL.topScorer.name}</span>
              <span className="hero-snapshot-player-stat">{SHOWCASE_EDITORIAL.topScorer.goals} gols</span>
            </div>
          )}
          {SHOWCASE_EDITORIAL.topAssister && (
            <div className="hero-snapshot-player">
              <span className="hero-snapshot-player-icon">🎯</span>
              <span className="hero-snapshot-player-name">{SHOWCASE_EDITORIAL.topAssister.name}</span>
              <span className="hero-snapshot-player-stat">{SHOWCASE_EDITORIAL.topAssister.assists} assist.</span>
            </div>
          )}
          {SHOWCASE_EDITORIAL.topHighlight && (
            <div className="hero-snapshot-player">
              <span className="hero-snapshot-player-icon">🥇</span>
              <span className="hero-snapshot-player-name">{SHOWCASE_EDITORIAL.topHighlight.name}</span>
              <span className="hero-snapshot-player-stat">destaque {SHOWCASE_EDITORIAL.topHighlight.appearances}×</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Full overview proof — the main commercial demonstration.
 * Club summary + real match cards side by side.
 */
export function OverviewShowcase() {
  if (SHOWCASE_CARDS.length === 0) return null;

  return (
    <div className="overview-proof" aria-label={`Dados reais do clube ${SHOWCASE_CLUB_NAME}`}>
      <div className="overview-proof-summary">
        <div className="showcase-summary-header">
          <span className="showcase-club-name">{SHOWCASE_CLUB_NAME}</span>
        </div>
        <OverviewClubSummary
          editorial={SHOWCASE_EDITORIAL}
          clubName={SHOWCASE_CLUB_NAME}
          className="showcase-club-summary"
        />
      </div>
      <div className="overview-proof-cards">
        {SHOWCASE_CARDS.map((p, i) => (
          <div key={p.matchId} className="overview-proof-card-wrap">
            <OverviewMatchCard
              presentation={p}
              variant="compact"
              isLatest={i === 0}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
