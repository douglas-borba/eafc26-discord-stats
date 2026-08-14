import { OverviewClubSummary } from "@/components/overview/overview-club-summary";
import { OverviewMatchCard } from "@/components/overview/overview-match-card";
import {
  SHOWCASE_CLUB_NAME,
  SHOWCASE_EDITORIAL,
  SHOWCASE_CARDS,
} from "./landing-showcase-data";

export function OverviewShowcase() {
  if (SHOWCASE_CARDS.length === 0) return null;

  return (
    <div className="showcase" aria-label={`Dados reais do clube ${SHOWCASE_CLUB_NAME}`}>
      <div className="showcase-summary">
        <div className="showcase-summary-header">
          <span className="showcase-club-name">{SHOWCASE_CLUB_NAME}</span>
        </div>
        <OverviewClubSummary
          editorial={SHOWCASE_EDITORIAL}
          clubName={SHOWCASE_CLUB_NAME}
          className="showcase-club-summary"
        />
      </div>
      <div className="showcase-cards">
        {SHOWCASE_CARDS.map((p, i) => (
          <div key={p.matchId} className="showcase-card-wrap">
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
