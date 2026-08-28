// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { DiscoveryView, NovelMetricsView, PositionObservationsView, type DiscoveryData, type NovelResult, type PositionObservationsData } from "@/components/admin/advanced-stats-explorer";

const base: DiscoveryData = {
  analysis: {
    rawMatchesAnalyzed: 8,
    playerMatchObservations: 58,
    aggregate0CodeCount: 131,
    aggregate1CodeCount: 33,
    unknownCodeCount: 158,
    knownCodeCount: 4,
    hypothesisCodeCount: 2,
    inventory: [],
    topCandidates: [],
    topDiscoverySignals: [],
    relations: [],
    correlations: [],
    calibration: [],
    relatedCodeFamilies: [],
  },
  newAggregateDataDetected: [],
};

function render(data: DiscoveryData) {
  return renderToStaticMarkup(
    <DiscoveryView
      data={data}
      aggregate="all"
      minimumMatches="0"
      minimumObservations="0"
      hideKnownRelationships
      confidence="ALL"
      evidence="ALL"
      loading={false}
      selectedCode={null}
      selectedRelation={null}
      onAggregate={() => {}}
      onMinimumMatches={() => {}}
      onMinimumObservations={() => {}}
      onHideKnownRelationships={() => {}}
      onConfidence={() => {}}
      onEvidence={() => {}}
      onRun={() => {}}
      onSelectCode={() => {}}
      onSelectRelation={() => {}}
      onBack={() => {}}
    />,
  );
}

describe("Advanced Stats Discovery V2", () => {
  it("renders safely when there are no candidates or related families", () => {
    const html = render(base);

    expect(html).toContain("No candidate meets the current evidence and confidence filters.");
    expect(html).not.toContain("Related Code Family");
  });

  it("shows V2 informative evidence and a related code family without a sporting label", () => {
    const edge = {
      aggregateIndex: 1, codeA: 26, codeB: 30, observationsTested: 40, matchesTested: 6, pearson: 0.9,
      exactEqualityRate: 0.8, informativeObservations: 25, informativeSupport: 0.92, bothZeroCount: 4,
      aNonZeroCount: 22, bNonZeroCount: 24, bothNonZeroCount: 20, eitherNonZeroCount: 26,
      overlapAmongActive: 20 / 26, zeroDominated: false, penalizedForLowOverlap: false,
      rankingScore: 0.9,
    };
    const relation = {
      id: "EQUAL:1:26:30:", aggregateIndex: 1, relationType: "EQUAL", codeA: 26, codeB: 30, codeC: null,
      observationsTested: 40, matchesTested: 6, exactMatches: 32, violations: 8, supportRate: 0.8,
      evidence: {
        totalObservations: 40, totalMatches: 6, globalMatches: 6, globalMatchesSatisfied: 6, globalSupport: 0.8,
        informativeObservations: 25, informativeMatches: 6, informativeSatisfied: 23, informativeSupport: 0.92,
        bothZeroCount: 4, allZeroCount: 4, aNonZeroCount: 22, bNonZeroCount: 24, bothNonZeroCount: 20,
        eitherNonZeroCount: 26, bothNonZeroRate: 0.5, eitherNonZeroRate: 0.65, overlapAmongActive: 20 / 26, zeroDominated: false,
      },
      evidenceTier: "STRONG_CANDIDATE", explainedByKnownMetric: false,
      score: { total: 80, informativeEvidenceComponent: 27, matchComponent: 18, variationComponent: 10, relationTypeComponent: 20, overlapComponent: 10, counterexampleComponent: -1, zeroDominationPenalty: 0, inequalityPenalty: 0, knownMetricPenalty: 0 },
      examples: [], counterexamples: [],
    };
    const inventory = (code: number) => ({
      aggregateIndex: 1, code, confidence: "UNKNOWN", rawObservationCount: 40, matchCount: 6, playerCount: 12,
      nonZeroCount: 22, zeroCount: 18, prevalence: 0.55, min: 0, max: 8, mean: 2.5, median: 2,
      sum: 100, distinctValueCount: 6, technicalClassification: "VARIABLE", observedValues: [],
    });
    const html = render({
      ...base,
      analysis: {
        ...base.analysis,
        topCandidates: [relation],
        topDiscoverySignals: [{ id: "relation:EQUAL:1:26:30:", aggregateIndex: 1, pattern: "26 == 30", type: "EXACT_EQUALITY", informativeObservations: 25, informativeSupport: 0.92, globalObservations: 40, globalSupport: 0.8, matches: 6, nonZeroOverlap: 20 / 26, counterexamples: 8, tier: "STRONG_CANDIDATE", zeroDominated: false, score: 80, relationId: relation.id }],
        relations: [relation],
        correlations: [edge],
        inventory: [inventory(26), inventory(30)],
        relatedCodeFamilies: [{ aggregateIndex: 1, codes: [26, 30], codeCount: 2, relationshipCount: 1, observations: 40, matches: 6, averagePearson: 0.9, minimumPearson: 0.9, averageNonZeroOverlap: 20 / 26, strongestEdge: edge, edges: [edge] }],
      },
    });

    expect(html).toContain("Informative evidence");
    expect(html).toContain("Related Code Families");
    expect(html).toContain("Non-zero overlap");
    expect(html).not.toContain("passing family");
  });
});

describe("Advanced Stats Explorer investigation surfaces", () => {
  it("renders an UNKNOWN novel candidate as investigation evidence without a sporting label", () => {
    const data: NovelResult = {
      rawMatchesAnalyzed: 5,
      playerMatchObservations: 8,
      families: [],
      candidates: [{
        aggregateIndex: 0, code: 999, registryStatus: "UNKNOWN", observations: 8, activeObservations: 5, activeRate: 0.625,
        matches: 5, players: 4, min: 0, max: 7, mean: 2.5, median: 2, distinctValues: 5, noveltyScore: 71,
        priority: "HIGH", classification: "NOVEL_CANDIDATE", closestKnownRelation: null, warnings: [], familyId: null, familyRepresentative: true,
      }],
    };
    const html = renderToStaticMarkup(<NovelMetricsView data={data} detail={null} loading={false} onRun={() => {}} onInspect={() => {}} onCloseDetail={() => {}} onBack={() => {}} />);

    expect(html).toContain("agg0[999]");
    expect(html).toContain("NOVEL_CANDIDATE");
    expect(html).toContain("No sporting meaning is assigned.");
  });

  it("renders raw position code zero as covered but explicitly unverified", () => {
    const data: PositionObservationsData = {
      coverage: "FULL", distinctCodes: 1,
      distribution: [{ eaPositionCode: "0", candidate: { rawCode: "0", candidateLabel: "GK", classification: "KNOWN_EXTERNAL_CANDIDATE", semanticStatus: "UNVERIFIED_EXTERNAL_MAPPING" }, observations: 1 }],
      observations: [{ matchId: "m-1", playedAt: "2026-08-28T00:00:00Z", opponentName: "Opponent", playerId: "p-1", playerName: "Player", eaPositionCode: "0", candidate: { rawCode: "0", candidateLabel: "GK", classification: "KNOWN_EXTERNAL_CANDIDATE", semanticStatus: "UNVERIFIED_EXTERNAL_MAPPING" }, completion: "COMPLETED", rating: "7.0" }],
    };
    const html = renderToStaticMarkup(<PositionObservationsView data={data} onBack={() => {}} />);

    expect(html).toContain("Coverage");
    expect(html).toContain("FULL");
    expect(html).toContain("UNVERIFIED_EXTERNAL_MAPPING");
    expect(html).toContain("does not claim actual played position");
  });
});
