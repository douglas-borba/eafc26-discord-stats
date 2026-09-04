// @ts-expect-error The dashboard intentionally does not ship @types/react-dom.
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { DiscoveryView, EvidenceAuditDetails, NovelMetricsView, ObservationComparisonView, PositionObservationsView, type DiscoveryData, type NovelResult, type ObservationComparison, type ObservationEvidenceAudit, type PositionObservationsData } from "@/components/admin/advanced-stats-explorer";

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
  it("renders read-only exact audit facts and RAW provenance without a sporting claim", () => {
    const audit: ObservationEvidenceAudit = {
      identity: { clubId: "club-a", matchId: "tumultua-match", playerId: "player-1", phrase: "Melhore seu tempo de bola" },
      canonicalMatch: { clubId: "club-a", matchId: "tumultua-match", playedAt: "2026-09-04T12:00:00Z", ourClubName: "QI da Topeira", opponentName: "Tumultua FC", ourScore: 3, opponentScore: 1, outcome: "WIN" },
      player: { playerId: "player-1", platformName: "dbeng_bass", proName: "R. Nazario" },
      observation: { phrase: "Melhore seu tempo de bola", observedCount: 6, completeness: "AT_LEAST", note: "literal", observedPositionContext: "CAM", createdAt: "2026-09-01T12:00:00Z", updatedAt: "2026-09-02T12:00:00Z" },
      playerMatchObservations: [
        { phrase: "Bom passe", observedCount: 2, completeness: "AT_LEAST" },
        { phrase: "Melhore seu tempo de bola", observedCount: 6, completeness: "AT_LEAST" },
      ],
      vectorTruncated: false,
      candidate: { aggregateIndex: 0, code: 183, provenance: "EXPLICIT_VALUE", explicitRawValue: 3, valueUsedByAnalyzer: 3, comparison: "CONTRADICTED", difference: -3, rawAggregate: "174:25,183:3", rawEntries: [{ code: 174, value: 25 }, { code: 183, value: 3 }], rawEntriesTruncated: false },
    };

    const html = renderToStaticMarkup(<EvidenceAuditDetails audit={audit} />);

    expect(html).toContain("AUDITORIA DE EVIDÊNCIA");
    expect(html).toContain("QI da Topeira");
    expect(html).toContain("Tumultua FC");
    expect(html).toContain("Melhore seu tempo de bola");
    expect(html).toContain("Observado ≥ 6 · Aggregate = 3 · Diferença = -3 · CONTRADICTED");
    expect(html).toContain("EXPLICIT_VALUE — código presente no RAW");
    expect(html).toContain("Outras observações deste mesmo jogador na partida");
    expect(html).toContain("Bom passe");
    expect(html).not.toContain("Reconciliar");
    expect(html).not.toContain("likely wrong match");
  });

  it("renders absent-code and unavailable RAW provenance distinctly", () => {
    const baseAudit: ObservationEvidenceAudit = {
      identity: { clubId: "club", matchId: "match", playerId: "player", phrase: "Frase" }, canonicalMatch: null,
      player: { playerId: "player", platformName: null, proName: null },
      observation: { phrase: "Frase", observedCount: 1, completeness: "AT_LEAST", note: null, observedPositionContext: null, createdAt: null, updatedAt: null },
      playerMatchObservations: [{ phrase: "Frase", observedCount: 1, completeness: "AT_LEAST" }], vectorTruncated: false,
      candidate: { aggregateIndex: 0, code: 183, provenance: "CODE_ABSENT_ASSUMED_ZERO", explicitRawValue: null, valueUsedByAnalyzer: 0, comparison: "CONTRADICTED", difference: -1, rawAggregate: "182:1", rawEntries: [{ code: 182, value: 1 }], rawEntriesTruncated: false },
    };
    const absentHtml = renderToStaticMarkup(<EvidenceAuditDetails audit={baseAudit} />);
    const unavailableHtml = renderToStaticMarkup(<EvidenceAuditDetails audit={{ ...baseAudit, candidate: { ...baseAudit.candidate, provenance: "AGGREGATE_UNAVAILABLE", valueUsedByAnalyzer: null, comparison: null, difference: null, rawAggregate: null, rawEntries: [] } }} />);

    expect(absentHtml).toContain("CODE_ABSENT_ASSUMED_ZERO — código ausente; o Analyzer usa 0");
    expect(absentHtml).toContain("Valor usado pelo Analyzer");
    expect(unavailableHtml).toContain("AGGREGATE_UNAVAILABLE — slot RAW indisponível");
  });

  it("renders ranked unknown candidates, known controls and collision warnings without assigning a sporting meaning", () => {
    const comparison: ObservationComparison = {
      phrase: "Melhore seu tempo de bola", annotatedMatches: 3, annotatedObservations: 3, excludedRawUnavailable: 0, contradictedCandidates: 4,
      observationCollisions: [], nextBestExperiments: ["Registre uma nova partida em que agg0[183] e agg0[112] tenham valores diferentes."],
      candidates: [
        {
          aggregateIndex: 0, code: 183, candidateKind: "UNKNOWN_CANDIDATE", registryConfidence: "UNKNOWN", metricName: null, registryEvidence: null,
          annotatedMatches: 3, comparableObservations: 3, totalObservedOccurrences: 8, aggregateLessThanObserved: 0, aggregateEqualObserved: 2,
          aggregateGreaterThanObserved: 1, exactSupportingEvidence: 0, contradictions: 0, totalExcess: 1, atLeastCompatibleCases: 1, classification: "DIRECT_COUNTER_POSSIBLE",
          investigationStatus: "HIGH_PRIORITY", investigationRank: 1,
          evidence: [{ matchId: "cartola", opponentName: "Cartola", observedCount: 2, completeness: "AT_LEAST", aggregateValue: 3, comparison: "AT_LEAST_COMPATIBLE" }],
          candidateCollisions: [{ aggregateIndex: 0, code: 112, candidateKind: "KNOWN_CONTROL", registryConfidence: "CONFIRMED", metricName: "Beats" }],
        },
        {
          aggregateIndex: 0, code: 112, candidateKind: "KNOWN_CONTROL", registryConfidence: "CONFIRMED", metricName: "Beats", registryEvidence: "Validated against EA gameplay data",
          annotatedMatches: 3, comparableObservations: 3, totalObservedOccurrences: 8, aggregateLessThanObserved: 0, aggregateEqualObserved: 2,
          aggregateGreaterThanObserved: 1, exactSupportingEvidence: 0, contradictions: 0, totalExcess: 1, atLeastCompatibleCases: 1, classification: "DIRECT_COUNTER_POSSIBLE",
          investigationStatus: "SURVIVES", investigationRank: null, evidence: [],
          candidateCollisions: [{ aggregateIndex: 0, code: 183, candidateKind: "UNKNOWN_CANDIDATE", registryConfidence: "UNKNOWN", metricName: null }],
        },
      ],
    };

    const html = renderToStaticMarkup(<ObservationComparisonView comparison={comparison} />);

    expect(html).toContain("Discovery candidates");
    expect(html).toContain("UNKNOWN_CANDIDATE");
    expect(html).toContain("Known controls");
    expect(html).toContain("Beats");
    expect(html).toContain("Candidate collision");
    expect(html).toContain("Observational compatibility does not confirm the sporting meaning of a code.");
    expect(html).not.toContain("completed dribbles");
  });

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
      distribution: [{ eaPositionCode: "0", candidate: { rawCode: "0", candidateLabel: "GK", classification: "NUMERIC_EXTERNAL_CANDIDATE", semanticStatus: "UNVERIFIED_EXTERNAL_MAPPING" }, observations: 1 }],
      observations: [{ matchId: "m-1", playedAt: "2026-08-28T00:00:00Z", opponentName: "Opponent", playerId: "p-1", playerName: "Player", eaPositionCode: "0", candidate: { rawCode: "0", candidateLabel: "GK", classification: "NUMERIC_EXTERNAL_CANDIDATE", semanticStatus: "UNVERIFIED_EXTERNAL_MAPPING" }, completion: "COMPLETED", rating: "7.0" }],
    };
    const html = renderToStaticMarkup(<PositionObservationsView data={data} onBack={() => {}} />);

    expect(html).toContain("Coverage");
    expect(html).toContain("FULL");
    expect(html).toContain("UNVERIFIED_EXTERNAL_MAPPING");
    expect(html).toContain("does not claim actual played position");
  });
});
