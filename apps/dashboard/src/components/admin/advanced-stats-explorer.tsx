"use client";

import { useState, useCallback, useEffect, useMemo } from "react";
import {
  DEFAULT_LIVE_FEEDBACK_PHRASES,
  findLiveFeedbackSuggestions,
  normalizeLiveFeedbackPhrase,
  sortLiveFeedbackPhrases,
  uniqueExactPhrases,
} from "@/lib/live-feedback-phrases";
import {
  buildLiveCollectorObservationInputs,
  clearLiveCollectorAssociation,
  createLiveCollectorDraft,
  hasCurrentLiveCollectorAssociation,
  selectLiveCollectorMatch,
  selectLiveCollectorPlayer,
  type CollectorDraft,
} from "@/lib/live-collector-draft";

type MatchSummary = {
  matchId: string;
  playedAt: string;
  opponentName: string | null;
  ourScore: number;
  opponentScore: number;
  hasRawAggregates: boolean;
};

type PlayerSummary = {
  playerId: string;
  platformName: string | null;
  proName: string | null;
  hasRawAggregates: boolean;
};

type AggregateEntry = {
  aggregate: number;
  code: number;
  value: number;
  confidence: string;
  metricName: string | null;
  evidence: string | null;
};

type KnownStats = {
  rating: string | null;
  goals: number | null;
  assists: number | null;
  shots: number | null;
  passesAttempted: number | null;
  passesCompleted: number | null;
  tacklesAttempted: number | null;
  tacklesCompleted: number | null;
  secondAssists: number;
  throughPasses: number;
  beats: number;
  interceptions: number;
  advancedCoverage: string;
};

type UnknownFieldEntry = {
  scope: string;
  name: string;
  jsonType: string;
  value: string;
  truncated: boolean;
  originalSize: number;
  isAdditionalAggregateCandidate: boolean;
};

type UnknownFieldsData = {
  status: "UNAVAILABLE" | "EMPTY" | "PRESENT";
  fields: UnknownFieldEntry[];
};

type PlayerExplorerData = {
  playerId: string;
  platformName: string | null;
  proName: string | null;
  matchId: string;
  playedAt: string;
  opponentName: string | null;
  aggregateEntries: AggregateEntry[];
  knownStats: KnownStats;
  rawAggregate0: string | null;
  rawAggregate1: string | null;
  rawAggregate2: string | null;
  rawAggregate3: string | null;
  rawContextFields: { name: string; jsonType: string; value: string; truncated: boolean }[];
  unknownFields: UnknownFieldsData;
  eaPositionCode: string | null;
  eaPositionCandidate: { rawCode: string | null; candidateLabel: string | null; classification: string; semanticStatus: string };
};

type ObservedCodeValue = { matchId: string; timestamp: string; playerId: string; playerName: string | null; value: number };
type CodeInventory = {
  aggregateIndex: number; code: number; confidence: string; rawObservationCount: number; matchCount: number;
  playerCount: number; nonZeroCount: number; zeroCount: number; prevalence: number; min: number; max: number;
  mean: number; median: number; sum: number; distinctValueCount: number; technicalClassification: string;
  observedValues: ObservedCodeValue[];
};
type RelationExample = { matchId: string; timestamp: string; playerId: string; playerName: string | null; a: number; b: number; c: number | null; expected: number; difference: number };
type RelationEvidence = {
  totalObservations: number; totalMatches: number; globalMatches: number; globalMatchesSatisfied: number; globalSupport: number;
  informativeObservations: number; informativeMatches: number; informativeSatisfied: number; informativeSupport: number;
  bothZeroCount: number; allZeroCount: number; aNonZeroCount: number; bNonZeroCount: number; bothNonZeroCount: number;
  eitherNonZeroCount: number; bothNonZeroRate: number; eitherNonZeroRate: number; overlapAmongActive: number; zeroDominated: boolean;
};
type DiscoveryRelation = {
  id: string; aggregateIndex: number; relationType: string; codeA: number; codeB: number; codeC: number | null;
  observationsTested: number; matchesTested: number; exactMatches: number; violations: number; supportRate: number;
  evidence: RelationEvidence; evidenceTier: string; explainedByKnownMetric: boolean; score: { total: number; informativeEvidenceComponent: number; matchComponent: number; variationComponent: number; relationTypeComponent: number; overlapComponent: number; counterexampleComponent: number; zeroDominationPenalty: number; inequalityPenalty: number; knownMetricPenalty: number };
  examples: RelationExample[]; counterexamples: RelationExample[];
};
type CodeCorrelation = {
  aggregateIndex: number; codeA: number; codeB: number; observationsTested: number; matchesTested: number; pearson: number;
  exactEqualityRate: number; informativeObservations: number; informativeSupport: number; bothZeroCount: number;
  aNonZeroCount: number; bNonZeroCount: number; bothNonZeroCount: number; eitherNonZeroCount: number;
  overlapAmongActive: number; zeroDominated: boolean; penalizedForLowOverlap: boolean;
};
type KnownMetricCalibration = {
  aggregateIndex: number; code: number; metric: string; observationsTested: number; matchesTested: number; exactMatches: number;
  supportRate: number; informativeObservations: number; informativeMatches: number; informativeSatisfied: number;
  informativeSupport: number; bothZeroCount: number; zeroDominated: boolean; redundantWithKnownMetric: boolean;
};
type RelatedCodeFamily = {
  aggregateIndex: number; codes: number[]; codeCount: number; relationshipCount: number; observations: number; matches: number;
  averagePearson: number; minimumPearson: number; averageNonZeroOverlap: number; strongestEdge: CodeCorrelation; edges: CodeCorrelation[];
};
type TopDiscoverySignal = {
  id: string; aggregateIndex: number; pattern: string; type: string; informativeObservations: number; informativeSupport: number;
  globalObservations: number; globalSupport: number; matches: number; nonZeroOverlap: number; counterexamples: number;
  tier: string; zeroDominated: boolean; score: number; relationId: string | null;
};
export type DiscoveryData = {
  analysis: {
    rawMatchesAnalyzed: number; playerMatchObservations: number; aggregate0CodeCount: number; aggregate1CodeCount: number;
    unknownCodeCount: number; knownCodeCount: number; hypothesisCodeCount: number; inventory: CodeInventory[];
    topCandidates: DiscoveryRelation[]; topDiscoverySignals: TopDiscoverySignal[]; relations: DiscoveryRelation[];
    correlations: CodeCorrelation[];
    calibration: KnownMetricCalibration[];
    relatedCodeFamilies: RelatedCodeFamily[];
  };
  newAggregateDataDetected: { fieldName: string; matchCount: number; playerCount: number }[];
};

type AnchorRef = { type: string; aggregateIndex: number | null; code: number | null; metricName: string | null };
type AnchorProfile = {
  anchor: AnchorRef; registryStatus: string; knownLabel: string | null; observations: number; matches: number;
  distinctPlayers: number; nonZeroObservations: number; prevalence: number; min: number; max: number; mean: number; median: number;
};
type ResidualDistribution = { residualCounts: Record<string, number>; min: number; max: number; mean: number; median: number; zeroPercent: number };
type AnchorRelationshipScore = {
  total: number; informativeSampleComponent: number; matchDiversityComponent: number; nonZeroOverlapComponent: number;
  conditionalSupportComponent: number; equalityComponent: number; subtypeConsistencyComponent: number; correlationComponent: number; counterexamplePenalty: number;
};
type AnchorEvidenceRow = {
  matchId: string; timestamp: string; playerId: string; playerName: string | null; anchorValue: number; candidateValue: number;
  difference: number; ratio: number | null; goals: number | null; assists: number | null; shots: number | null;
  passesAttempted: number | null; passesCompleted: number | null; tacklesAttempted: number | null; tacklesCompleted: number | null;
  code112: number | null; code115: number | null; code152: number | null; code174: number | null; matchCompletion: string | null;
};
type ConditionalProfileEntry = {
  candidateCode: number; candidateActiveWhenAnchorActive: number; anchorActiveObservations: number;
  candidateInactiveWhenAnchorActive: number; anchorActiveWhenCandidateActive: number; candidateActiveObservations: number;
  pCandidateActiveGivenAnchorActive: number | null; pAnchorActiveGivenCandidateActive: number | null;
};
type AnchorRelationship = {
  candidateAggregateIndex: number; candidateCode: number; candidateRegistryStatus: string; candidateKnownLabel: string | null;
  technicalClassification: string; exactEqualityRate: number; informativeEqualityRate: number;
  anchorGteCandidateRate: number; candidateGteAnchorRate: number;
  residualAMinusB: ResidualDistribution; residualBMinusA: ResidualDistribution;
  ratioBAMeanWhenAPositive: number | null; ratioABMeanWhenBPositive: number | null;
  pearson: number | null; spearman: number | null; bothNonZero: number; eitherNonZero: number; nonZeroOverlap: number;
  pCandidateActiveGivenAnchorActive: number | null; pAnchorActiveGivenCandidateActive: number | null; pEqualGivenEitherActive: number | null;
  observations: number; informativeObservations: number; matches: number; distinctPlayers: number;
  score: AnchorRelationshipScore; evidenceObservations: AnchorEvidenceRow[]; differenceCases: AnchorEvidenceRow[];
};
type FamilyMatrixCell = { codeA: number; codeB: number; pearson: number | null; informativeEquality: number; nonZeroOverlap: number };
type FamilyObservationRow = {
  matchId: string; timestamp: string; playerId: string; playerName: string | null; values: Record<string, number>;
  goals: number | null; assists: number | null; shots: number | null; passesAttempted: number | null;
  passesCompleted: number | null; tacklesAttempted: number | null; tacklesCompleted: number | null; matchCompletion: string | null;
};
type FamilyInvestigation = { aggregateIndex: number; codes: number[]; matrix: FamilyMatrixCell[]; observations: FamilyObservationRow[] };
type DatasetMetadata = { rawMatchesAnalyzed: number; playerMatchObservations: number; distinctPlayers: number; distinctMatches: number };
type AnchorInvestigation = { anchor: AnchorProfile; relationships: AnchorRelationship[]; conditionalProfiles: ConditionalProfileEntry[]; dataset: DatasetMetadata };

// V3 — Residual Explainer types
type ResidualGroup = { direction: string; count: number; matches: number; players: number };
type ResidualCodeStats = { count: number; activeCount: number; activationRate: number | null; mean: number | null; median: number | null; min: number | null; max: number | null };
type ResidualContrast = { activationRateDelta: number | null; meanDelta: number | null };
type ResidualDiscriminatorScore = {
  total: number; activationDeltaComponent: number; valueDeltaComponent: number; consistencyComponent: number;
  sampleSizeComponent: number; matchDiversityComponent: number; playerDiversityComponent: number;
  directionSpecificityComponent: number; singlePlayerPenalty: number; singleMatchPenalty: number; tinySamplePenalty: number;
};
type ResidualDiscriminator = {
  aggregateIndex: number; code: number; registryStatus: string; registryLabel: string | null;
  technicalClassification: string; totalObservations: number; activeObservations: number;
  negative: ResidualCodeStats; zero: ResidualCodeStats; positive: ResidualCodeStats;
  positiveVsZero: ResidualContrast; negativeVsZero: ResidualContrast; positiveVsNegative: ResidualContrast;
  pActiveGivenPositive: number | null; pActiveGivenZero: number | null; pActiveGivenNegative: number | null;
  score: ResidualDiscriminatorScore; warnings: string[]; distinctMatches: number; distinctPlayers: number;
};
type ResidualEvidenceRow = {
  matchId: string; timestamp: string; playerId: string; playerName: string | null;
  anchorValue: number; candidateValue: number; residual: number; residualDirection: string; investigatedCodeValue: number;
  goals: number | null; assists: number | null; shots: number | null;
  passesAttempted: number | null; passesCompleted: number | null;
  tacklesAttempted: number | null; tacklesCompleted: number | null;
  code112: number | null; code115: number | null; code152: number | null; code174: number | null;
  matchCompletion: string | null;
};
type ResidualSignatureEntry = { aggregateIndex: number; code: number; value: number; registryStatus: string; registryLabel: string | null; isTopDiscriminator: boolean };
type ResidualSignature = {
  matchId: string; playerId: string; playerName: string | null;
  anchorValue: number; candidateValue: number; residual: number; residualDirection: string;
  matchCompletion: string | null; relevantCodes: ResidualSignatureEntry[];
};
type ResidualExplainerResult = {
  anchor: AnchorRef; candidateAggregateIndex: number; candidateCode: number;
  candidateRegistryStatus: string; candidateLabel: string | null;
  groups: ResidualGroup[]; discriminators: ResidualDiscriminator[];
  evidence: ResidualEvidenceRow[]; signatures: ResidualSignature[];
  dataset: DatasetMetadata;
};

export type NovelKnownRelation = { name: string; observations: number; exactEqualityRate: number | null; pearson: number | null; spearman: number | null; nonZeroOverlap: number | null; pCandidateActiveGivenKnownActive: number | null; pKnownActiveGivenCandidateActive: number | null; stableRatio: boolean | null; classification: string };
export type NovelCandidate = { aggregateIndex: number; code: number; registryStatus: string; observations: number; activeObservations: number; activeRate: number | null; matches: number; players: number; min: number; max: number; mean: number | null; median: number | null; distinctValues: number; noveltyScore: number; priority: string; classification: string; closestKnownRelation: NovelKnownRelation | null; warnings: string[]; familyId: string | null; familyRepresentative: boolean };
export type NovelFamily = { id: string; aggregateIndex: number; representativeCode: number; relatedCodes: number[]; relationship: string };
export type NovelResult = { rawMatchesAnalyzed: number; playerMatchObservations: number; candidates: NovelCandidate[]; families: NovelFamily[] };
export type NovelEvidence = { matchId: string; timestamp: string; playerId: string; playerName: string | null; completion: string | null; value: number; knownMetrics: Record<string, number | null> };
export type NovelDetail = { candidate: NovelCandidate; knownRelations: NovelKnownRelation[]; relatedFamily: NovelFamily | null; highValues: NovelEvidence[]; lowNonZeroValues: NovelEvidence[]; zeroValues: NovelEvidence[] };
export type PositionObservation = { matchId: string; playedAt: string; opponentName: string | null; playerId: string; playerName: string | null; eaPositionCode: string | null; candidate: { rawCode: string | null; candidateLabel: string | null; classification: string; semanticStatus: string }; completion: string; rating: string | null };
export type PositionObservationsData = { coverage: string; observations: PositionObservation[]; distribution: { eaPositionCode: string | null; candidate: PositionObservation["candidate"]; observations: number }[]; distinctCodes: number };

type View = "matches" | "players" | "detail" | "compare" | "discovery" | "anchor" | "novel" | "position";

export function AdvancedStatsExplorer() {
  const [clubId, setClubId] = useState("");
  const [activeClubId, setActiveClubId] = useState("");
  const [matches, setMatches] = useState<MatchSummary[]>([]);
  const [players, setPlayers] = useState<PlayerSummary[]>([]);
  const [selectedMatch, setSelectedMatch] = useState<MatchSummary | null>(null);
  const [playerData, setPlayerData] = useState<PlayerExplorerData | null>(null);
  const [compareData, setCompareData] = useState<PlayerExplorerData[]>([]);
  const [compareMatchIds, setCompareMatchIds] = useState<Set<string>>(new Set());
  const [comparePlayerId, setComparePlayerId] = useState("");
  const [view, setView] = useState<View>("matches");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showZeros, setShowZeros] = useState(false);
  const [showRaw, setShowRaw] = useState(false);
  const [discovery, setDiscovery] = useState<DiscoveryData | null>(null);
  const [discoveryAggregate, setDiscoveryAggregate] = useState("all");
  const [minimumMatches, setMinimumMatches] = useState("0");
  const [minimumObservations, setMinimumObservations] = useState("0");
  const [hideKnownRelationships, setHideKnownRelationships] = useState(true);
  const [discoveryConfidence, setDiscoveryConfidence] = useState("ALL");
  const [discoveryEvidence, setDiscoveryEvidence] = useState("ALL");
  const [selectedDiscoveryCode, setSelectedDiscoveryCode] = useState<CodeInventory | null>(null);
  const [selectedDiscoveryRelation, setSelectedDiscoveryRelation] = useState<DiscoveryRelation | null>(null);
  const [anchorData, setAnchorData] = useState<AnchorInvestigation | null>(null);
  const [anchorType, setAnchorType] = useState("AGGREGATE_CODE");
  const [anchorAggregateIndex, setAnchorAggregateIndex] = useState("0");
  const [anchorCode, setAnchorCode] = useState("");
  const [anchorMetric, setAnchorMetric] = useState("goals");
  const [selectedAnchorRelationship, setSelectedAnchorRelationship] = useState<AnchorRelationship | null>(null);
  const [anchorShowDifferencesOnly, setAnchorShowDifferencesOnly] = useState(false);
  const [anchorDnfFilter, setAnchorDnfFilter] = useState("ALL");
  const [familyData, setFamilyData] = useState<FamilyInvestigation | null>(null);
  const [familyCodes, setFamilyCodes] = useState("");
  const [residualData, setResidualData] = useState<ResidualExplainerResult | null>(null);
  const [residualFilter, setResidualFilter] = useState("ALL");
  const [selectedDiscriminator, setSelectedDiscriminator] = useState<ResidualDiscriminator | null>(null);
  const [novel, setNovel] = useState<NovelResult | null>(null);
  const [novelDetail, setNovelDetail] = useState<NovelDetail | null>(null);
  const [positionObservations, setPositionObservations] = useState<PositionObservationsData | null>(null);

  const fetchJson = useCallback(async (url: string) => {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    return res.json();
  }, []);

  const loadMatches = useCallback(async () => {
    if (!clubId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const data = await fetchJson(`/api/admin/explorer/clubs/${clubId}/matches?limit=20`);
      setMatches(data);
      setActiveClubId(clubId);
      setView("matches");
      setSelectedMatch(null);
      setPlayerData(null);
      setCompareData([]);
      setCompareMatchIds(new Set());
      setDiscovery(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro desconhecido");
    } finally {
      setLoading(false);
    }
  }, [clubId, fetchJson]);

  const loadPlayers = useCallback(async (match: MatchSummary) => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/matches/${match.matchId}/players`);
      setPlayers(data);
      setSelectedMatch(match);
      setView("players");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro desconhecido");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, fetchJson]);

  const loadPlayerDetail = useCallback(async (playerId: string) => {
    if (!selectedMatch) return;
    setLoading(true);
    setError(null);
    try {
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/matches/${selectedMatch.matchId}/players/${playerId}`);
      setPlayerData(data);
      setView("detail");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro desconhecido");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, selectedMatch, fetchJson]);

  const toggleCompareMatch = useCallback((matchId: string) => {
    setCompareMatchIds((prev) => {
      const next = new Set(prev);
      if (next.has(matchId)) next.delete(matchId);
      else if (next.size < 5) next.add(matchId);
      return next;
    });
  }, []);

  const loadComparison = useCallback(async () => {
    if (!comparePlayerId || compareMatchIds.size === 0) return;
    setLoading(true);
    setError(null);
    try {
      const qs = Array.from(compareMatchIds).map((id) => `matchIds=${id}`).join("&");
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/players/${comparePlayerId}/compare?${qs}`);
      setCompareData(data);
      setView("compare");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro desconhecido");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, comparePlayerId, compareMatchIds, fetchJson]);

  const exportData = useCallback(async (format: "json" | "csv") => {
    try {
      const res = await fetch(`/api/admin/explorer/clubs/${activeClubId}/export?limit=20&format=${format}`, { cache: "no-store" });
      if (!res.ok) throw new Error(`${res.status}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `explorer-${activeClubId}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Export failed");
    }
  }, [activeClubId]);

  const loadDiscovery = useCallback(async () => {
    if (!activeClubId) return;
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        limit: "10",
        aggregate: discoveryAggregate,
        minimumMatches,
        minimumObservations,
        hideKnownRelationships: String(hideKnownRelationships),
      });
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/discovery?${params}`);
      setDiscovery(data);
      setSelectedDiscoveryCode(null);
      setSelectedDiscoveryRelation(null);
      setView("discovery");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Discovery failed");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, discoveryAggregate, fetchJson, hideKnownRelationships, minimumMatches, minimumObservations]);

  const loadAnchor = useCallback(async () => {
    if (!activeClubId) return;
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ limit: "10", anchorType });
      if (anchorType === "AGGREGATE_CODE" || anchorType === "CONFIRMED_ADVANCED") {
        params.set("aggregateIndex", anchorAggregateIndex);
        params.set("code", anchorCode);
      }
      if (anchorType === "KNOWN_METRIC") {
        params.set("metricName", anchorMetric);
      }
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/anchor?${params}`);
      setAnchorData(data);
      setSelectedAnchorRelationship(null);
      setView("anchor");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Anchor investigation failed");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, anchorType, anchorAggregateIndex, anchorCode, anchorMetric, fetchJson]);

  const loadFamily = useCallback(async () => {
    if (!activeClubId || !familyCodes.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const codes = familyCodes.split(",").map((c) => c.trim()).filter(Boolean).join(",");
      const params = new URLSearchParams({ limit: "10", aggregateIndex: anchorAggregateIndex, codes });
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/family?${params}`);
      setFamilyData(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Family investigation failed");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, familyCodes, anchorAggregateIndex, fetchJson]);

  const loadResidualExplainer = useCallback(async (rel: AnchorRelationship) => {
    if (!activeClubId) return;
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        limit: "10", anchorType,
        candidateAggregateIndex: String(rel.candidateAggregateIndex),
        candidateCode: String(rel.candidateCode),
      });
      if (anchorType === "AGGREGATE_CODE" || anchorType === "CONFIRMED_ADVANCED") {
        params.set("aggregateIndex", anchorAggregateIndex);
        params.set("code", anchorCode);
      }
      if (anchorType === "KNOWN_METRIC") params.set("metricName", anchorMetric);
      const data = await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/residual-explainer?${params}`);
      setResidualData(data);
      setResidualFilter("ALL");
      setSelectedDiscriminator(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Residual explainer failed");
    } finally {
      setLoading(false);
    }
  }, [activeClubId, anchorType, anchorAggregateIndex, anchorCode, anchorMetric, fetchJson]);

  const loadNovel = useCallback(async () => {
    if (!activeClubId) return;
    setLoading(true); setError(null);
    try { setNovel(await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/novel-metrics?limit=10`)); setNovelDetail(null); setView("novel"); }
    catch (e) { setError(e instanceof Error ? e.message : "Novel discovery failed"); }
    finally { setLoading(false); }
  }, [activeClubId, fetchJson]);

  const loadNovelDetail = useCallback(async (candidate: NovelCandidate) => {
    if (!activeClubId) return;
    setLoading(true); setError(null);
    try { setNovelDetail(await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/novel-metrics?limit=10&aggregateIndex=${candidate.aggregateIndex}&code=${candidate.code}`)); }
    catch (e) { setError(e instanceof Error ? e.message : "Novel detail failed"); }
    finally { setLoading(false); }
  }, [activeClubId, fetchJson]);

  const loadPositionObservations = useCallback(async () => {
    if (!activeClubId || !playerData) return;
    setLoading(true); setError(null);
    try { setPositionObservations(await fetchJson(`/api/admin/explorer/clubs/${activeClubId}/players/${playerData.playerId}/position-observations?limit=20`)); setView("position"); }
    catch (e) { setError(e instanceof Error ? e.message : "Position observations failed"); }
    finally { setLoading(false); }
  }, [activeClubId, playerData, fetchJson]);

  return (
    <div style={{ fontFamily: "monospace", maxWidth: 1200 }}>
      <h1 style={{ fontSize: 20, marginBottom: 4 }}>Advanced Stats Explorer</h1>
      <p style={{ fontSize: 12, color: "#6e7681", marginBottom: 16 }}>
        Internal diagnostic tool — correlations are NOT mappings. Unknown codes must not receive sporting meanings.
      </p>

      {/* Club selector */}
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        <input
          value={clubId}
          onChange={(e) => setClubId(e.target.value)}
          placeholder="Club ID"
          style={{ padding: "6px 10px", border: "1px solid #30363d", borderRadius: 6, background: "#0d1117", color: "#c9d1d9", width: 200 }}
        />
        <button onClick={loadMatches} disabled={loading || !clubId.trim()} style={btnStyle}>
          {loading ? "Loading..." : "Load Matches"}
        </button>
        {activeClubId && (
          <>
            <button onClick={() => exportData("json")} style={btnStyle}>Export JSON</button>
            <button onClick={() => exportData("csv")} style={btnStyle}>Export CSV</button>
            <button onClick={loadDiscovery} disabled={loading} style={btnStyle}>Discovery</button>
            <button onClick={loadNovel} disabled={loading} style={btnStyle}>Novel Metrics</button>
            <button onClick={() => setView("anchor")} style={btnStyle}>Anchor</button>
          </>
        )}
      </div>

      {error && <div style={{ color: "#f85149", marginBottom: 12, fontSize: 13 }}>{error}</div>}

      {/* Matches list */}
      {view === "matches" && matches.length > 0 && (
        <div>
          <h2 style={h2Style}>Matches ({matches.length})</h2>
          <div style={{ display: "flex", gap: 8, marginBottom: 12, alignItems: "center" }}>
            <span style={{ fontSize: 12, color: "#8b949e" }}>Compare player:</span>
            <input
              value={comparePlayerId}
              onChange={(e) => setComparePlayerId(e.target.value)}
              placeholder="Player ID"
              style={{ padding: "4px 8px", border: "1px solid #30363d", borderRadius: 4, background: "#0d1117", color: "#c9d1d9", width: 160, fontSize: 12 }}
            />
            <button onClick={loadComparison} disabled={loading || !comparePlayerId || compareMatchIds.size === 0} style={{ ...btnStyle, fontSize: 12, padding: "4px 10px" }}>
              Compare ({compareMatchIds.size})
            </button>
          </div>
          <table style={tableStyle}>
            <thead>
              <tr>
                <th style={thStyle}>Sel</th>
                <th style={thStyle}>Match ID</th>
                <th style={thStyle}>Date</th>
                <th style={thStyle}>Opponent</th>
                <th style={thStyle}>Score</th>
                <th style={thStyle}>Raw</th>
                <th style={thStyle}></th>
              </tr>
            </thead>
            <tbody>
              {matches.map((m) => (
                <tr key={m.matchId} style={{ borderBottom: "1px solid #21262d" }}>
                  <td style={tdStyle}>
                    <input
                      type="checkbox"
                      checked={compareMatchIds.has(m.matchId)}
                      onChange={() => toggleCompareMatch(m.matchId)}
                      disabled={!compareMatchIds.has(m.matchId) && compareMatchIds.size >= 5}
                    />
                  </td>
                  <td style={{ ...tdStyle, fontSize: 11 }}>{m.matchId}</td>
                  <td style={tdStyle}>{new Date(m.playedAt).toLocaleDateString("pt-BR")}</td>
                  <td style={tdStyle}>{m.opponentName ?? "—"}</td>
                  <td style={tdStyle}>{m.ourScore} × {m.opponentScore}</td>
                  <td style={tdStyle}>{m.hasRawAggregates ? "✓" : "—"}</td>
                  <td style={tdStyle}>
                    <button onClick={() => loadPlayers(m)} style={{ ...btnStyle, fontSize: 12, padding: "2px 8px" }}>Players</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Players list */}
      {view === "players" && selectedMatch && (
        <div>
          <button onClick={() => setView("matches")} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to matches</button>
          <h2 style={h2Style}>
            Players — vs {selectedMatch.opponentName ?? "?"} ({selectedMatch.ourScore}×{selectedMatch.opponentScore})
          </h2>
          <table style={tableStyle}>
            <thead>
              <tr>
                <th style={thStyle}>Player ID</th>
                <th style={thStyle}>Name</th>
                <th style={thStyle}>Pro Name</th>
                <th style={thStyle}>Raw</th>
                <th style={thStyle}></th>
              </tr>
            </thead>
            <tbody>
              {players.map((p) => (
                <tr key={p.playerId} style={{ borderBottom: "1px solid #21262d" }}>
                  <td style={{ ...tdStyle, fontSize: 11 }}>{p.playerId}</td>
                  <td style={tdStyle}>{p.platformName ?? "—"}</td>
                  <td style={tdStyle}>{p.proName ?? "—"}</td>
                  <td style={tdStyle}>{p.hasRawAggregates ? "✓" : "—"}</td>
                  <td style={tdStyle}>
                    <button onClick={() => loadPlayerDetail(p.playerId)} style={{ ...btnStyle, fontSize: 12, padding: "2px 8px" }}>Inspect</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Player detail */}
      {view === "detail" && playerData && (
        <PlayerDetailView
          data={playerData}
          clubId={activeClubId}
          showZeros={showZeros}
          showRaw={showRaw}
          onToggleZeros={() => setShowZeros(!showZeros)}
          onToggleRaw={() => setShowRaw(!showRaw)}
          onPositionObservations={loadPositionObservations}
          onBack={() => setView("players")}
        />
      )}

      {/* Multi-match comparison */}
      {view === "compare" && compareData.length > 0 && (
        <CompareView data={compareData} showZeros={showZeros} onToggleZeros={() => setShowZeros(!showZeros)} onBack={() => setView("matches")} />
      )}

      {view === "discovery" && (
        <DiscoveryView
          data={discovery}
          aggregate={discoveryAggregate}
          minimumMatches={minimumMatches}
          minimumObservations={minimumObservations}
          hideKnownRelationships={hideKnownRelationships}
          confidence={discoveryConfidence}
          evidence={discoveryEvidence}
          loading={loading}
          selectedCode={selectedDiscoveryCode}
          selectedRelation={selectedDiscoveryRelation}
          onAggregate={setDiscoveryAggregate}
          onMinimumMatches={setMinimumMatches}
          onMinimumObservations={setMinimumObservations}
          onHideKnownRelationships={setHideKnownRelationships}
          onConfidence={setDiscoveryConfidence}
          onEvidence={setDiscoveryEvidence}
          onRun={loadDiscovery}
          onSelectCode={setSelectedDiscoveryCode}
          onSelectRelation={setSelectedDiscoveryRelation}
          onBack={() => setView("matches")}
        />
      )}

      {view === "anchor" && (
        <AnchorView
          data={anchorData}
          anchorType={anchorType}
          aggregateIndex={anchorAggregateIndex}
          code={anchorCode}
          metric={anchorMetric}
          loading={loading}
          selectedRelationship={selectedAnchorRelationship}
          showDifferencesOnly={anchorShowDifferencesOnly}
          dnfFilter={anchorDnfFilter}
          familyData={familyData}
          familyCodes={familyCodes}
          onAnchorType={setAnchorType}
          onAggregateIndex={setAnchorAggregateIndex}
          onCode={setAnchorCode}
          onMetric={setAnchorMetric}
          onRun={loadAnchor}
          onSelectRelationship={setSelectedAnchorRelationship}
          onToggleDifferencesOnly={() => setAnchorShowDifferencesOnly(!anchorShowDifferencesOnly)}
          onDnfFilter={setAnchorDnfFilter}
          onFamilyCodes={setFamilyCodes}
          onLoadFamily={loadFamily}
          onExplainResiduals={loadResidualExplainer}
          residualData={residualData}
          residualFilter={residualFilter}
          onResidualFilter={setResidualFilter}
          selectedDiscriminator={selectedDiscriminator}
          onSelectDiscriminator={setSelectedDiscriminator}
          onBack={() => setView("discovery")}
        />
      )}

      {view === "novel" && <NovelMetricsView data={novel} detail={novelDetail} loading={loading} onRun={loadNovel} onInspect={loadNovelDetail} onCloseDetail={() => setNovelDetail(null)} onBack={() => setView("matches")} />}
      {view === "position" && <PositionObservationsView data={positionObservations} onBack={() => setView("detail")} />}

      {activeClubId && <LiveCollector clubId={activeClubId} onSaved={() => { if (playerData) { /* detail reload handled by parent state */ } }} />}
    </div>
  );
}

function PlayerDetailView({
  data,
  clubId,
  showZeros,
  showRaw,
  onToggleZeros,
  onToggleRaw,
  onPositionObservations,
  onBack,
}: {
  data: PlayerExplorerData;
  clubId: string;
  showZeros: boolean;
  showRaw: boolean;
  onToggleZeros: () => void;
  onToggleRaw: () => void;
  onPositionObservations: () => void;
  onBack: () => void;
}) {
  const entries = showZeros ? data.aggregateEntries : data.aggregateEntries.filter((e) => e.value !== 0);
  const hasAny = data.aggregateEntries.length > 0;

  return (
    <div>
      <button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to players</button>
      <h2 style={h2Style}>{data.platformName ?? data.playerId}</h2>
      <p style={{ fontSize: 12, color: "#8b949e", marginBottom: 8 }}>
        Match: {data.matchId} | vs {data.opponentName ?? "?"} | {new Date(data.playedAt).toLocaleDateString("pt-BR")} | Coverage: {data.knownStats.advancedCoverage}
      </p>
      <section style={{ marginBottom: 16 }}>
        <h3 style={h3Style}>Position Observation</h3>
        <p style={{ color: "#8b949e", fontSize: 11 }}>Raw EA code: <strong>{data.eaPositionCode ?? "—"}</strong> · External candidate: <strong>{data.eaPositionCandidate.candidateLabel ?? "—"}</strong> · {data.eaPositionCandidate.classification} · {data.eaPositionCandidate.semanticStatus}. This is not an actual-position claim.</p>
        <button onClick={onPositionObservations} style={{ ...btnStyle, fontSize: 12 }}>Inspect this player across matches</button>
      </section>

      <ObservationPanel clubId={clubId} data={data} />

      {/* Known stats */}
      <h3 style={h3Style}>Known Stats (ground truth)</h3>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 4, marginBottom: 16 }}>
        <StatChip label="Rating" value={data.knownStats.rating} />
        <StatChip label="Goals" value={data.knownStats.goals} />
        <StatChip label="Assists" value={data.knownStats.assists} />
        <StatChip label="Shots" value={data.knownStats.shots} />
        <StatChip label="Passes" value={`${data.knownStats.passesCompleted ?? "?"}/${data.knownStats.passesAttempted ?? "?"}`} />
        <StatChip label="Tackles" value={`${data.knownStats.tacklesCompleted ?? "?"}/${data.knownStats.tacklesAttempted ?? "?"}`} />
        <StatChip label="Pre-assists" value={data.knownStats.secondAssists} />
        <StatChip label="Through passes" value={data.knownStats.throughPasses} />
        <StatChip label="Beats" value={data.knownStats.beats} />
        <StatChip label="Interceptions (sum)" value={data.knownStats.interceptions} warn />
      </div>

      {/* Aggregate entries */}
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 8 }}>
        <h3 style={{ ...h3Style, marginBottom: 0 }}>Aggregate Codes</h3>
        <label style={{ fontSize: 12, color: "#8b949e", cursor: "pointer" }}>
          <input type="checkbox" checked={showZeros} onChange={onToggleZeros} style={{ marginRight: 4 }} />
          Show zeros
        </label>
        <label style={{ fontSize: 12, color: "#8b949e", cursor: "pointer" }}>
          <input type="checkbox" checked={showRaw} onChange={onToggleRaw} style={{ marginRight: 4 }} />
          Raw view
        </label>
      </div>

      {!hasAny ? (
        <p style={{ color: "#f0883e", fontSize: 13, fontStyle: "italic" }}>Advanced raw data unavailable for this match</p>
      ) : (
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={thStyle}>Agg</th>
              <th style={thStyle}>Code</th>
              <th style={thStyle}>Value</th>
              <th style={thStyle}>Status</th>
              <th style={thStyle}>Mapping</th>
              <th style={thStyle}>Evidence</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={`${e.aggregate}-${e.code}`} style={{ borderBottom: "1px solid #21262d", background: confidenceBg(e.confidence) }}>
                <td style={tdStyle}>{e.aggregate}</td>
                <td style={tdStyle}>{e.code}</td>
                <td style={{ ...tdStyle, fontWeight: 600 }}>{e.value}</td>
                <td style={tdStyle}><ConfidenceBadge confidence={e.confidence} /></td>
                <td style={tdStyle}>{e.metricName ?? "—"}</td>
                <td style={{ ...tdStyle, fontSize: 11, color: "#8b949e" }}>{e.evidence ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* Unknown fields */}
      <div style={{ marginTop: 16 }}>
        <h3 style={h3Style}>Raw Unknown Fields</h3>
        {data.unknownFields.status === "UNAVAILABLE" ? (
          <p style={{ color: "#8b949e", fontSize: 12, fontStyle: "italic" }}>
            Capture not active — historical data before unknown field detection was enabled.
          </p>
        ) : data.unknownFields.status === "EMPTY" ? (
          <p style={{ color: "#3fb950", fontSize: 12 }}>
            ✓ No unknown fields detected — EA response matched all declared DTO fields.
          </p>
        ) : (
          <div>
            <p style={{ color: "#f0883e", fontSize: 12, marginBottom: 8 }}>
              {data.unknownFields.fields.length} unknown field{data.unknownFields.fields.length !== 1 ? "s" : ""} detected
            </p>
            <table style={tableStyle}>
              <thead>
                <tr>
                  <th style={thStyle}>Scope</th>
                  <th style={thStyle}>Field Name</th>
                  <th style={thStyle}>JSON Type</th>
                  <th style={thStyle}>Value</th>
                  <th style={thStyle}>Size</th>
                </tr>
              </thead>
              <tbody>
                {data.unknownFields.fields.map((f) => (
                  <tr key={`${f.scope}-${f.name}`} style={{ borderBottom: "1px solid #21262d", background: f.isAdditionalAggregateCandidate ? "#1a1f2b" : undefined }}>
                    <td style={{ ...tdStyle, fontSize: 11, color: "#8b949e" }}>{f.scope}</td>
                    <td style={{ ...tdStyle, fontFamily: "monospace" }}>
                      {f.name}
                      {f.isAdditionalAggregateCandidate && <span style={{ marginLeft: 6, fontSize: 10, color: "#f0883e", fontFamily: "sans-serif" }}>ADDITIONAL AGGREGATE CANDIDATE</span>}
                    </td>
                    <td style={{ ...tdStyle, fontSize: 11, color: "#8b949e" }}>{f.jsonType}</td>
                    <td style={tdStyle}>
                      <UnknownFieldValue value={f.value} jsonType={f.jsonType} truncated={f.truncated} />
                    </td>
                    <td style={{ ...tdStyle, fontSize: 11, color: "#8b949e" }}>
                      {f.truncated ? `${f.originalSize}B (truncated)` : `${f.originalSize}B`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Raw view */}
      {showRaw && (
        <div style={{ marginTop: 16 }}>
          <h3 style={h3Style}>Raw Aggregates</h3>
          <pre style={{ background: "#161b22", padding: 12, borderRadius: 6, fontSize: 12, overflow: "auto", color: "#c9d1d9" }}>
            {JSON.stringify({ aggregate_0: data.rawAggregate0, aggregate_1: data.rawAggregate1, aggregate_2: data.rawAggregate2, aggregate_3: data.rawAggregate3 }, null, 2)}
          </pre>
        </div>
      )}

      {data.rawContextFields.length > 0 && (
        <section style={{ marginTop: 16 }}>
          <h3 style={h3Style}>RAW context</h3>
          <p style={{ color: "#8b949e", fontSize: 11 }}>Transport context only; no sporting semantics are assigned.</p>
          <pre style={{ background: "#161b22", padding: 12, borderRadius: 6, fontSize: 12, overflow: "auto", color: "#c9d1d9" }}>{JSON.stringify(data.rawContextFields, null, 2)}</pre>
        </section>
      )}
    </div>
  );
}

type ExplorerObservation = { phrase: string; observedCount: number; completeness: "AT_LEAST" | "EXACT"; note: string | null; observedPositionContext: string | null };
type ObservationReconciliationResult = {
  status: "SUCCESS" | "SOURCE_NOT_FOUND" | "TARGET_ALREADY_EXISTS" | "INVALID_TARGET" | "NO_CHANGE";
  observation: ExplorerObservation | null;
  existingTarget: ExplorerObservation | null;
};
export type ObservationEvidence = {
  matchId: string; opponentName: string | null; observedCount: number; completeness: string; aggregateValue: number; comparison: string;
};
export type ObservationCandidate = {
  aggregateIndex: number; code: number; candidateKind: "UNKNOWN_CANDIDATE" | "KNOWN_CONTROL"; registryConfidence: string;
  metricName: string | null; registryEvidence: string | null; annotatedMatches: number; comparableObservations: number;
  totalObservedOccurrences: number; aggregateLessThanObserved: number; aggregateEqualObserved: number;
  aggregateGreaterThanObserved: number; exactSupportingEvidence: number; contradictions: number; totalExcess: number;
  atLeastCompatibleCases: number; classification: string; investigationStatus: "CONTRADICTED" | "INSUFFICIENT_EVIDENCE" | "SURVIVES" | "HIGH_PRIORITY";
  investigationRank: number | null; evidence: ObservationEvidence[];
  candidateCollisions: { aggregateIndex: number; code: number; candidateKind: string; registryConfidence: string; metricName: string | null }[];
};
export type ObservationComparison = {
  phrase: string; annotatedMatches: number; annotatedObservations: number; excludedRawUnavailable: number;
  contradictedCandidates: number; candidates: ObservationCandidate[];
  observationCollisions: { phrase: string; sharedObservedMatches: number }[];
  nextBestExperiments: string[];
};

function ObservationPanel({ clubId, data }: { clubId: string; data: PlayerExplorerData }) {
  const [phrase, setPhrase] = useState("");
  const [count, setCount] = useState("0");
  const [completeness, setCompleteness] = useState<"AT_LEAST" | "EXACT">("AT_LEAST");
  const [note, setNote] = useState("");
  const [positionContext, setPositionContext] = useState("");
  const [items, setItems] = useState<ExplorerObservation[]>([]);
  const [comparison, setComparison] = useState<ObservationComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingReconciliation, setPendingReconciliation] = useState<{ source: ExplorerObservation; targetPhrase: string } | null>(null);
  const [reconciliationMessage, setReconciliationMessage] = useState<string | null>(null);
  const [reconciling, setReconciling] = useState(false);

  const base = `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches/${encodeURIComponent(data.matchId)}/players/${encodeURIComponent(data.playerId)}/observations`;
  const load = async () => {
    setLoading(true); setError(null);
    try { const response = await fetch(base, { cache: "no-store" }); if (!response.ok) throw new Error(`HTTP ${response.status}`); setItems(await response.json()); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to load observations"); }
    finally { setLoading(false); }
  };
  const save = async () => {
    const observedCount = Number(count);
    if (!phrase || !Number.isInteger(observedCount) || observedCount < 0) { setError("Phrase and a non-negative whole count are required."); return; }
    setLoading(true); setError(null);
    try {
      const response = await fetch(base, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ phrase, observedCount, completeness, note: note || null, observedPositionContext: positionContext || null }) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const saved = await response.json() as ExplorerObservation;
      setItems((previous) => [...previous.filter((item) => item.phrase !== saved.phrase), saved].sort((a, b) => a.phrase.localeCompare(b.phrase)));
      setPhrase(""); setCount("0"); setNote(""); setPositionContext(""); setComparison(null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save observation"); }
    finally { setLoading(false); }
  };
  const compare = async (selectedPhrase: string) => {
    setLoading(true); setError(null);
    try {
      const url = `/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(data.playerId)}/observation-comparison?phrase=${encodeURIComponent(selectedPhrase)}&limit=20`;
      const response = await fetch(url, { cache: "no-store" }); if (!response.ok) throw new Error(`HTTP ${response.status}`); setComparison(await response.json());
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to compare observations"); }
    finally { setLoading(false); }
  };

  const reconcile = async () => {
    if (!pendingReconciliation) return;
    setReconciling(true); setError(null); setReconciliationMessage(null);
    try {
      const response = await fetch(`${base}/reconcile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sourcePhrase: pendingReconciliation.source.phrase, targetPhrase: pendingReconciliation.targetPhrase }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const result = await response.json() as ObservationReconciliationResult;
      if (result.status === "SUCCESS") {
        await load();
        setComparison(null);
        setPendingReconciliation(null);
        setReconciliationMessage("Frase reconciliada. A evidência foi preservada e a lista foi atualizada.");
        return;
      }
      if (result.status === "TARGET_ALREADY_EXISTS") {
        const existing = result.existingTarget;
        setReconciliationMessage(
          existing
            ? `Conflito: a frase de destino já existe nesta partida para este jogador (${existing.phrase} = ${existing.observedCount}, ${existing.completeness}). Nenhuma evidência foi alterada.`
            : "Conflito: a frase de destino já existe nesta partida para este jogador. Nenhuma evidência foi alterada.",
        );
      } else if (result.status === "SOURCE_NOT_FOUND") {
        setReconciliationMessage("A frase original não foi encontrada. Atualize as observações antes de tentar novamente.");
      } else if (result.status === "NO_CHANGE") {
        setReconciliationMessage("A frase atual e a frase de destino são iguais. Nenhuma evidência foi alterada.");
      } else {
        setReconciliationMessage("A frase de destino não é válida. Nenhuma evidência foi alterada.");
      }
      setPendingReconciliation(null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to reconcile observation"); }
    finally { setReconciling(false); }
  };

  const knownPhrases = uniqueExactPhrases([...DEFAULT_LIVE_FEEDBACK_PHRASES, ...items.map((item) => item.phrase)]);
  const suggestionFor = (item: ExplorerObservation) =>
    findLiveFeedbackSuggestions(item.phrase, knownPhrases, 1).find((suggestion) => suggestion.phrase !== item.phrase) ?? null;

  return <section style={{ marginBottom: 16, padding: 12, border: "1px solid #30363d", borderRadius: 6 }}>
    <h3 style={h3Style}>Human observational evidence</h3>
    <p style={{ color: "#f0883e", fontSize: 11 }}>Observations are separate from EA facts. AT_LEAST is the default because messages can be missed while playing.</p>
    <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 8 }}>
      <input aria-label="Observation phrase" value={phrase} onChange={(event) => setPhrase(event.target.value)} placeholder="Exact EA feedback phrase" style={inputStyle} />
      <input aria-label="Observed count" value={count} onChange={(event) => setCount(event.target.value)} type="number" min="0" style={{ ...inputStyle, width: 90 }} />
      <select aria-label="Observation completeness" value={completeness} onChange={(event) => setCompleteness(event.target.value as "AT_LEAST" | "EXACT")} style={selectStyle}><option value="AT_LEAST">AT_LEAST</option><option value="EXACT">EXACT</option></select>
      <input aria-label="Observed position context" value={positionContext} onChange={(event) => setPositionContext(event.target.value)} placeholder="Observed position/context (optional)" style={inputStyle} />
      <input aria-label="Observation note" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Note (optional)" style={inputStyle} />
      <button onClick={save} disabled={loading} style={btnStyle}>{loading ? "Saving…" : "Save observation"}</button>
      <button onClick={load} disabled={loading} style={btnStyle}>Load observations</button>
    </div>
    {error && <p style={{ color: "#f85149", fontSize: 11 }}>{error}</p>}
    {reconciliationMessage && <p style={{ color: reconciliationMessage.startsWith("Frase reconciliada") ? "#3fb950" : reconciliationMessage.startsWith("Conflito") ? "#f0883e" : "#f85149", fontSize: 11 }}>{reconciliationMessage}</p>}
    {items.length > 0 && <table style={tableStyle}><thead><tr><th style={thStyle}>Phrase</th><th style={thStyle}>Observed</th><th style={thStyle}>Completeness</th><th style={thStyle}>Position context</th><th style={thStyle}></th></tr></thead><tbody>{items.map((item) => {
      const suggestion = suggestionFor(item);
      return <tr key={item.phrase} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{item.phrase}{suggestion && <div style={{ color: "#8b949e", fontSize: 10, marginTop: 3 }}>Possível frase conhecida: <strong style={{ color: "#c9d1d9" }}>{suggestion.phrase}</strong></div>}</td><td style={tdStyle}>{item.observedCount}</td><td style={tdStyle}>{item.completeness}</td><td style={tdStyle}>{item.observedPositionContext ?? "—"}</td><td style={tdStyle}><div style={{ display: "flex", gap: 5, flexWrap: "wrap" }}><button onClick={() => compare(item.phrase)} disabled={loading || reconciling} style={{ ...btnStyle, fontSize: 11 }}>Compare</button>{suggestion && <button onClick={() => { setPendingReconciliation({ source: item, targetPhrase: suggestion.phrase }); setReconciliationMessage(null); }} disabled={loading || reconciling} style={{ ...btnStyle, fontSize: 11 }}>Reconciliar</button>}</div></td></tr>;
    })}</tbody></table>}
    {pendingReconciliation && <div style={{ marginTop: 8, padding: 8, border: "1px solid #d29922", borderRadius: 4, background: "#161b22", fontSize: 11 }}>
      <strong>Confirmar reconciliação de evidência</strong>
      <p style={{ margin: "5px 0", color: "#c9d1d9" }}>Frase atual: <strong>{pendingReconciliation.source.phrase}</strong><br />Frase de destino: <strong>{pendingReconciliation.targetPhrase}</strong><br />Partida: {data.matchId}<br />Jogador: {data.playerId}<br />Observado: {pendingReconciliation.source.observedCount} · {pendingReconciliation.source.completeness}</p>
      <p style={{ margin: "5px 0", color: "#f0883e" }}>A frase será alterada somente nesta evidência. Contagens nunca são somadas; uma colisão bloqueará a operação.</p>
      <button onClick={reconcile} disabled={reconciling} style={{ ...btnStyle, fontSize: 11 }}>{reconciling ? "Reconciliando…" : "Confirmar reconciliação"}</button>
      <button onClick={() => setPendingReconciliation(null)} disabled={reconciling} style={{ ...btnStyle, marginLeft: 6, fontSize: 11 }}>Cancelar</button>
    </div>}
    {comparison && <ObservationComparisonView comparison={comparison} />}
    <ObservationImportPanel clubId={clubId} onImported={() => { load(); }} />
  </section>;
}

type ImportPreview = {
  total: number;
  newCount: number;
  alreadyExistsCount: number;
  conflictCount: number;
  invalidCount: number;
  records: {
    index: number;
    matchId: string;
    playerId: string;
    phrase: string;
    observedCount: number;
    completeness: string;
    status: "NEW" | "ALREADY_EXISTS" | "CONFLICT" | "INVALID";
    reason: string | null;
    existingObservedCount: number | null;
    existingCompleteness: string | null;
    existingNote: string | null;
  }[];
};

type ImportResult = {
  inserted: number;
  alreadyExisted: number;
  total: number;
};

function ObservationImportPanel({ clubId, onImported }: { clubId: string; onImported: () => void }) {
  const [open, setOpen] = useState(false);
  const [json, setJson] = useState("");
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => { setPreview(null); setResult(null); setError(null); };

  const doPreview = async () => {
    reset();
    let parsed: unknown;
    try { parsed = JSON.parse(json); } catch { setError("Invalid JSON. Check syntax."); return; }
    if (!parsed || typeof parsed !== "object" || !Array.isArray((parsed as Record<string, unknown>).observations)) {
      setError('Expected format: { "observations": [ ... ] }'); return;
    }
    setLoading(true);
    try {
      const response = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observations/preview`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: json,
      });
      if (!response.ok) { const body = await response.json().catch(() => null); throw new Error(body?.message ?? `HTTP ${response.status}`); }
      setPreview(await response.json());
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Preview failed"); }
    finally { setLoading(false); }
  };

  const doImport = async () => {
    if (!preview || preview.conflictCount > 0 || preview.invalidCount > 0) return;
    setLoading(true); setError(null);
    try {
      const response = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observations/import`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: json,
      });
      if (!response.ok) { const body = await response.json().catch(() => null); throw new Error(body?.message ?? `HTTP ${response.status}`); }
      const importResult: ImportResult = await response.json();
      setResult(importResult);
      setPreview(null);
      onImported();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Import failed"); }
    finally { setLoading(false); }
  };

  if (!open) return <button onClick={() => setOpen(true)} style={{ ...btnStyle, marginTop: 8, fontSize: 11 }}>Import observations</button>;

  const canImport = preview && preview.conflictCount === 0 && preview.invalidCount === 0 && preview.newCount > 0;
  const statusColor = (status: string) => {
    switch (status) {
      case "NEW": return "#3fb950";
      case "ALREADY_EXISTS": return "#8b949e";
      case "CONFLICT": return "#f85149";
      case "INVALID": return "#f85149";
      default: return "#c9d1d9";
    }
  };

  return <div style={{ marginTop: 12, padding: 10, border: "1px solid #30363d", borderRadius: 6, background: "#161b22" }}>
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
      <h4 style={{ color: "#c9d1d9", fontSize: 13, margin: 0 }}>Import observations</h4>
      <button onClick={() => { setOpen(false); reset(); setJson(""); }} style={{ ...btnStyle, fontSize: 10 }}>Close</button>
    </div>

    {!result && <>
      <textarea
        aria-label="Observation import JSON"
        value={json}
        onChange={(event) => { setJson(event.target.value); reset(); }}
        placeholder='{ "observations": [ { "matchId": "...", "playerId": "...", "phrase": "...", "observedCount": 1 } ] }'
        style={{ ...inputStyle, width: "100%", minHeight: 120, fontFamily: "monospace", fontSize: 11, resize: "vertical" }}
      />
      <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
        <button onClick={doPreview} disabled={loading || !json.trim()} style={btnStyle}>{loading && !preview ? "Validating…" : "Validate / Preview"}</button>
        {canImport && <button onClick={doImport} disabled={loading} style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043" }}>
          {loading ? "Importing…" : `Import ${preview.newCount} observation${preview.newCount === 1 ? "" : "s"}`}
        </button>}
      </div>
    </>}

    {error && <p style={{ color: "#f85149", fontSize: 11, marginTop: 6 }}>{error}</p>}

    {preview && <div style={{ marginTop: 8 }}>
      <div style={{ display: "flex", gap: 16, fontSize: 12, color: "#c9d1d9", marginBottom: 8 }}>
        <span><strong>{preview.total}</strong> records</span>
        <span style={{ color: "#3fb950" }}>{preview.newCount} NEW</span>
        <span style={{ color: "#8b949e" }}>{preview.alreadyExistsCount} ALREADY EXISTS</span>
        <span style={{ color: preview.conflictCount > 0 ? "#f85149" : "#8b949e" }}>{preview.conflictCount} CONFLICT</span>
        <span style={{ color: preview.invalidCount > 0 ? "#f85149" : "#8b949e" }}>{preview.invalidCount} INVALID</span>
      </div>
      <div style={{ maxHeight: 300, overflowY: "auto", overflowX: "auto" }}>
        <table style={tableStyle}>
          <thead><tr>
            <th style={thStyle}>#</th>
            <th style={thStyle}>Match</th>
            <th style={thStyle}>Player</th>
            <th style={thStyle}>Phrase</th>
            <th style={thStyle}>Count</th>
            <th style={thStyle}>Completeness</th>
            <th style={thStyle}>Status</th>
            <th style={thStyle}>Reason</th>
          </tr></thead>
          <tbody>{preview.records.map((record) => <tr key={record.index} style={{ borderBottom: "1px solid #21262d" }}>
            <td style={tdStyle}>{record.index}</td>
            <td style={{ ...tdStyle, fontSize: 10 }}>{record.matchId.slice(-8)}</td>
            <td style={{ ...tdStyle, fontSize: 10 }}>{record.playerId.slice(-8)}</td>
            <td style={tdStyle}>{record.phrase}</td>
            <td style={tdStyle}>{record.observedCount}</td>
            <td style={tdStyle}>{record.completeness}</td>
            <td style={{ ...tdStyle, color: statusColor(record.status), fontWeight: 600 }}>{record.status}</td>
            <td style={{ ...tdStyle, fontSize: 10, maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis" }}>{record.reason ?? "—"}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </div>}

    {result && <div style={{ marginTop: 8, padding: 8, background: "#0d1117", borderRadius: 4 }}>
      <p style={{ color: "#3fb950", fontSize: 12, margin: 0 }}>Import complete: {result.inserted} inserted, {result.alreadyExisted} already existed ({result.total} total).</p>
      <button onClick={() => { setResult(null); setJson(""); reset(); }} style={{ ...btnStyle, marginTop: 6, fontSize: 11 }}>New import</button>
    </div>}
  </div>;
}

// ── Live Observation Collector ──────────────────────────────────────

const DRAFT_STORAGE_KEY = "fc-stats-live-collector-draft";

type CollectorPhase = "collect" | "review" | "associate" | "save";

function readDraft(): CollectorDraft | null {
  try {
    const raw = localStorage.getItem(DRAFT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || typeof parsed.clubId !== "string" || typeof parsed.phrases !== "object" || parsed.phrases === null) return null;
    // Drafts created before the opponent reminder existed remain valid.
    return {
      ...parsed,
      opponentName: typeof parsed.opponentName === "string" ? parsed.opponentName.trim() : "",
      associationDraftStartedAt: typeof parsed.associationDraftStartedAt === "string"
        ? parsed.associationDraftStartedAt
        : null,
    } as CollectorDraft;
  } catch { return null; }
}

function writeDraft(draft: CollectorDraft) {
  try {
    localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify({
      ...draft,
      opponentName: draft.opponentName.trim(),
    }));
  } catch {}
}

function clearDraft() {
  try { localStorage.removeItem(DRAFT_STORAGE_KEY); } catch {}
}

function LiveCollector({ clubId, onSaved }: {
  clubId: string;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<CollectorDraft | null>(null);
  const [phase, setPhase] = useState<CollectorPhase>("collect");
  const [historicalPalette, setHistoricalPalette] = useState<string[]>([]);
  const [phraseOrder, setPhraseOrder] = useState<string[]>([]);
  const [newPhrase, setNewPhrase] = useState("");
  const [phraseAssistNotice, setPhraseAssistNotice] = useState<string | null>(null);
  const [dismissedReviewSuggestions, setDismissedReviewSuggestions] = useState<string[]>([]);
  const [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [saveResult, setSaveResult] = useState<ImportResult | null>(null);
  const [matches, setMatches] = useState<{ matchId: string; playedAt: string; opponentName: string | null; ourScore: number; opponentScore: number }[]>([]);
  const [players, setPlayers] = useState<{ playerId: string; platformName: string | null; proName: string | null }[]>([]);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  // Load existing draft on mount
  useEffect(() => {
    const existing = readDraft();
    if (existing) {
      setDraft(existing);
      setPhraseOrder(Object.keys(existing.phrases));
    }
  }, []);

  // Load phrase palette only after this collection explicitly selects a player.
  const palettePlayerId = draft?.playerId;
  useEffect(() => {
    if (!open || !palettePlayerId || (draft && draft.clubId !== clubId)) return;
    fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/players/${encodeURIComponent(palettePlayerId)}/observation-phrases`, { cache: "no-store" })
      .then((r) => r.ok ? r.json() : [])
      .then((phrases: unknown) => {
        const nextPalette = Array.isArray(phrases)
          ? [...new Set(phrases.filter((phrase): phrase is string => typeof phrase === "string" && phrase.trim().length > 0))]
          : [];
        setHistoricalPalette(nextPalette);
        setPhraseOrder((currentOrder) => uniqueExactPhrases([...currentOrder, ...nextPalette]));

        // Only new drafts receive palette shortcuts in their persisted local draft.
        // Restored drafts retain their phrase set exactly as it was collected.
        setDraft((current) => {
          if (!current || current.clubId !== clubId || current.playerId !== palettePlayerId || current.phraseCollectionVersion !== 1) return current;
          const missing = nextPalette.filter((phrase) => !(phrase in current.phrases));
          if (missing.length === 0) return current;
          const next = {
            ...current,
            phrases: { ...current.phrases, ...Object.fromEntries(missing.map((phrase) => [phrase, 0])) },
          };
          writeDraft(next);
          return next;
        });
      })
      .catch(() => {});
  }, [open, clubId, palettePlayerId, draft?.clubId, draft?.startedAt]);

  const updateDraft = (updater: (d: CollectorDraft) => CollectorDraft) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const next = updater(prev);
      writeDraft(next);
      return next;
    });
  };

  const startNew = () => {
    const d = createLiveCollectorDraft(clubId);
    setDraft(d);
    setPhraseOrder([...DEFAULT_LIVE_FEEDBACK_PHRASES]);
    writeDraft(d);
    setPhase("collect");
    setError(null);
    setPreview(null);
    setSaveResult(null);
    setNewPhrase("");
    setPhraseAssistNotice(null);
    setDismissedReviewSuggestions([]);
    setMatches([]);
    setPlayers([]);
    setConfirmDiscard(false);
  };

  const handleOpen = () => {
    setOpen(true);
    const existing = readDraft();
    if (existing) {
      setDraft(existing);
      setPhraseOrder(Object.keys(existing.phrases));
      setPhase("collect");
    }
  };

  const increment = (phrase: string) => {
    setPhraseOrder((currentOrder) => uniqueExactPhrases([...currentOrder, phrase]));
    updateDraft((d) => ({ ...d, phrases: { ...d.phrases, [phrase]: (d.phrases[phrase] ?? 0) + 1 } }));
  };

  const decrement = (phrase: string) => {
    updateDraft((d) => {
      const current = d.phrases[phrase] ?? 0;
      return { ...d, phrases: { ...d.phrases, [phrase]: Math.max(0, current - 1) } };
    });
  };

  const addPhraseAsNew = () => {
    const trimmed = newPhrase.trim();
    if (!trimmed) return;
    if (draft && !(trimmed in draft.phrases)) {
      setPhraseOrder((currentOrder) => uniqueExactPhrases([...currentOrder, trimmed]));
      updateDraft((d) => ({ ...d, phrases: { ...d.phrases, [trimmed]: 0 } }));
    }
    setNewPhrase("");
    setPhraseAssistNotice(null);
  };

  const totalCount = draft ? Object.values(draft.phrases).reduce((s, c) => s + c, 0) : 0;
  const activeCount = draft ? Object.values(draft.phrases).filter((c) => c > 0).length : 0;

  const knownPhrases = useMemo(
    () => uniqueExactPhrases([...DEFAULT_LIVE_FEEDBACK_PHRASES, ...historicalPalette]),
    [historicalPalette],
  );
  const phraseCandidates = useMemo(
    () => uniqueExactPhrases([...knownPhrases, ...Object.keys(draft?.phrases ?? {})]),
    [draft?.phrases, knownPhrases],
  );
  const phraseSuggestions = useMemo(
    () => findLiveFeedbackSuggestions(newPhrase, phraseCandidates.filter((phrase) => phrase !== newPhrase.trim())),
    [newPhrase, phraseCandidates],
  );

  const useSuggestedPhrase = (phrase: string) => {
    setPhraseOrder((currentOrder) => uniqueExactPhrases([...currentOrder, phrase]));
    updateDraft((current) => ({
      ...current,
      phrases: { ...current.phrases, [phrase]: (current.phrases[phrase] ?? 0) + 1 },
    }));
    setNewPhrase("");
    setPhraseAssistNotice(null);
  };

  const addPhrase = () => {
    if (!newPhrase.trim()) return;
    if (phraseSuggestions.length > 0) {
      setPhraseAssistNotice("Escolha uma sugestão ou mantenha a frase como nova.");
      return;
    }
    addPhraseAsNew();
  };

  // The separate order state includes zero-count palette shortcuts. Sorting is presentation-only,
  // so counts and persisted evidence never affect a phrase's visual position.
  const orderedPhrases = draft
    ? sortLiveFeedbackPhrases(phraseOrder.filter((phrase) => phraseCandidates.includes(phrase)))
    : [];
  const normalizedFilter = normalizeLiveFeedbackPhrase(filter);
  const filteredPhrases = normalizedFilter
    ? orderedPhrases.filter((phrase) => normalizeLiveFeedbackPhrase(phrase).includes(normalizedFilter))
    : orderedPhrases;

  const reviewSuggestions = useMemo(() => {
    if (!draft) return [];
    return Object.entries(draft.phrases)
      .filter(([phrase, count]) => count > 0 && !knownPhrases.includes(phrase) && !dismissedReviewSuggestions.includes(phrase))
      .flatMap(([phrase]) => {
        const suggestion = findLiveFeedbackSuggestions(phrase, knownPhrases)[0];
        return suggestion && suggestion.kind !== "PREFIX" ? [{ phrase, suggestedPhrase: suggestion.phrase }] : [];
      });
  }, [draft, knownPhrases, dismissedReviewSuggestions]);

  const mergeIntoKnownPhrase = (sourcePhrase: string, targetPhrase: string) => {
    setPhraseOrder((currentOrder) => {
      const withoutSource = currentOrder.filter((phrase) => phrase !== sourcePhrase);
      return uniqueExactPhrases([...withoutSource, targetPhrase]);
    });
    updateDraft((current) => {
      const sourceCount = current.phrases[sourcePhrase] ?? 0;
      const { [sourcePhrase]: _, ...withoutSource } = current.phrases;
      return {
        ...current,
        phrases: { ...withoutSource, [targetPhrase]: (withoutSource[targetPhrase] ?? 0) + sourceCount },
      };
    });
  };

  // Association: load recent matches
  const loadMatches = async () => {
    setLoading(true);
    try {
      const data = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches?limit=20`, { cache: "no-store" }).then((r) => r.json());
      setMatches(data);
      setPhase("associate");
    } catch { setError("Failed to load matches"); }
    finally { setLoading(false); }
  };

  const selectMatch = async (mid: string) => {
    setLoading(true);
    setError(null);
    setPreview(null);
    setPlayers([]);
    updateDraft((d) => selectLiveCollectorMatch(d, mid));
    try {
      const data = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/matches/${encodeURIComponent(mid)}/players`, { cache: "no-store" }).then((r) => r.json());
      setPlayers(data);
    } catch { setError("Failed to load players"); }
    finally { setLoading(false); }
  };

  const selectPlayer = (pid: string, name: string | null) => {
    setPreview(null);
    updateDraft((d) => selectLiveCollectorPlayer(d, pid, name));
  };

  const beginAssociation = (message?: string) => {
    setPreview(null);
    setSaveResult(null);
    setError(message ?? null);
    setPlayers([]);
    setMatches([]);
    updateDraft(clearLiveCollectorAssociation);
    void loadMatches();
  };

  // Save flow: preview then import
  const doPreview = async () => {
    if (!draft) return;
    if (!hasCurrentLiveCollectorAssociation(draft)) {
      beginAssociation("Associe a partida e o jogador desta coleta antes de validar.");
      return;
    }
    const observations = buildLiveCollectorObservationInputs(draft);
    if (observations.length === 0) { setError("No observations with count > 0"); return; }
    setLoading(true); setError(null);
    try {
      const response = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observations/preview`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ observations }),
      });
      if (!response.ok) { const body = await response.json().catch(() => null); throw new Error(body?.message ?? `HTTP ${response.status}`); }
      setPreview(await response.json());
      setPhase("save");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Preview failed"); }
    finally { setLoading(false); }
  };

  const doImport = async () => {
    if (!draft || !preview) return;
    if (!hasCurrentLiveCollectorAssociation(draft)) {
      beginAssociation("Associe a partida e o jogador desta coleta antes de salvar.");
      return;
    }
    if (preview.conflictCount > 0 || preview.invalidCount > 0) return;
    const observations = buildLiveCollectorObservationInputs(draft);
    setLoading(true); setError(null);
    try {
      const response = await fetch(`/api/admin/explorer/clubs/${encodeURIComponent(clubId)}/observations/import`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ observations }),
      });
      if (!response.ok) { const body = await response.json().catch(() => null); throw new Error(body?.message ?? `HTTP ${response.status}`); }
      const result: ImportResult = await response.json();
      setSaveResult(result);
      clearDraft();
      setDraft(null);
      onSaved();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Import failed"); }
    finally { setLoading(false); }
  };

  if (!open) {
    const existing = readDraft();
    return <button onClick={handleOpen} style={{ ...btnStyle, marginTop: 8, fontSize: 11 }}>
      {existing ? `Coletar ao vivo (draft ativo)` : "Coletar ao vivo"}
    </button>;
  }

  // Success state
  if (saveResult) return <div style={{ marginTop: 12, padding: 16, border: "1px solid #238636", borderRadius: 8, background: "#0d1117" }}>
    <p style={{ color: "#3fb950", fontSize: 14, margin: 0, fontWeight: 600 }}>Coleta salva com sucesso</p>
    <p style={{ color: "#8b949e", fontSize: 12, margin: "8px 0" }}>{saveResult.inserted} inseridas, {saveResult.alreadyExisted} já existiam ({saveResult.total} total).</p>
    <button onClick={() => { setSaveResult(null); setOpen(false); }} style={btnStyle}>Fechar</button>
  </div>;

  // Existing draft from another context
  if (draft && draft.clubId !== clubId) {
    return <div style={{ marginTop: 12, padding: 16, border: "1px solid #f0883e", borderRadius: 8, background: "#161b22" }}>
      <p style={{ color: "#f0883e", fontSize: 13 }}>Existe um rascunho ativo de outro contexto (club: {draft.clubId}).</p>
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <button onClick={() => { setPhase("collect"); }} style={btnStyle}>Continuar rascunho existente</button>
        <button onClick={() => { if (confirmDiscard) { clearDraft(); setDraft(null); setConfirmDiscard(false); startNew(); } else setConfirmDiscard(true); }}
          style={{ ...btnStyle, borderColor: confirmDiscard ? "#f85149" : undefined }}>
          {confirmDiscard ? "Confirmar descarte" : "Descartar e começar novo"}
        </button>
      </div>
    </div>;
  }

  // No draft — start new or resume
  if (!draft) {
    return <div style={{ marginTop: 12, padding: 16, border: "1px solid #30363d", borderRadius: 8, background: "#161b22" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h4 style={{ color: "#c9d1d9", fontSize: 14, margin: 0 }}>Coletar ao vivo</h4>
        <button onClick={() => setOpen(false)} style={{ ...btnStyle, fontSize: 10 }}>Fechar</button>
      </div>
      <p style={{ color: "#8b949e", fontSize: 12, margin: "8px 0" }}>Conte feedbacks da EA em tempo real enquanto joga.</p>
      <button onClick={startNew} style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043", fontSize: 14, padding: "12px 24px" }}>Iniciar coleta</button>
    </div>;
  }

  const hasCurrentAssociation = hasCurrentLiveCollectorAssociation(draft);
  const canSave = hasCurrentAssociation && activeCount > 0;

  // SAVE phase
  if (phase === "save" && preview) {
    const canImport = hasCurrentAssociation && preview.conflictCount === 0 && preview.invalidCount === 0 && preview.newCount > 0;
    return <div style={{ marginTop: 12, padding: 16, border: "1px solid #30363d", borderRadius: 8, background: "#161b22" }}>
      <h4 style={{ color: "#c9d1d9", fontSize: 14, margin: "0 0 8px" }}>Revisão do import</h4>
      <div style={{ display: "flex", gap: 16, fontSize: 13, color: "#c9d1d9", marginBottom: 8, flexWrap: "wrap" }}>
        <span style={{ color: "#3fb950" }}>{preview.newCount} NEW</span>
        <span style={{ color: "#8b949e" }}>{preview.alreadyExistsCount} ALREADY EXISTS</span>
        <span style={{ color: preview.conflictCount > 0 ? "#f85149" : "#8b949e" }}>{preview.conflictCount} CONFLICT</span>
        <span style={{ color: preview.invalidCount > 0 ? "#f85149" : "#8b949e" }}>{preview.invalidCount} INVALID</span>
      </div>
      {preview.records.filter((r) => r.status === "CONFLICT" || r.status === "INVALID").map((r) => (
        <p key={r.index} style={{ color: "#f85149", fontSize: 11, margin: 2 }}>{r.phrase}: {r.reason}</p>
      ))}
      {!hasCurrentAssociation && <p style={{ color: "#f0883e", fontSize: 12, marginTop: 4 }}>A associação desta coleta precisa ser confirmada antes de salvar.</p>}
      {error && <p style={{ color: "#f85149", fontSize: 12, marginTop: 4 }}>{error}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button onClick={() => setPhase("review")} style={btnStyle}>← Voltar</button>
        <button onClick={() => beginAssociation()} style={btnStyle}>Trocar associação</button>
        {canImport && <button onClick={doImport} disabled={loading} style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043", fontSize: 14, padding: "10px 20px" }}>
          {loading ? "Salvando…" : `Salvar ${preview.newCount} observação${preview.newCount === 1 ? "" : "ões"}`}
        </button>}
      </div>
    </div>;
  }

  // ASSOCIATE phase
  if (phase === "associate") {
    return <div style={{ marginTop: 12, padding: 16, border: "1px solid #30363d", borderRadius: 8, background: "#161b22" }}>
      <h4 style={{ color: "#c9d1d9", fontSize: 14, margin: "0 0 8px" }}>Associar partida e jogador</h4>
      <p style={{ color: "#8b949e", fontSize: 12 }}>Selecione a partida canônica e o jogador para esta coleta.</p>
      {draft.opponentName && <p style={{ color: "#8b949e", fontSize: 12, margin: "0 0 8px" }}>Coleta: vs. {draft.opponentName}</p>}
      {error && <p style={{ color: "#f85149", fontSize: 12 }}>{error}</p>}

      <h5 style={{ color: "#c9d1d9", fontSize: 13, margin: "12px 0 6px" }}>Partida</h5>
      {matches.length === 0 && <p style={{ color: "#8b949e", fontSize: 12 }}>Carregando…</p>}
      <div style={{ maxHeight: 250, overflowY: "auto" }}>
        {matches.map((m) => (
          <button key={m.matchId} onClick={() => selectMatch(m.matchId)} disabled={loading}
            style={{ ...btnStyle, display: "block", width: "100%", textAlign: "left", marginBottom: 4, padding: "10px 12px", fontSize: 13,
              background: draft.matchId === m.matchId ? "#1f6feb33" : undefined, borderColor: draft.matchId === m.matchId ? "#1f6feb" : undefined }}>
            {new Date(m.playedAt).toLocaleDateString("pt-BR")} vs {m.opponentName ?? "?"} ({m.ourScore}×{m.opponentScore})
          </button>
        ))}
      </div>

      {draft.matchId && players.length > 0 && <>
        <h5 style={{ color: "#c9d1d9", fontSize: 13, margin: "12px 0 6px" }}>Jogador</h5>
        <div style={{ maxHeight: 200, overflowY: "auto" }}>
          {players.map((p) => (
            <button key={p.playerId} onClick={() => selectPlayer(p.playerId, p.platformName ?? p.proName)} disabled={loading}
              style={{ ...btnStyle, display: "block", width: "100%", textAlign: "left", marginBottom: 4, padding: "10px 12px", fontSize: 13,
                background: draft.playerId === p.playerId ? "#1f6feb33" : undefined, borderColor: draft.playerId === p.playerId ? "#1f6feb" : undefined }}>
              {p.platformName ?? p.proName ?? p.playerId}
            </button>
          ))}
        </div>
      </>}

      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <button onClick={() => setPhase("review")} style={btnStyle}>← Voltar</button>
        {hasCurrentAssociation && <button onClick={doPreview} disabled={loading || activeCount === 0}
          style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043", fontSize: 14, padding: "10px 20px" }}>
          {loading ? "Validando…" : "Validar e salvar"}
        </button>}
      </div>
    </div>;
  }

  // REVIEW phase
  if (phase === "review") {
    const entries = Object.entries(draft.phrases).filter(([, c]) => c > 0).sort((a, b) => b[1] - a[1]);
    return <div style={{ marginTop: 12, padding: 16, border: "1px solid #30363d", borderRadius: 8, background: "#161b22" }}>
      <h4 style={{ color: "#c9d1d9", fontSize: 14, margin: "0 0 8px" }}>Revisão da coleta</h4>
      <p style={{ color: "#8b949e", fontSize: 12, margin: "0 0 8px" }}>{totalCount} feedback{totalCount !== 1 ? "s" : ""} · {activeCount} frase{activeCount !== 1 ? "s" : ""}</p>
      {draft.opponentName && <p style={{ color: "#8b949e", fontSize: 12, margin: "0 0 8px" }}>Coleta: vs. {draft.opponentName}</p>}
      {hasCurrentAssociation && <p style={{ color: "#8b949e", fontSize: 11 }}>Partida: {draft.matchId.slice(-8)} · Jogador: {draft.playerName ?? draft.playerId?.slice(-8)}</p>}
      {!hasCurrentAssociation && (draft.matchId || draft.playerId) && <p style={{ color: "#f0883e", fontSize: 11 }}>A associação anterior precisa ser confirmada novamente para esta coleta.</p>}

      {reviewSuggestions.map(({ phrase, suggestedPhrase }) => (
        <div key={phrase} className="live-collector-review-suggestion">
          <strong>Possível frase já conhecida</strong>
          <span>“{phrase}” → “{suggestedPhrase}”</span>
          <div>
            <button onClick={() => mergeIntoKnownPhrase(phrase, suggestedPhrase)} style={{ ...btnStyle, fontSize: 11, padding: "5px 8px" }}>Usar “{suggestedPhrase}”</button>
            <button onClick={() => setDismissedReviewSuggestions((current) => [...current, phrase])} style={{ ...btnStyle, fontSize: 11, padding: "5px 8px" }}>Manter como está</button>
          </div>
        </div>
      ))}

      {entries.map(([phrase, count]) => (
        <div key={phrase} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderBottom: "1px solid #21262d" }}>
          <span style={{ color: "#c9d1d9", fontSize: 14 }}>{phrase}</span>
          <span style={{ color: "#3fb950", fontSize: 16, fontWeight: 700, minWidth: 30, textAlign: "right" }}>{count}</span>
        </div>
      ))}

      <div style={{ marginTop: 12 }}>
        <label style={{ color: "#c9d1d9", fontSize: 12, display: "block", marginBottom: 4 }}>Completude</label>
        <select aria-label="Completude da coleta" value={draft.completeness} onChange={(e) => updateDraft((d) => ({ ...d, completeness: e.target.value as "AT_LEAST" | "EXACT" }))}
          style={{ ...selectStyle, fontSize: 14, padding: "8px 12px", width: "100%" }}>
          <option value="AT_LEAST">AT_LEAST — Alguns feedbacks podem ter sido perdidos</option>
          <option value="EXACT">EXACT — Todos os feedbacks foram observados</option>
        </select>
      </div>

      {error && <p style={{ color: "#f85149", fontSize: 12, marginTop: 4 }}>{error}</p>}

      <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
        <button onClick={() => setPhase("collect")} style={btnStyle}>← Editar</button>
        {canSave ? (
          <>
            <button onClick={doPreview} disabled={loading} style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043", fontSize: 14, padding: "10px 20px" }}>
              {loading ? "Validando…" : "Validar e salvar"}
            </button>
            <button onClick={() => beginAssociation()} disabled={loading} style={btnStyle}>Trocar associação</button>
          </>
        ) : (
          <button onClick={() => beginAssociation()} disabled={loading} style={{ ...btnStyle, fontSize: 14, padding: "10px 20px" }}>
            {loading ? "Carregando…" : "Associar partida"}
          </button>
        )}
        <button onClick={() => { if (confirmDiscard) { clearDraft(); setDraft(null); setConfirmDiscard(false); setPhase("collect"); } else setConfirmDiscard(true); }}
          style={{ ...btnStyle, borderColor: confirmDiscard ? "#f85149" : undefined, fontSize: 12 }}>
          {confirmDiscard ? "Confirmar descarte" : "Descartar"}
        </button>
      </div>
    </div>;
  }

  // COLLECT phase (main mobile-friendly counting UI)
  return <div style={{ marginTop: 12, padding: 12, border: "1px solid #30363d", borderRadius: 8, background: "#161b22" }}>
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
      <div>
        <h4 style={{ color: "#c9d1d9", fontSize: 14, margin: 0 }}>COLETANDO AO VIVO</h4>
        {draft.opponentName && <p style={{ color: "#8b949e", fontSize: 12, margin: "2px 0 0" }}>vs. {draft.opponentName}</p>}
      </div>
      <button onClick={() => setOpen(false)} style={{ ...btnStyle, fontSize: 10 }}>Minimizar</button>
    </div>

    <label className="live-collector-opponent">
      <span>Adversário (lembrete local)</span>
      <input aria-label="Adversário da partida" value={draft.opponentName}
        onChange={(event) => updateDraft((current) => ({ ...current, opponentName: event.target.value.trim() }))}
        placeholder="Ex.: Esporte Fino" style={{ ...inputStyle, fontSize: 13, padding: "8px 10px" }} />
    </label>

    <div className="live-collector-toolbar">
      <div className="live-collector-summary" aria-label="Resumo da coleta">
        <span><strong>{totalCount}</strong> feedback{totalCount !== 1 ? "s" : ""}</span>
        <span><strong>{activeCount}</strong> frase{activeCount !== 1 ? "s" : ""}</span>
      </div>
      <input className="live-collector-filter" aria-label="Filtrar frases" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filtrar frases…"
        style={{ ...inputStyle, fontSize: 13, padding: "8px 10px" }} />
      <div className="live-collector-new-phrase">
        <input aria-label="Nova frase de feedback" value={newPhrase} onChange={(e) => { setNewPhrase(e.target.value); setPhraseAssistNotice(null); }}
          onKeyDown={(e) => { if (e.key === "Enter") addPhrase(); }}
          placeholder="Nova frase EA…" style={{ ...inputStyle, minWidth: 0, flex: 1, fontSize: 13, padding: "8px 10px" }} />
        <button onClick={phraseSuggestions.length > 0 ? addPhraseAsNew : addPhrase} disabled={!newPhrase.trim()} style={{ ...btnStyle, fontSize: 13, padding: "8px 12px", whiteSpace: "nowrap" }}>
          {phraseSuggestions.length > 0 ? "Manter como nova" : "+ Frase"}
        </button>
      </div>
    </div>

    {newPhrase.trim() && phraseSuggestions.length > 0 && <div className="live-collector-suggestions" role="status">
      <strong>Você quis dizer?</strong>
      {phraseSuggestions.map((suggestion) => (
        <button key={suggestion.phrase} onClick={() => useSuggestedPhrase(suggestion.phrase)} style={{ ...btnStyle, fontSize: 12, padding: "5px 8px" }}>
          Usar “{suggestion.phrase}”
        </button>
      ))}
    </div>}

    {phraseAssistNotice && <p style={{ color: "#8b949e", fontSize: 12, margin: "0 0 8px" }}>{phraseAssistNotice}</p>}

    {/* Phrase counters */}
    <div className="live-collector-phrase-grid">
      {filteredPhrases.map((phrase) => {
        const count = draft.phrases[phrase] ?? 0;
        return <div key={phrase} className="live-collector-phrase-item">
          <button aria-label={`Decrementar ${phrase}`} onClick={() => decrement(phrase)} disabled={count === 0}
            className="live-collector-phrase-action" style={{ ...btnStyle, opacity: count === 0 ? 0.3 : 1 }}>−</button>
          <span className="live-collector-phrase-count" style={{ color: count > 0 ? "#3fb950" : "#484f58" }}>{count}</span>
          <button aria-label={`Incrementar ${phrase}`} onClick={() => increment(phrase)}
            className="live-collector-phrase-action" style={{ ...btnStyle, background: "#238636", borderColor: "#2ea043" }}>+</button>
          <span className="live-collector-phrase-label" style={{ color: count > 0 ? "#c9d1d9" : "#8b949e" }}>{phrase}</span>
        </div>;
      })}
    </div>

    {error && <p style={{ color: "#f85149", fontSize: 12, marginTop: 6 }}>{error}</p>}

    <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
      <button onClick={() => { setPhase("review"); setFilter(""); }} disabled={activeCount === 0}
        style={{ ...btnStyle, background: activeCount > 0 ? "#1f6feb" : undefined, borderColor: activeCount > 0 ? "#388bfd" : undefined, fontSize: 14, padding: "12px 20px", flex: 1 }}>
        Finalizar coleta
      </button>
    </div>
  </div>;
}

export function ObservationComparisonView({ comparison }: { comparison: ObservationComparison }) {
  const [showContradicted, setShowContradicted] = useState(false);
  const [expandedEvidence, setExpandedEvidence] = useState<string | null>(null);
  const keyFor = (candidate: ObservationCandidate) => `${candidate.aggregateIndex}-${candidate.code}`;
  const unknown = comparison.candidates.filter((candidate) => candidate.candidateKind === "UNKNOWN_CANDIDATE");
  const discoveries = unknown.filter((candidate) => candidate.investigationStatus === "HIGH_PRIORITY" || candidate.investigationStatus === "SURVIVES").slice(0, 12);
  const controls = comparison.candidates.filter((candidate) => candidate.candidateKind === "KNOWN_CONTROL" && candidate.investigationStatus !== "CONTRADICTED");
  const contradicted = comparison.candidates.filter((candidate) => candidate.investigationStatus === "CONTRADICTED");
  const renderCandidate = (candidate: ObservationCandidate) => {
    const key = keyFor(candidate);
    const expanded = expandedEvidence === key;
    return <div key={key} style={{ borderTop: "1px solid #30363d", padding: "10px 0" }}>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8, flexWrap: "wrap" }}>
        <strong style={{ fontSize: 12 }}>agg{candidate.aggregateIndex}[{candidate.code}]</strong>
        <span style={{ fontSize: 10, color: candidate.candidateKind === "KNOWN_CONTROL" ? "#79c0ff" : "#f0c674" }}>{candidate.candidateKind}</span>
        <span style={{ fontSize: 10, color: "#8b949e" }}>{candidate.investigationStatus}</span>
        {candidate.metricName && <span style={{ fontSize: 10, color: "#8b949e" }}>{candidate.metricName}</span>}
      </div>
      <p style={{ color: "#8b949e", fontSize: 11, margin: "5px 0" }}>
        Exatas: {candidate.aggregateEqualObserved} · Compatíveis: {candidate.atLeastCompatibleCases} · Contradições: {candidate.contradictions} · Excesso: +{candidate.totalExcess}
      </p>
      {candidate.candidateCollisions.length > 0 && <p style={{ color: "#f0883e", fontSize: 11, margin: "5px 0" }}>
        Candidate collision: {candidate.candidateCollisions.map((collision) => `agg${collision.aggregateIndex}[${collision.code}]${collision.metricName ? ` (${collision.metricName})` : ""}`).join(", ")}
      </p>}
      <button onClick={() => setExpandedEvidence(expanded ? null : key)} style={{ ...btnStyle, fontSize: 11, padding: "2px 8px" }}>{expanded ? "Hide evidence" : "View evidence"}</button>
      {expanded && <div style={{ overflowX: "auto", marginTop: 8 }}><table style={tableStyle}><thead><tr><th style={thStyle}>Match</th><th style={thStyle}>Observed</th><th style={thStyle}>Aggregate</th><th style={thStyle}>Result</th></tr></thead><tbody>{candidate.evidence.map((evidence) => <tr key={evidence.matchId} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{evidence.opponentName ?? evidence.matchId}</td><td style={tdStyle}>{evidence.completeness === "AT_LEAST" ? "≥ " : ""}{evidence.observedCount}</td><td style={tdStyle}>{evidence.aggregateValue}</td><td style={tdStyle}>{evidence.comparison}</td></tr>)}</tbody></table></div>}
    </div>;
  };

  return <div style={{ marginTop: 10 }}>
    <strong style={{ fontSize: 12 }}>Observational candidate analysis: {comparison.phrase}</strong>
    <p style={{ color: "#f0883e", fontSize: 11 }}>Observational compatibility does not confirm the sporting meaning of a code.</p>
    <p style={{ color: "#8b949e", fontSize: 11 }}>{comparison.annotatedMatches} observed matches · {comparison.excludedRawUnavailable} RAW aggregate slots unavailable · {comparison.contradictedCandidates} contradicted</p>
    {comparison.observationCollisions.length > 0 && <p style={{ color: "#f0883e", fontSize: 11 }}>Observation collision: {comparison.observationCollisions.map((collision) => collision.phrase).join(", ")}</p>}
    {comparison.nextBestExperiments.length > 0 && <ul style={{ color: "#8b949e", fontSize: 11, margin: "8px 0", paddingLeft: 18 }}>{comparison.nextBestExperiments.map((recommendation) => <li key={recommendation}>{recommendation}</li>)}</ul>}
    <div style={{ marginTop: 8 }}>
      <strong style={{ fontSize: 11 }}>Discovery candidates</strong>
      {discoveries.length === 0 ? <p style={{ color: "#8b949e", fontSize: 11 }}>No UNKNOWN candidate survives with enough evidence yet.</p> : discoveries.map(renderCandidate)}
    </div>
    {controls.length > 0 && <div style={{ marginTop: 10 }}><strong style={{ fontSize: 11 }}>Known controls</strong>{controls.map(renderCandidate)}</div>}
    {contradicted.length > 0 && <div style={{ marginTop: 10 }}><button onClick={() => setShowContradicted(!showContradicted)} style={{ ...btnStyle, fontSize: 11 }}>{showContradicted ? "Hide contradicted" : `Show ${contradicted.length} contradicted`}</button>{showContradicted && contradicted.map(renderCandidate)}</div>}
  </div>;
}

function CompareView({
  data,
  showZeros,
  onToggleZeros,
  onBack,
}: {
  data: PlayerExplorerData[];
  showZeros: boolean;
  onToggleZeros: () => void;
  onBack: () => void;
}) {
  const allCodes = new Map<string, { aggregate: number; code: number; confidence: string; metricName: string | null }>();
  for (const d of data) {
    for (const e of d.aggregateEntries) {
      const key = `${e.aggregate}-${e.code}`;
      if (!allCodes.has(key)) allCodes.set(key, { aggregate: e.aggregate, code: e.code, confidence: e.confidence, metricName: e.metricName });
    }
  }
  let codes = Array.from(allCodes.values()).sort((a, b) => a.aggregate - b.aggregate || a.code - b.code);

  if (!showZeros) {
    codes = codes.filter((c) => {
      const key = `${c.aggregate}-${c.code}`;
      return data.some((d) => d.aggregateEntries.some((e) => `${e.aggregate}-${e.code}` === key && e.value !== 0));
    });
  }

  const valueFor = (d: PlayerExplorerData, agg: number, code: number) => {
    const e = d.aggregateEntries.find((e) => e.aggregate === agg && e.code === code);
    return e?.value ?? null;
  };

  return (
    <div>
      <button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to matches</button>
      <h2 style={h2Style}>Multi-Match Comparison — {data[0]?.platformName ?? data[0]?.playerId}</h2>
      <p style={{ fontSize: 11, color: "#f0883e", marginBottom: 8 }}>
        Aggregates are shown separately per slot. Values from different aggregates must NOT be summed for unknown codes.
      </p>
      <label style={{ fontSize: 12, color: "#8b949e", cursor: "pointer", marginBottom: 8, display: "block" }}>
        <input type="checkbox" checked={showZeros} onChange={onToggleZeros} style={{ marginRight: 4 }} />
        Show zeros
      </label>
      <div style={{ overflowX: "auto" }}>
        <table style={tableStyle}>
          <thead>
            <tr>
              <th style={thStyle}>Agg</th>
              <th style={thStyle}>Code</th>
              <th style={thStyle}>Status</th>
              <th style={thStyle}>Mapping</th>
              {data.map((d) => (
                <th key={d.matchId} style={{ ...thStyle, fontSize: 11 }}>
                  vs {d.opponentName ?? "?"}<br />{new Date(d.playedAt).toLocaleDateString("pt-BR")}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {codes.map((c) => (
              <tr key={`${c.aggregate}-${c.code}`} style={{ borderBottom: "1px solid #21262d", background: confidenceBg(c.confidence) }}>
                <td style={tdStyle}>{c.aggregate}</td>
                <td style={tdStyle}>{c.code}</td>
                <td style={tdStyle}><ConfidenceBadge confidence={c.confidence} /></td>
                <td style={tdStyle}>{c.metricName ?? "—"}</td>
                {data.map((d) => {
                  const val = valueFor(d, c.aggregate, c.code);
                  return (
                    <td key={d.matchId} style={{ ...tdStyle, fontWeight: 600, textAlign: "right" }}>
                      {val === null ? <span style={{ color: "#484f58" }}>n/a</span> : val}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function DiscoveryView({
  data, aggregate, minimumMatches, minimumObservations, hideKnownRelationships, confidence, evidence, loading, selectedCode, selectedRelation,
  onAggregate, onMinimumMatches, onMinimumObservations, onHideKnownRelationships, onConfidence, onEvidence, onRun, onSelectCode, onSelectRelation, onBack,
}: {
  data: DiscoveryData | null; aggregate: string; minimumMatches: string; minimumObservations: string; hideKnownRelationships: boolean;
  confidence: string; evidence: string;
  loading: boolean; selectedCode: CodeInventory | null; selectedRelation: DiscoveryRelation | null;
  onAggregate: (value: string) => void; onMinimumMatches: (value: string) => void; onMinimumObservations: (value: string) => void;
  onHideKnownRelationships: (value: boolean) => void; onConfidence: (value: string) => void; onEvidence: (value: string) => void; onRun: () => void; onSelectCode: (value: CodeInventory | null) => void;
  onSelectRelation: (value: DiscoveryRelation | null) => void; onBack: () => void;
}) {
  const analysis = data?.analysis;
  const visibleCodes = analysis?.inventory.filter((code) =>
    confidence === "ALL" || (confidence === "KNOWN" ? ["CONFIRMED", "HIGH_CONFIDENCE"].includes(code.confidence) : code.confidence === confidence),
  ) ?? [];
  const visibleRefs = new Set(visibleCodes.map((code) => `${code.aggregateIndex}-${code.code}`));
  const visibleRelation = (relation: DiscoveryRelation) =>
    (evidence === "ALL" || relation.evidenceTier === evidence) &&
    [relation.codeA, relation.codeB, relation.codeC]
      .filter((code): code is number => code !== null)
      .every((code) => visibleRefs.has(`${relation.aggregateIndex}-${code}`));
  const visibleSignals = analysis?.topDiscoverySignals.filter((signal) => evidence === "ALL" || signal.tier === evidence) ?? [];
  const visibleCorrelations = analysis?.correlations.filter((correlation) =>
    visibleRefs.has(`${correlation.aggregateIndex}-${correlation.codeA}`) && visibleRefs.has(`${correlation.aggregateIndex}-${correlation.codeB}`),
  ) ?? [];
  return (
    <div>
      <button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to matches</button>
      <h2 style={h2Style}>Discovery</h2>
      <p style={{ color: "#f0883e", fontSize: 12, marginBottom: 12 }}>
        Mathematical observations only. Correlation and repeated patterns are not sporting mappings.
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginBottom: 16 }}>
        <label style={filterLabel}>Aggregate <select value={aggregate} onChange={(e) => onAggregate(e.target.value)} style={selectStyle}><option value="all">ALL</option><option value="0">0</option><option value="1">1</option></select></label>
        <label style={filterLabel}>Minimum matches <input type="number" min="0" value={minimumMatches} onChange={(e) => onMinimumMatches(e.target.value)} style={numberInputStyle} /></label>
        <label style={filterLabel}>Minimum observations <input type="number" min="0" value={minimumObservations} onChange={(e) => onMinimumObservations(e.target.value)} style={numberInputStyle} /></label>
        <label style={filterLabel}>Confidence <select value={confidence} onChange={(e) => onConfidence(e.target.value)} style={selectStyle}><option value="ALL">ALL</option><option value="UNKNOWN">UNKNOWN</option><option value="HYPOTHESIS">HYPOTHESIS</option><option value="KNOWN">KNOWN</option></select></label>
        <label style={filterLabel}>Evidence <select value={evidence} onChange={(e) => onEvidence(e.target.value)} style={selectStyle}><option value="ALL">ALL</option><option value="CANDIDATE">CANDIDATE</option><option value="STRONG_CANDIDATE">STRONG_CANDIDATE</option></select></label>
        <label style={filterLabel}><input type="checkbox" checked={hideKnownRelationships} onChange={(e) => onHideKnownRelationships(e.target.checked)} /> Hide relationships explained by known metrics</label>
        <button onClick={onRun} disabled={loading} style={btnStyle}>{loading ? "Analyzing..." : "Run discovery"}</button>
      </div>
      {!analysis ? <p style={{ color: "#8b949e", fontSize: 13 }}>Run a bounded analysis of the latest RAW matches.</p> : <>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 6, marginBottom: 16 }}>
          <StatChip label="RAW matches analyzed" value={analysis.rawMatchesAnalyzed} />
          <StatChip label="Player-match observations" value={analysis.playerMatchObservations} />
          <StatChip label="Aggregate 0 codes" value={analysis.aggregate0CodeCount} />
          <StatChip label="Aggregate 1 codes" value={analysis.aggregate1CodeCount} />
          <StatChip label="Unknown codes" value={analysis.unknownCodeCount} />
          <StatChip label="Known codes" value={analysis.knownCodeCount} />
          <StatChip label="Hypothesis codes" value={analysis.hypothesisCodeCount} warn />
        </div>
        {data.newAggregateDataDetected.length > 0 && <div style={{ background: "#3d2e00", border: "1px solid #d29922", padding: 10, borderRadius: 6, marginBottom: 16, color: "#f0c674", fontSize: 12 }}>
          <strong>NEW AGGREGATE DATA DETECTED</strong>{" — "}{data.newAggregateDataDetected.map((a) => `${a.fieldName}: ${a.playerCount} players / ${a.matchCount} matches`).join("; ")}. No automatic parsing or mapping was applied.
        </div>}
            <h3 style={h3Style}>Top Discovery Candidates</h3>
            {visibleSignals.length === 0 ? <p style={{ color: "#8b949e", fontSize: 12 }}>No candidate meets the current evidence and confidence filters.</p> : <TopDiscoverySignalsTable signals={visibleSignals} relations={analysis?.relations ?? []} onSelectRelation={onSelectRelation} />}
        <h3 style={{ ...h3Style, marginTop: 20 }}>Code Inventory</h3>
        {visibleCodes.length === 0 ? <p style={{ color: "#8b949e", fontSize: 12 }}>No RAW aggregate coverage matches the current filters.</p> :
          <div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Code</th><th style={thStyle}>Status</th><th style={thStyle}>Technical class</th><th style={thStyle}>Obs</th><th style={thStyle}>Matches</th><th style={thStyle}>Non-zero</th><th style={thStyle}>Prevalence</th><th style={thStyle}>Range</th><th style={thStyle}></th></tr></thead><tbody>
            {visibleCodes.map((code) => <tr key={`${code.aggregateIndex}-${code.code}`} style={{ borderBottom: "1px solid #21262d", background: confidenceBg(code.confidence) }}><td style={tdStyle}>{code.aggregateIndex}</td><td style={tdStyle}>{code.code}</td><td style={tdStyle}><ConfidenceBadge confidence={code.confidence} /></td><td style={tdStyle}>{code.technicalClassification}</td><td style={tdStyle}>{code.rawObservationCount}</td><td style={tdStyle}>{code.matchCount}</td><td style={tdStyle}>{code.nonZeroCount}</td><td style={tdStyle}>{percent(code.prevalence)}</td><td style={tdStyle}>{code.min}–{code.max}</td><td style={tdStyle}><button onClick={() => onSelectCode(code)} style={{ ...btnStyle, padding: "2px 8px", fontSize: 11 }}>Inspect</button></td></tr>)}
          </tbody></table></div>}
        {visibleCorrelations.length > 0 && <><h3 style={{ ...h3Style, marginTop: 20 }}>Moves together</h3><p style={{ color: "#8b949e", fontSize: 11 }}>Pearson is not a mapping. Active overlap and informative evidence prevent zero-zero coincidences from overstating a relationship.</p><div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Codes</th><th style={thStyle}>Observations</th><th style={thStyle}>Matches</th><th style={thStyle}>Pearson</th><th style={thStyle}>Informative</th><th style={thStyle}>Both non-zero</th><th style={thStyle}>Either non-zero</th><th style={thStyle}>Overlap</th></tr></thead><tbody>{visibleCorrelations.slice(0, 30).map((c) => <tr key={`${c.aggregateIndex}-${c.codeA}-${c.codeB}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{c.aggregateIndex}</td><td style={tdStyle}>{c.codeA} / {c.codeB}</td><td style={tdStyle}>{c.observationsTested}</td><td style={tdStyle}>{c.matchesTested}</td><td style={tdStyle}>{c.pearson.toFixed(3)}{c.penalizedForLowOverlap ? " (low overlap)" : ""}</td><td style={tdStyle}>{c.informativeObservations} · {percent(c.informativeSupport)}</td><td style={tdStyle}>{c.bothNonZeroCount}</td><td style={tdStyle}>{c.eitherNonZeroCount}</td><td style={tdStyle}>{percent(c.overlapAmongActive)}</td></tr>)}</tbody></table></div></>}
        {analysis.relatedCodeFamilies.length > 0 && <RelatedCodeFamilies families={analysis.relatedCodeFamilies} />}
        {analysis.calibration.length > 0 && <><h3 style={{ ...h3Style, marginTop: 20 }}>Calibration / Known-Metric Relationships</h3><p style={{ color: "#8b949e", fontSize: 11 }}>Controls for validating the method. Zero-dominated relationships are excluded by default and no result creates a mapping.</p><div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Code</th><th style={thStyle}>Known metric</th><th style={thStyle}>Global support</th><th style={thStyle}>Informative support</th><th style={thStyle}>Informative obs</th><th style={thStyle}>Matches</th><th style={thStyle}>Both zero</th><th style={thStyle}>Result</th></tr></thead><tbody>{analysis.calibration.map((c) => <tr key={`${c.aggregateIndex}-${c.code}-${c.metric}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{c.aggregateIndex}</td><td style={tdStyle}>{c.code}</td><td style={tdStyle}>{c.metric}</td><td style={tdStyle}>{percent(c.supportRate)}</td><td style={tdStyle}>{percent(c.informativeSupport)}</td><td style={tdStyle}>{c.informativeObservations}</td><td style={tdStyle}>{c.matchesTested}</td><td style={tdStyle}>{c.bothZeroCount}</td><td style={tdStyle}>{c.redundantWithKnownMetric ? "REDUNDANT WITH KNOWN METRIC" : "Calibration only"}</td></tr>)}</tbody></table></div></>}
      </>}
      {selectedCode && <CodeDetail code={selectedCode} relations={analysis?.relations ?? []} onClose={() => onSelectCode(null)} />}
      {selectedRelation && <RelationDetail relation={selectedRelation} onClose={() => onSelectRelation(null)} />}
    </div>
  );
}

function AnchorView({
  data, anchorType, aggregateIndex, code, metric, loading, selectedRelationship, showDifferencesOnly, dnfFilter,
  familyData, familyCodes,
  onAnchorType, onAggregateIndex, onCode, onMetric, onRun, onSelectRelationship, onToggleDifferencesOnly, onDnfFilter,
  onFamilyCodes, onLoadFamily, onExplainResiduals, residualData, residualFilter, onResidualFilter, selectedDiscriminator, onSelectDiscriminator, onBack,
}: {
  data: AnchorInvestigation | null; anchorType: string; aggregateIndex: string; code: string; metric: string;
  loading: boolean; selectedRelationship: AnchorRelationship | null; showDifferencesOnly: boolean; dnfFilter: string;
  familyData: FamilyInvestigation | null; familyCodes: string;
  onAnchorType: (v: string) => void; onAggregateIndex: (v: string) => void; onCode: (v: string) => void;
  onMetric: (v: string) => void; onRun: () => void; onSelectRelationship: (v: AnchorRelationship | null) => void;
  onToggleDifferencesOnly: () => void; onDnfFilter: (v: string) => void;
  onFamilyCodes: (v: string) => void; onLoadFamily: () => void; onBack: () => void;
  onExplainResiduals: (relationship: AnchorRelationship) => void; residualData: ResidualExplainerResult | null;
  residualFilter: string; onResidualFilter: (value: string) => void; selectedDiscriminator: ResidualDiscriminator | null;
  onSelectDiscriminator: (value: ResidualDiscriminator | null) => void;
}) {
  const knownMetrics = ["goals", "assists", "shots", "passesAttempted", "passesCompleted", "tacklesAttempted", "tacklesCompleted"];
  const confirmedCodes = [{ agg: 0, code: 112, name: "Beats" }, { agg: 0, code: 115, name: "Pre-assists" }, { agg: 0, code: 152, name: "Through passes" }];

  const anchorLabel = () => {
    if (anchorType === "KNOWN_METRIC") return metric;
    if (anchorType === "CONFIRMED_ADVANCED") {
      const found = confirmedCodes.find((c) => c.agg === Number(aggregateIndex) && c.code === Number(code));
      return found ? `${found.name} (agg${aggregateIndex}[${code}])` : `agg${aggregateIndex}[${code}]`;
    }
    return `agg${aggregateIndex}[${code}]`;
  };

  const filteredRelationships = data?.relationships.filter((r) => {
    if (dnfFilter === "ALL") return true;
    return r.evidenceObservations.some((e) => e.matchCompletion === dnfFilter);
  }) ?? [];

  const exportAnchor = () => {
    if (!data) return;
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `anchor-investigation-${Date.now()}.json`; a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back</button>
      <h2 style={h2Style}>Anchor Investigation</h2>
      <p style={{ color: "#f0883e", fontSize: 12, marginBottom: 12 }}>
        Mathematical structure analysis only. Subtype/superset/duplicate labels are technical classifications — not sporting meanings.
      </p>

      {/* Anchor selector */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginBottom: 16 }}>
        <label style={filterLabel}>Type
          <select value={anchorType} onChange={(e) => onAnchorType(e.target.value)} style={selectStyle}>
            <option value="AGGREGATE_CODE">Aggregate Code</option>
            <option value="KNOWN_METRIC">Known Metric</option>
            <option value="CONFIRMED_ADVANCED">Confirmed Advanced</option>
          </select>
        </label>
        {anchorType === "KNOWN_METRIC" ? (
          <label style={filterLabel}>Metric
            <select value={metric} onChange={(e) => onMetric(e.target.value)} style={selectStyle}>
              {knownMetrics.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
          </label>
        ) : anchorType === "CONFIRMED_ADVANCED" ? (
          <label style={filterLabel}>Code
            <select value={`${aggregateIndex}-${code}`} onChange={(e) => { const [a, c] = e.target.value.split("-"); onAggregateIndex(a); onCode(c); }} style={selectStyle}>
              {confirmedCodes.map((c) => <option key={`${c.agg}-${c.code}`} value={`${c.agg}-${c.code}`}>{c.name} ({c.code})</option>)}
            </select>
          </label>
        ) : (
          <>
            <label style={filterLabel}>Agg <select value={aggregateIndex} onChange={(e) => onAggregateIndex(e.target.value)} style={selectStyle}><option value="0">0</option><option value="1">1</option></select></label>
            <label style={filterLabel}>Code <input type="number" value={code} onChange={(e) => onCode(e.target.value)} style={numberInputStyle} /></label>
          </>
        )}
        <button onClick={onRun} disabled={loading} style={btnStyle}>{loading ? "Analyzing..." : "Investigate"}</button>
        {data && <button onClick={exportAnchor} style={btnStyle}>Export JSON</button>}
      </div>

      {!data ? <p style={{ color: "#8b949e", fontSize: 13 }}>Select an anchor and run investigation.</p> : <>
        {/* Anchor profile */}
        <h3 style={h3Style}>Anchor Profile — {anchorLabel()}</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: 5, marginBottom: 16 }}>
          <StatChip label="Status" value={data.anchor.registryStatus} />
          {data.anchor.knownLabel && <StatChip label="Label" value={data.anchor.knownLabel} />}
          <StatChip label="Observations" value={data.anchor.observations} />
          <StatChip label="Matches" value={data.anchor.matches} />
          <StatChip label="Distinct players" value={data.anchor.distinctPlayers} />
          <StatChip label="Non-zero" value={data.anchor.nonZeroObservations} />
          <StatChip label="Prevalence" value={percent(data.anchor.prevalence)} />
          <StatChip label="Range" value={`${data.anchor.min}–${data.anchor.max}`} />
          <StatChip label="Mean" value={data.anchor.mean.toFixed(2)} />
          <StatChip label="Median" value={data.anchor.median.toFixed(1)} />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(100px, 1fr))", gap: 5, marginBottom: 16 }}>
          <StatChip label="RAW matches" value={data.dataset.rawMatchesAnalyzed} />
          <StatChip label="Observations" value={data.dataset.playerMatchObservations} />
          <StatChip label="Players" value={data.dataset.distinctPlayers} />
        </div>

        {/* DNF filter */}
        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
          <label style={filterLabel}>Match completion
            <select value={dnfFilter} onChange={(e) => onDnfFilter(e.target.value)} style={selectStyle}>
              <option value="ALL">ALL</option>
              <option value="COMPLETED">COMPLETED</option>
              <option value="DNF">DNF</option>
              <option value="UNKNOWN">UNKNOWN</option>
            </select>
          </label>
        </div>

        {/* Top related codes */}
        <h3 style={h3Style}>Top Related Codes ({filteredRelationships.length})</h3>
        {filteredRelationships.length === 0 ? <p style={{ color: "#8b949e", fontSize: 12 }}>No candidates found in the bounded dataset.</p> : (
          <div style={{ overflowX: "auto" }}>
            <table style={tableStyle}>
              <thead><tr>
                <th style={thStyle}>Agg</th><th style={thStyle}>Code</th><th style={thStyle}>Status</th><th style={thStyle}>Label</th>
                <th style={thStyle}>Classification</th><th style={thStyle}>Informative =</th><th style={thStyle}>Overlap</th>
                <th style={thStyle}>P(B|A)</th><th style={thStyle}>P(A|B)</th><th style={thStyle}>Pearson</th>
                <th style={thStyle}>Matches</th><th style={thStyle}>Players</th><th style={thStyle}>Score</th><th style={thStyle}></th>
              </tr></thead>
              <tbody>
                {filteredRelationships.slice(0, 50).map((r) => (
                  <tr key={`${r.candidateAggregateIndex}-${r.candidateCode}`} style={{ borderBottom: "1px solid #21262d", background: classificationBg(r.technicalClassification) }}>
                    <td style={tdStyle}>{r.candidateAggregateIndex}</td>
                    <td style={tdStyle}>{r.candidateCode}</td>
                    <td style={tdStyle}><ConfidenceBadge confidence={r.candidateRegistryStatus} /></td>
                    <td style={tdStyle}>{r.candidateKnownLabel ?? "—"}</td>
                    <td style={tdStyle}><ClassificationBadge classification={r.technicalClassification} /></td>
                    <td style={tdStyle}>{percent(r.informativeEqualityRate)}</td>
                    <td style={tdStyle}>{percent(r.nonZeroOverlap)}</td>
                    <td style={tdStyle}>{percent(r.pCandidateActiveGivenAnchorActive)}</td>
                    <td style={tdStyle}>{percent(r.pAnchorActiveGivenCandidateActive)}</td>
                    <td style={tdStyle}>{r.pearson?.toFixed(3) ?? "—"}</td>
                    <td style={tdStyle}>{r.matches}</td>
                    <td style={tdStyle}>{r.distinctPlayers}</td>
                    <td style={tdStyle}>{r.score.total.toFixed(1)}</td>
                    <td style={tdStyle}><button onClick={() => onSelectRelationship(r)} style={{ ...btnStyle, padding: "2px 8px", fontSize: 11 }}>Inspect</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Selected relationship detail */}
        {selectedRelationship && (
          <AnchorRelationshipDetail
            relationship={selectedRelationship}
            anchorLabel={anchorLabel()}
            showDifferencesOnly={showDifferencesOnly}
            dnfFilter={dnfFilter}
            onToggleDifferencesOnly={onToggleDifferencesOnly}
            onClose={() => onSelectRelationship(null)}
            onExplainResiduals={() => onExplainResiduals(selectedRelationship)}
            residualData={residualData}
            residualFilter={residualFilter}
            onResidualFilter={onResidualFilter}
            selectedDiscriminator={selectedDiscriminator}
            onSelectDiscriminator={onSelectDiscriminator}
            loading={loading}
          />
        )}

        {/* Conditional code profile */}
        {data.conditionalProfiles.length > 0 && <>
          <h3 style={{ ...h3Style, marginTop: 20 }}>Conditional Code Profile — When Anchor Is Active</h3>
          <p style={{ color: "#8b949e", fontSize: 11 }}>Shows activation patterns relative to the anchor. Not a sporting interpretation.</p>
          <div style={{ overflowX: "auto" }}>
            <table style={tableStyle}>
              <thead><tr>
                <th style={thStyle}>Code</th><th style={thStyle}>B active | A active</th><th style={thStyle}>B inactive | A active</th>
                <th style={thStyle}>A active | B active</th><th style={thStyle}>P(B|A)</th><th style={thStyle}>P(A|B)</th>
              </tr></thead>
              <tbody>
                {data.conditionalProfiles.filter((c) => c.anchorActiveObservations > 0 || c.candidateActiveObservations > 0).slice(0, 30).map((c) => (
                  <tr key={c.candidateCode} style={{ borderBottom: "1px solid #21262d" }}>
                    <td style={tdStyle}>{c.candidateCode}</td>
                    <td style={tdStyle}>{c.candidateActiveWhenAnchorActive}/{c.anchorActiveObservations}</td>
                    <td style={tdStyle}>{c.candidateInactiveWhenAnchorActive}/{c.anchorActiveObservations}</td>
                    <td style={tdStyle}>{c.anchorActiveWhenCandidateActive}/{c.candidateActiveObservations}</td>
                    <td style={tdStyle}>{percent(c.pCandidateActiveGivenAnchorActive)}</td>
                    <td style={tdStyle}>{percent(c.pAnchorActiveGivenCandidateActive)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>}

        {/* Family inspector */}
        <h3 style={{ ...h3Style, marginTop: 20 }}>Family Inspector</h3>
        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
          <label style={filterLabel}>Codes (comma-separated)
            <input type="text" value={familyCodes} onChange={(e) => onFamilyCodes(e.target.value)} placeholder="e.g. 0,121,158,164" style={{ ...selectStyle, width: 200 }} />
          </label>
          <button onClick={onLoadFamily} disabled={loading || !familyCodes.trim()} style={btnStyle}>Inspect family</button>
        </div>
        {familyData && <FamilyInspector data={familyData} />}
      </>}
    </div>
  );
}

function AnchorRelationshipDetail({
  relationship, anchorLabel, showDifferencesOnly, dnfFilter, onToggleDifferencesOnly, onClose,
  onExplainResiduals, residualData, residualFilter, onResidualFilter, selectedDiscriminator, onSelectDiscriminator, loading,
}: {
  relationship: AnchorRelationship; anchorLabel: string; showDifferencesOnly: boolean; dnfFilter: string;
  onToggleDifferencesOnly: () => void; onClose: () => void;
  onExplainResiduals: () => void; residualData: ResidualExplainerResult | null;
  residualFilter: string; onResidualFilter: (f: string) => void;
  selectedDiscriminator: ResidualDiscriminator | null; onSelectDiscriminator: (d: ResidualDiscriminator | null) => void;
  loading: boolean;
}) {
  const r = relationship;
  const rows = showDifferencesOnly ? r.differenceCases : r.evidenceObservations;
  const filteredRows = dnfFilter === "ALL" ? rows : rows.filter((e) => e.matchCompletion === dnfFilter);

  return (
    <section style={detailStyle}>
      <button onClick={onClose} style={{ ...btnStyle, fontSize: 11 }}>Close detail</button>
      <h3 style={h3Style}>{anchorLabel} → code {r.candidateCode}</h3>
      <p style={{ color: "#f0883e", fontSize: 12 }}>
        Technical classification: <ClassificationBadge classification={r.technicalClassification} />. This is not a sporting mapping.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 5, marginBottom: 12 }}>
        <StatChip label="Informative equality" value={percent(r.informativeEqualityRate)} />
        <StatChip label="Exact equality" value={percent(r.exactEqualityRate)} />
        <StatChip label="A >= B rate" value={percent(r.anchorGteCandidateRate)} />
        <StatChip label="B >= A rate" value={percent(r.candidateGteAnchorRate)} />
        <StatChip label="Overlap" value={percent(r.nonZeroOverlap)} />
        <StatChip label="P(B>0|A>0)" value={percent(r.pCandidateActiveGivenAnchorActive)} />
        <StatChip label="P(A>0|B>0)" value={percent(r.pAnchorActiveGivenCandidateActive)} />
        <StatChip label="P(==|active)" value={percent(r.pEqualGivenEitherActive)} />
        <StatChip label="Pearson" value={r.pearson?.toFixed(3) ?? "—"} />
        <StatChip label="Spearman" value={r.spearman?.toFixed(3) ?? "—"} />
        <StatChip label="Ratio B/A (A>0)" value={r.ratioBAMeanWhenAPositive?.toFixed(3) ?? "—"} />
        <StatChip label="Ratio A/B (B>0)" value={r.ratioABMeanWhenBPositive?.toFixed(3) ?? "—"} />
        <StatChip label="Both non-zero" value={r.bothNonZero} />
        <StatChip label="Either non-zero" value={r.eitherNonZero} />
        <StatChip label="Matches" value={r.matches} />
        <StatChip label="Distinct players" value={r.distinctPlayers} />
      </div>

      {/* Residual distribution A-B */}
      <h4 style={h3Style}>Residual Distribution (Anchor - Candidate)</h4>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(100px, 1fr))", gap: 5, marginBottom: 8 }}>
        <StatChip label="Min" value={r.residualAMinusB.min} />
        <StatChip label="Max" value={r.residualAMinusB.max} />
        <StatChip label="Mean" value={r.residualAMinusB.mean.toFixed(2)} />
        <StatChip label="Median" value={r.residualAMinusB.median.toFixed(1)} />
        <StatChip label="% zero" value={percent(r.residualAMinusB.zeroPercent)} />
      </div>
      <div style={{ fontSize: 11, color: "#8b949e", marginBottom: 12 }}>
        {Object.entries(r.residualAMinusB.residualCounts).sort(([a], [b]) => Number(a) - Number(b)).map(([val, count]) => (
          <span key={val} style={{ marginRight: 8 }}>residual={val}: {count}</span>
        ))}
      </div>

      {/* Score components */}
      <h4 style={h3Style}>Anchor Relationship Score</h4>
      <pre style={{ background: "#161b22", padding: 8, borderRadius: 4, fontSize: 11, marginBottom: 12 }}>{JSON.stringify(r.score, null, 2)}</pre>

      {/* Evidence table */}
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 8 }}>
        <h4 style={{ ...h3Style, marginBottom: 0 }}>{showDifferencesOnly ? "Difference Cases" : "Player-Match Evidence"}</h4>
        <label style={{ fontSize: 12, color: "#8b949e", cursor: "pointer" }}>
          <input type="checkbox" checked={showDifferencesOnly} onChange={onToggleDifferencesOnly} style={{ marginRight: 4 }} />
          Show only differences
        </label>
      </div>
      {filteredRows.length === 0 ? <p style={{ color: "#3fb950", fontSize: 12 }}>No difference cases — values are always equal on the retained sample.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={tableStyle}>
            <thead><tr>
              <th style={thStyle}>Match</th><th style={thStyle}>Player</th><th style={thStyle}>Anchor</th><th style={thStyle}>Candidate</th>
              <th style={thStyle}>Diff</th><th style={thStyle}>Ratio</th>
              <th style={thStyle}>Goals</th><th style={thStyle}>Assists</th><th style={thStyle}>Shots</th>
              <th style={thStyle}>PassC</th><th style={thStyle}>TackC</th>
              <th style={thStyle}>112</th><th style={thStyle}>115</th><th style={thStyle}>152</th><th style={thStyle}>174</th>
              <th style={thStyle}>Completion</th>
            </tr></thead>
            <tbody>
              {filteredRows.map((e, i) => (
                <tr key={`${e.matchId}-${e.playerId}-${i}`} style={{ borderBottom: "1px solid #21262d", background: e.difference !== 0 ? "rgba(248, 81, 73, 0.08)" : undefined }}>
                  <td style={{ ...tdStyle, fontSize: 11 }}>{e.matchId.slice(0, 12)}</td>
                  <td style={tdStyle}>{e.playerName ?? e.playerId}</td>
                  <td style={{ ...tdStyle, fontWeight: 600 }}>{e.anchorValue}</td>
                  <td style={{ ...tdStyle, fontWeight: 600 }}>{e.candidateValue}</td>
                  <td style={{ ...tdStyle, color: e.difference !== 0 ? "#f85149" : "#3fb950" }}>{e.difference}</td>
                  <td style={tdStyle}>{e.ratio?.toFixed(2) ?? "—"}</td>
                  <td style={tdStyle}>{e.goals ?? "—"}</td>
                  <td style={tdStyle}>{e.assists ?? "—"}</td>
                  <td style={tdStyle}>{e.shots ?? "—"}</td>
                  <td style={tdStyle}>{e.passesCompleted ?? "—"}</td>
                  <td style={tdStyle}>{e.tacklesCompleted ?? "—"}</td>
                  <td style={tdStyle}>{e.code112 ?? "—"}</td>
                  <td style={tdStyle}>{e.code115 ?? "—"}</td>
                  <td style={tdStyle}>{e.code152 ?? "—"}</td>
                  <td style={tdStyle}>{e.code174 ?? "—"}</td>
                  <td style={{ ...tdStyle, fontSize: 10 }}>{e.matchCompletion ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* V3 — Residual Explainer */}
      {r.informativeEqualityRate < 1.0 && (
        <div style={{ marginTop: 16 }}>
          <button onClick={onExplainResiduals} disabled={loading} style={{ ...btnStyle, background: "#388bfd", color: "#fff", fontWeight: 600 }}>
            {loading ? "Loading…" : residualData ? "Reload Residual Explainer" : "Explain differences"}
          </button>
        </div>
      )}

      {residualData && (
        <div style={{ marginTop: 16 }}>
          <h4 style={{ ...h3Style, fontSize: 14 }}>Residual Explainer — {residualData.candidateLabel ?? `agg${residualData.candidateAggregateIndex}[${residualData.candidateCode}]`}</h4>
          <p style={{ color: "#8b949e", fontSize: 11, marginBottom: 8 }}>
            Residual = anchorValue − candidateValue. Groups: NEGATIVE (candidate &gt; anchor), ZERO (equal), POSITIVE (anchor &gt; candidate).
          </p>

          {/* Groups summary */}
          <div style={{ display: "flex", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
            {residualData.groups.map((g) => (
              <div key={g.direction} style={{ padding: "6px 12px", background: g.direction === "ZERO" ? "#1a3a1a" : g.direction === "POSITIVE" ? "#3a2a1a" : "#1a2a3a", borderRadius: 6, fontSize: 12 }}>
                <strong style={{ color: g.direction === "ZERO" ? "#3fb950" : g.direction === "POSITIVE" ? "#f0883e" : "#58a6ff" }}>{g.direction}</strong>
                <span style={{ color: "#8b949e", marginLeft: 8 }}>{g.count} obs · {g.matches} matches · {g.players} players</span>
              </div>
            ))}
          </div>

          {/* Discriminators table */}
          {residualData.discriminators.length > 0 && (
            <div style={{ overflowX: "auto", marginBottom: 12 }}>
              <h5 style={{ color: "#c9d1d9", fontSize: 12, marginBottom: 6 }}>Residual Discriminators (ranked by score)</h5>
              <table style={tableStyle}>
                <thead><tr>
                  <th style={thStyle}>Agg</th><th style={thStyle}>Code</th><th style={thStyle}>Status</th>
                  <th style={thStyle}>Classification</th>
                  <th style={thStyle}>NEG act%</th><th style={thStyle}>ZERO act%</th><th style={thStyle}>POS act%</th>
                  <th style={thStyle}>Δ pos-zero</th><th style={thStyle}>Δ neg-zero</th>
                  <th style={thStyle}>Matches</th><th style={thStyle}>Players</th><th style={thStyle}>Score</th>
                  <th style={thStyle}>Warnings</th><th style={thStyle}></th>
                </tr></thead>
                <tbody>
                  {residualData.discriminators.map((d) => {
                    const cls = d.technicalClassification;
                    const clsColor = cls === "POSITIVE_RESIDUAL_ASSOCIATED" ? "#f0883e" : cls === "NEGATIVE_RESIDUAL_ASSOCIATED" ? "#58a6ff" : cls === "DIFFERENCE_ASSOCIATED" ? "#d2a8ff" : cls === "INSUFFICIENT_EVIDENCE" ? "#484f58" : "#8b949e";
                    return (
                      <tr key={`${d.aggregateIndex}-${d.code}`} style={{ borderBottom: "1px solid #21262d", cursor: "pointer", background: selectedDiscriminator?.code === d.code && selectedDiscriminator?.aggregateIndex === d.aggregateIndex ? "#1c2333" : undefined }} onClick={() => onSelectDiscriminator(selectedDiscriminator?.code === d.code && selectedDiscriminator?.aggregateIndex === d.aggregateIndex ? null : d)}>
                        <td style={tdStyle}>{d.aggregateIndex}</td>
                        <td style={tdStyle}>{d.code}</td>
                        <td style={{ ...tdStyle, fontSize: 10 }}>{d.registryStatus}{d.registryLabel ? ` (${d.registryLabel})` : ""}</td>
                        <td style={{ ...tdStyle, color: clsColor, fontSize: 10, fontWeight: 600 }}>{cls}</td>
                        <td style={tdStyle}>{percent(d.negative.activationRate)}</td>
                        <td style={tdStyle}>{percent(d.zero.activationRate)}</td>
                        <td style={tdStyle}>{percent(d.positive.activationRate)}</td>
                        <td style={tdStyle}>{d.positiveVsZero.activationRateDelta != null ? (d.positiveVsZero.activationRateDelta > 0 ? "+" : "") + (d.positiveVsZero.activationRateDelta * 100).toFixed(1) + "%" : "—"}</td>
                        <td style={tdStyle}>{d.negativeVsZero.activationRateDelta != null ? (d.negativeVsZero.activationRateDelta > 0 ? "+" : "") + (d.negativeVsZero.activationRateDelta * 100).toFixed(1) + "%" : "—"}</td>
                        <td style={tdStyle}>{d.distinctMatches}</td>
                        <td style={tdStyle}>{d.distinctPlayers}</td>
                        <td style={{ ...tdStyle, fontWeight: 600 }}>{d.score.total.toFixed(2)}</td>
                        <td style={{ ...tdStyle, fontSize: 10, color: "#f85149" }}>{d.warnings.length > 0 ? d.warnings.join(", ") : ""}</td>
                        <td style={tdStyle}><button style={{ ...btnStyle, fontSize: 10, padding: "2px 6px" }}>inspect</button></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Selected discriminator detail */}
          {selectedDiscriminator && (
            <div style={{ padding: 10, background: "#161b22", borderRadius: 6, marginBottom: 12, fontSize: 11 }}>
              <h5 style={{ color: "#c9d1d9", fontSize: 12, marginBottom: 6 }}>
                Discriminator Detail — agg{selectedDiscriminator.aggregateIndex}[{selectedDiscriminator.code}]
                {selectedDiscriminator.registryLabel ? ` (${selectedDiscriminator.registryLabel})` : ""}
              </h5>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 8 }}>
                {(["negative", "zero", "positive"] as const).map((dir) => {
                  const s = selectedDiscriminator[dir];
                  return (
                    <div key={dir} style={{ padding: 8, background: "#0d1117", borderRadius: 4 }}>
                      <strong style={{ color: dir === "zero" ? "#3fb950" : dir === "positive" ? "#f0883e" : "#58a6ff" }}>{dir.toUpperCase()}</strong>
                      <div style={{ color: "#8b949e", marginTop: 4 }}>
                        <div>Obs: {s.count} · Active: {s.activeCount} · Rate: {percent(s.activationRate)}</div>
                        <div>Mean: {s.mean?.toFixed(2) ?? "—"} · Median: {s.median ?? "—"}</div>
                        <div>Min: {s.min ?? "—"} · Max: {s.max ?? "—"}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
              <div style={{ marginTop: 8, color: "#8b949e" }}>
                <div>P(active|POS): {percent(selectedDiscriminator.pActiveGivenPositive)} · P(active|ZERO): {percent(selectedDiscriminator.pActiveGivenZero)} · P(active|NEG): {percent(selectedDiscriminator.pActiveGivenNegative)}</div>
                <div style={{ marginTop: 4, fontSize: 10 }}>
                  Score components: actDelta={selectedDiscriminator.score.activationDeltaComponent.toFixed(2)} valDelta={selectedDiscriminator.score.valueDeltaComponent.toFixed(2)} consistency={selectedDiscriminator.score.consistencyComponent.toFixed(2)} sample={selectedDiscriminator.score.sampleSizeComponent.toFixed(2)} matchDiv={selectedDiscriminator.score.matchDiversityComponent.toFixed(2)} playerDiv={selectedDiscriminator.score.playerDiversityComponent.toFixed(2)} dirSpec={selectedDiscriminator.score.directionSpecificityComponent.toFixed(2)} | penalties: player={selectedDiscriminator.score.singlePlayerPenalty.toFixed(2)} match={selectedDiscriminator.score.singleMatchPenalty.toFixed(2)} tiny={selectedDiscriminator.score.tinySamplePenalty.toFixed(2)}
                </div>
              </div>
            </div>
          )}

          {/* Evidence table */}
          <div style={{ marginBottom: 8 }}>
            <span style={{ color: "#8b949e", fontSize: 11, marginRight: 8 }}>Evidence filter:</span>
            {["ALL", "NEGATIVE", "POSITIVE"].map((f) => (
              <button key={f} onClick={() => onResidualFilter(f)} style={{ ...btnStyle, fontSize: 10, padding: "2px 8px", marginRight: 4, background: residualFilter === f ? "#388bfd" : undefined, color: residualFilter === f ? "#fff" : undefined }}>{f}</button>
            ))}
          </div>
          {(() => {
            const ev = residualFilter === "ALL" ? residualData.evidence : residualData.evidence.filter((e) => e.residualDirection === residualFilter);
            return ev.length > 0 ? (
              <div style={{ overflowX: "auto", marginBottom: 12 }}>
                <table style={tableStyle}>
                  <thead><tr>
                    <th style={thStyle}>Match</th><th style={thStyle}>Player</th>
                    <th style={thStyle}>Anchor</th><th style={thStyle}>Candidate</th><th style={thStyle}>Residual</th><th style={thStyle}>Dir</th>
                    <th style={thStyle}>Investigated</th>
                    <th style={thStyle}>Goals</th><th style={thStyle}>Assists</th><th style={thStyle}>Passes</th>
                    <th style={thStyle}>Completion</th>
                  </tr></thead>
                  <tbody>
                    {ev.map((e, i) => (
                      <tr key={i} style={{ borderBottom: "1px solid #21262d" }}>
                        <td style={{ ...tdStyle, fontSize: 10 }}>{e.matchId.slice(-6)}</td>
                        <td style={{ ...tdStyle, fontSize: 10 }}>{e.playerName ?? e.playerId.slice(-6)}</td>
                        <td style={tdStyle}>{e.anchorValue}</td>
                        <td style={tdStyle}>{e.candidateValue}</td>
                        <td style={{ ...tdStyle, color: e.residual > 0 ? "#f0883e" : e.residual < 0 ? "#58a6ff" : "#3fb950", fontWeight: 600 }}>{e.residual > 0 ? "+" : ""}{e.residual}</td>
                        <td style={{ ...tdStyle, fontSize: 10 }}>{e.residualDirection}</td>
                        <td style={{ ...tdStyle, fontWeight: 600 }}>{e.investigatedCodeValue}</td>
                        <td style={tdStyle}>{e.goals ?? "—"}</td>
                        <td style={tdStyle}>{e.assists ?? "—"}</td>
                        <td style={tdStyle}>{e.passesCompleted ?? "—"}</td>
                        <td style={{ ...tdStyle, fontSize: 10 }}>{e.matchCompletion ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : <p style={{ color: "#484f58", fontSize: 11 }}>No evidence rows for this filter.</p>;
          })()}

          {/* Residual Signatures */}
          {residualData.signatures.length > 0 && (
            <div>
              <h5 style={{ color: "#c9d1d9", fontSize: 12, marginBottom: 6 }}>Residual Signatures — Non-zero residual observations</h5>
              <p style={{ color: "#8b949e", fontSize: 10, marginBottom: 8 }}>Code activations for each observation where anchor ≠ candidate. Top discriminators highlighted.</p>
              {residualData.signatures.map((sig, si) => (
                <div key={si} style={{ padding: 8, background: "#0d1117", borderRadius: 4, marginBottom: 6, fontSize: 11 }}>
                  <div style={{ color: "#c9d1d9", marginBottom: 4 }}>
                    <strong style={{ color: sig.residual > 0 ? "#f0883e" : "#58a6ff" }}>{sig.residualDirection}</strong>
                    <span style={{ color: "#8b949e", marginLeft: 8 }}>residual={sig.residual > 0 ? "+" : ""}{sig.residual} · anchor={sig.anchorValue} · candidate={sig.candidateValue}</span>
                    <span style={{ color: "#484f58", marginLeft: 8 }}>{sig.playerName ?? sig.playerId.slice(-6)} · {sig.matchId.slice(-6)}</span>
                  </div>
                  <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                    {sig.relevantCodes.map((rc, ri) => (
                      <span key={ri} style={{ padding: "2px 6px", borderRadius: 3, fontSize: 10, background: rc.isTopDiscriminator ? "#1c2333" : "#161b22", border: rc.isTopDiscriminator ? "1px solid #388bfd" : "1px solid #21262d", color: rc.isTopDiscriminator ? "#58a6ff" : "#8b949e" }}>
                        agg{rc.aggregateIndex}[{rc.code}]={rc.value}{rc.registryLabel ? ` (${rc.registryLabel})` : ""}{rc.registryStatus === "CONFIRMED" ? " ✓" : ""}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function FamilyInspector({ data }: { data: FamilyInvestigation }) {
  return (
    <section style={detailStyle}>
      <h3 style={h3Style}>Family · Aggregate {data.aggregateIndex} · Codes: {data.codes.join(", ")}</h3>
      <p style={{ color: "#f0883e", fontSize: 12 }}>Pairwise relationships within the family. Mathematical structure only.</p>

      {/* Matrix */}
      <div style={{ overflowX: "auto", marginBottom: 16 }}>
        <table style={tableStyle}>
          <thead><tr>
            <th style={thStyle}>A</th><th style={thStyle}>B</th><th style={thStyle}>Pearson</th>
            <th style={thStyle}>Informative =</th><th style={thStyle}>Non-zero overlap</th>
          </tr></thead>
          <tbody>
            {data.matrix.map((cell) => (
              <tr key={`${cell.codeA}-${cell.codeB}`} style={{ borderBottom: "1px solid #21262d" }}>
                <td style={tdStyle}>{cell.codeA}</td>
                <td style={tdStyle}>{cell.codeB}</td>
                <td style={tdStyle}>{cell.pearson?.toFixed(3) ?? "—"}</td>
                <td style={tdStyle}>{percent(cell.informativeEquality)}</td>
                <td style={tdStyle}>{percent(cell.nonZeroOverlap)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Side-by-side observations */}
      <h4 style={h3Style}>Player-Match Observations ({data.observations.length})</h4>
      <div style={{ overflowX: "auto" }}>
        <table style={tableStyle}>
          <thead><tr>
            <th style={thStyle}>Match</th><th style={thStyle}>Player</th>
            {data.codes.map((c) => <th key={c} style={thStyle}>Code {c}</th>)}
            <th style={thStyle}>Goals</th><th style={thStyle}>Assists</th><th style={thStyle}>PassC</th><th style={thStyle}>TackC</th>
            <th style={thStyle}>Completion</th>
          </tr></thead>
          <tbody>
            {data.observations.map((obs, i) => (
              <tr key={`${obs.matchId}-${obs.playerId}-${i}`} style={{ borderBottom: "1px solid #21262d" }}>
                <td style={{ ...tdStyle, fontSize: 11 }}>{obs.matchId.slice(0, 12)}</td>
                <td style={tdStyle}>{obs.playerName ?? obs.playerId}</td>
                {data.codes.map((c) => <td key={c} style={{ ...tdStyle, fontWeight: 600 }}>{obs.values[String(c)] ?? 0}</td>)}
                <td style={tdStyle}>{obs.goals ?? "—"}</td>
                <td style={tdStyle}>{obs.assists ?? "—"}</td>
                <td style={tdStyle}>{obs.passesCompleted ?? "—"}</td>
                <td style={tdStyle}>{obs.tacklesCompleted ?? "—"}</td>
                <td style={{ ...tdStyle, fontSize: 10 }}>{obs.matchCompletion ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ClassificationBadge({ classification }: { classification: string }) {
  const colors: Record<string, { bg: string; text: string }> = {
    NEAR_DUPLICATE: { bg: "#238636", text: "#fff" },
    POSSIBLE_SUBTYPE: { bg: "#1f6feb", text: "#fff" },
    POSSIBLE_SUPERSET: { bg: "#8957e5", text: "#fff" },
    RELATED: { bg: "#9e6a03", text: "#fff" },
    INDEPENDENT: { bg: "#30363d", text: "#8b949e" },
  };
  const c = colors[classification] ?? colors.INDEPENDENT;
  return <span style={{ background: c.bg, color: c.text, padding: "1px 6px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{classification}</span>;
}

function classificationBg(classification: string): string | undefined {
  if (classification === "NEAR_DUPLICATE") return "rgba(35, 134, 54, 0.08)";
  if (classification === "POSSIBLE_SUBTYPE") return "rgba(31, 111, 235, 0.06)";
  if (classification === "POSSIBLE_SUPERSET") return "rgba(137, 87, 229, 0.06)";
  return undefined;
}

function DiscoveryRelationsTable({ relations, onSelect }: { relations: DiscoveryRelation[]; onSelect: (relation: DiscoveryRelation) => void }) {
  return <div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Pattern</th><th style={thStyle}>Type</th><th style={thStyle}>Informative evidence</th><th style={thStyle}>Global evidence</th><th style={thStyle}>Matches</th><th style={thStyle}>Non-zero overlap</th><th style={thStyle}>Counterexamples</th><th style={thStyle}>Tier</th><th style={thStyle}></th></tr></thead><tbody>{relations.map((relation) => <tr key={relation.id} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{relation.aggregateIndex}</td><td style={tdStyle}>{relationPattern(relation)}</td><td style={tdStyle}>{relation.relationType}</td><td style={tdStyle}>{relation.evidence.informativeSatisfied}/{relation.evidence.informativeObservations} ({percent(relation.evidence.informativeSupport)})</td><td style={tdStyle}>{relation.exactMatches}/{relation.observationsTested} ({percent(relation.supportRate)})</td><td style={tdStyle}>{relation.matchesTested}</td><td style={tdStyle}>{relation.evidence.bothNonZeroCount}/{relation.evidence.eitherNonZeroCount} ({percent(relation.evidence.overlapAmongActive)})</td><td style={{ ...tdStyle, color: relation.violations > 0 ? "#f0883e" : undefined }}>{relation.violations}</td><td style={tdStyle}>{relation.evidence.zeroDominated ? <span style={{ color: "#d29922" }}>ZERO-DOMINATED</span> : <EvidenceBadge tier={relation.evidenceTier} />}</td><td style={tdStyle}><button onClick={() => onSelect(relation)} style={{ ...btnStyle, padding: "2px 8px", fontSize: 11 }}>Inspect</button></td></tr>)}</tbody></table></div>;
}

function TopDiscoverySignalsTable({ signals, relations, onSelectRelation }: { signals: TopDiscoverySignal[]; relations: DiscoveryRelation[]; onSelectRelation: (relation: DiscoveryRelation) => void }) {
  return <div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Pattern</th><th style={thStyle}>Type</th><th style={thStyle}>Informative evidence</th><th style={thStyle}>Global evidence</th><th style={thStyle}>Matches</th><th style={thStyle}>Non-zero overlap</th><th style={thStyle}>Counterexamples</th><th style={thStyle}>Tier</th><th style={thStyle}></th></tr></thead><tbody>{signals.map((signal) => { const relation = signal.relationId ? relations.find((item) => item.id === signal.relationId) : null; return <tr key={signal.id} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{signal.aggregateIndex}</td><td style={tdStyle}>{signal.pattern}</td><td style={tdStyle}>{signal.type}</td><td style={tdStyle}>{signal.informativeObservations} ({percent(signal.informativeSupport)})</td><td style={tdStyle}>{signal.globalObservations} ({percent(signal.globalSupport)})</td><td style={tdStyle}>{signal.matches}</td><td style={tdStyle}>{percent(signal.nonZeroOverlap)}</td><td style={tdStyle}>{signal.counterexamples}</td><td style={tdStyle}>{signal.zeroDominated ? <span style={{ color: "#d29922" }}>ZERO-DOMINATED</span> : <EvidenceBadge tier={signal.tier} />}</td><td style={tdStyle}>{relation && <button onClick={() => onSelectRelation(relation)} style={{ ...btnStyle, padding: "2px 8px", fontSize: 11 }}>Inspect</button>}</td></tr>; })}</tbody></table></div>;
}

function CodeDetail({ code, relations, onClose }: { code: CodeInventory; relations: DiscoveryRelation[]; onClose: () => void }) {
  const involved = relations.filter((r) => r.aggregateIndex === code.aggregateIndex && [r.codeA, r.codeB, r.codeC].includes(code.code));
  return <section style={detailStyle}><button onClick={onClose} style={{ ...btnStyle, fontSize: 11 }}>Close code detail</button><h3 style={h3Style}>Aggregate {code.aggregateIndex}[{code.code}]</h3><p style={{ color: "#f0883e", fontSize: 12 }}>Observed transport code. No sporting interpretation is assigned.</p><div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: 5 }}><StatChip label="Observations" value={code.rawObservationCount} /><StatChip label="Matches" value={code.matchCount} /><StatChip label="Non-zero" value={code.nonZeroCount} /><StatChip label="Prevalence" value={percent(code.prevalence)} /><StatChip label="Min / Max" value={`${code.min} / ${code.max}`} /><StatChip label="Mean / Median" value={`${code.mean.toFixed(2)} / ${code.median}`} /><StatChip label="Distinct values" value={code.distinctValueCount} /></div><h4 style={h3Style}>Observed values</h4><table style={tableStyle}><thead><tr><th style={thStyle}>Match</th><th style={thStyle}>Player</th><th style={thStyle}>Value</th></tr></thead><tbody>{code.observedValues.map((v) => <tr key={`${v.matchId}-${v.playerId}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{v.matchId}</td><td style={tdStyle}>{v.playerName ?? v.playerId}</td><td style={tdStyle}>{v.value}</td></tr>)}</tbody></table><h4 style={h3Style}>Relationships</h4>{involved.length ? <DiscoveryRelationsTable relations={involved.slice(0, 20)} onSelect={() => {}} /> : <p style={{ color: "#8b949e", fontSize: 12 }}>No relationship passed the bounded discovery filters.</p>}</section>;
}

function RelatedCodeFamilies({ families }: { families: RelatedCodeFamily[] }) {
  const [selected, setSelected] = useState<RelatedCodeFamily | null>(null);
  return <><h3 style={{ ...h3Style, marginTop: 20 }}>Related Code Families</h3><p style={{ color: "#8b949e", fontSize: 11 }}>Connected groups of strong within-aggregate signals. A family is not assigned a sporting meaning.</p><div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Aggregate</th><th style={thStyle}>Codes</th><th style={thStyle}>Edges</th><th style={thStyle}>Average Pearson</th><th style={thStyle}>Minimum Pearson</th><th style={thStyle}>Non-zero overlap</th><th style={thStyle}>Matches</th><th style={thStyle}>Observations</th><th style={thStyle}></th></tr></thead><tbody>{families.map((family) => <tr key={`${family.aggregateIndex}-${family.codes.join("-")}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{family.aggregateIndex}</td><td style={tdStyle}>{family.codes.join(", ")}</td><td style={tdStyle}>{family.relationshipCount}</td><td style={tdStyle}>{family.averagePearson.toFixed(3)}</td><td style={tdStyle}>{family.minimumPearson.toFixed(3)}</td><td style={tdStyle}>{percent(family.averageNonZeroOverlap)}</td><td style={tdStyle}>{family.matches}</td><td style={tdStyle}>{family.observations}</td><td style={tdStyle}><button onClick={() => setSelected(family)} style={{ ...btnStyle, padding: "2px 8px", fontSize: 11 }}>Inspect</button></td></tr>)}</tbody></table></div>{selected && <section style={detailStyle}><button onClick={() => setSelected(null)} style={{ ...btnStyle, fontSize: 11 }}>Close family detail</button><h3 style={h3Style}>Related Code Family · Aggregate {selected.aggregateIndex}</h3><p style={{ color: "#f0883e", fontSize: 12 }}>Codes: {selected.codes.join(", ")}. Edges show related mathematical behavior only.</p><table style={tableStyle}><thead><tr><th style={thStyle}>Code A</th><th style={thStyle}>Code B</th><th style={thStyle}>Pearson</th><th style={thStyle}>Equality</th><th style={thStyle}>Informative support</th><th style={thStyle}>Both non-zero</th><th style={thStyle}>Either non-zero</th><th style={thStyle}>Overlap</th></tr></thead><tbody>{selected.edges.map((edge) => <tr key={`${edge.codeA}-${edge.codeB}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{edge.codeA}</td><td style={tdStyle}>{edge.codeB}</td><td style={tdStyle}>{edge.pearson.toFixed(3)}</td><td style={tdStyle}>{percent(edge.exactEqualityRate)}</td><td style={tdStyle}>{percent(edge.informativeSupport)}</td><td style={tdStyle}>{edge.bothNonZeroCount}</td><td style={tdStyle}>{edge.eitherNonZeroCount}</td><td style={tdStyle}>{percent(edge.overlapAmongActive)}</td></tr>)}</tbody></table></section>}</>;
}

function RelationDetail({ relation, onClose }: { relation: DiscoveryRelation; onClose: () => void }) {
  const rows = (entries: RelationExample[], title: string, color: string) => <><h4 style={{ ...h3Style, color }}>{title} ({entries.length})</h4>{entries.length === 0 ? <p style={{ color: "#8b949e", fontSize: 12 }}>None in the retained sample.</p> : <table style={tableStyle}><thead><tr><th style={thStyle}>Match</th><th style={thStyle}>Player</th><th style={thStyle}>A</th><th style={thStyle}>B</th>{relation.codeC !== null && <th style={thStyle}>C</th>}<th style={thStyle}>Expected</th><th style={thStyle}>Difference</th></tr></thead><tbody>{entries.map((e) => <tr key={`${e.matchId}-${e.playerId}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{e.matchId}</td><td style={tdStyle}>{e.playerName ?? e.playerId}</td><td style={tdStyle}>{e.a}</td><td style={tdStyle}>{e.b}</td>{relation.codeC !== null && <td style={tdStyle}>{e.c}</td>}<td style={tdStyle}>{e.expected}</td><td style={tdStyle}>{e.difference}</td></tr>)}</tbody></table>}</>;
  return <section style={detailStyle}><button onClick={onClose} style={{ ...btnStyle, fontSize: 11 }}>Close relation detail</button><h3 style={h3Style}>Aggregate {relation.aggregateIndex}: {relationPattern(relation)}</h3><p style={{ color: "#f0883e", fontSize: 12 }}>Global: {relation.exactMatches}/{relation.observationsTested} · {percent(relation.supportRate)}. Informative: {relation.evidence.informativeSatisfied}/{relation.evidence.informativeObservations} · {percent(relation.evidence.informativeSupport)}. This is not a sporting interpretation.</p><div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: 5 }}><StatChip label="Both zero" value={relation.evidence.bothZeroCount} /><StatChip label="All zero" value={relation.evidence.allZeroCount} /><StatChip label="Both non-zero" value={relation.evidence.bothNonZeroCount} /><StatChip label="Either non-zero" value={relation.evidence.eitherNonZeroCount} /><StatChip label="Active overlap" value={percent(relation.evidence.overlapAmongActive)} /><StatChip label="Zero dominated" value={relation.evidence.zeroDominated ? "YES" : "NO"} warn={relation.evidence.zeroDominated} /></div>{rows(relation.counterexamples, "Counterexamples", "#f85149")}{rows(relation.examples, "Examples supporting the relation", "#3fb950")}<h4 style={h3Style}>Discovery score components</h4><pre style={{ background: "#161b22", padding: 8, borderRadius: 4, fontSize: 11 }}>{JSON.stringify(relation.score, null, 2)}</pre></section>;
}

function relationPattern(relation: DiscoveryRelation): string {
  const a = `${relation.codeA}`; const b = `${relation.codeB}`; const c = relation.codeC === null ? "" : ` ${relation.relationType === "SUM" ? "+" : "-"} ${relation.codeC}`;
  if (relation.relationType === "EQUAL") return `${a} == ${b}`;
  if (relation.relationType === "GREATER_OR_EQUAL") return `${a} >= ${b}`;
  if (relation.relationType === "LESS_OR_EQUAL") return `${a} <= ${b}`;
  return `${a} == ${b}${c}`;
}

function percent(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function EvidenceBadge({ tier }: { tier: string }) {
  const color = tier === "STRONG_CANDIDATE" ? "#238636" : tier === "CANDIDATE" ? "#1f6feb" : "#30363d";
  return <span style={{ background: color, color: "#fff", padding: "1px 6px", borderRadius: 4, fontSize: 10 }}>{tier}</span>;
}

function UnknownFieldValue({ value, jsonType, truncated }: { value: string; jsonType: string; truncated: boolean }) {
  const shouldExpand = jsonType === "object" || jsonType === "array" || truncated || value.length > 240;
  if (!shouldExpand) {
    return <span style={{ fontFamily: "monospace", fontSize: 12 }}>{value}</span>;
  }
  return <UnknownFieldExpandable value={value} truncated={truncated} />;
}

function UnknownFieldExpandable({ value, truncated }: { value: string; truncated: boolean }) {
  const [expanded, setExpanded] = useState(false);
  let formatted = value;
  try { formatted = JSON.stringify(JSON.parse(value), null, 2); } catch {}
  const preview = formatted.length > 240 ? `${formatted.slice(0, 240)}…` : formatted;
  return (
    <div>
      {!expanded && <pre style={{ margin: "0 0 4px", whiteSpace: "pre-wrap", fontFamily: "monospace", fontSize: 12, color: "#c9d1d9" }}>{preview}</pre>}
      <button onClick={() => setExpanded(!expanded)} style={{ background: "none", border: "none", color: "#58a6ff", cursor: "pointer", fontSize: 12, padding: 0 }}>
        {expanded ? "▼ collapse" : "► expand"}
      </button>
      {expanded && (
        <pre style={{ background: "#161b22", padding: 8, borderRadius: 4, fontSize: 11, overflow: "auto", color: "#c9d1d9", marginTop: 4, maxHeight: 300 }}>
          {formatted}{truncated && <span style={{ color: "#f0883e" }}>{"\n"}… (truncated)</span>}
        </pre>
      )}
    </div>
  );
}

function StatChip({ label, value, warn }: { label: string; value: string | number | null | undefined; warn?: boolean }) {
  return (
    <div style={{ background: "#161b22", padding: "4px 8px", borderRadius: 4, fontSize: 12 }}>
      <span style={{ color: "#8b949e" }}>{label}: </span>
      <span style={{ color: warn ? "#f0883e" : "#c9d1d9", fontWeight: 600 }}>{value ?? "—"}</span>
      {warn && <span style={{ color: "#f0883e", fontSize: 10, marginLeft: 4 }}>⚠ agg0+agg1</span>}
    </div>
  );
}

function ConfidenceBadge({ confidence }: { confidence: string }) {
  const colors: Record<string, { bg: string; text: string }> = {
    CONFIRMED: { bg: "#238636", text: "#fff" },
    HIGH_CONFIDENCE: { bg: "#1f6feb", text: "#fff" },
    HYPOTHESIS: { bg: "#9e6a03", text: "#fff" },
    UNKNOWN: { bg: "#30363d", text: "#8b949e" },
  };
  const c = colors[confidence] ?? colors.UNKNOWN;
  return (
    <span style={{ background: c.bg, color: c.text, padding: "1px 6px", borderRadius: 4, fontSize: 11, fontWeight: 600 }}>
      {confidence}
    </span>
  );
}

function confidenceBg(confidence: string): string | undefined {
  if (confidence === "CONFIRMED") return "rgba(35, 134, 54, 0.08)";
  if (confidence === "HYPOTHESIS") return "rgba(158, 106, 3, 0.08)";
  return undefined;
}

export function NovelMetricsView({ data, detail, loading, onRun, onInspect, onCloseDetail, onBack }: {
  data: NovelResult | null; detail: NovelDetail | null; loading: boolean;
  onRun: () => void; onInspect: (candidate: NovelCandidate) => void; onCloseDetail: () => void; onBack: () => void;
}) {
  return <div>
    <button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to matches</button>
    <h2 style={h2Style}>Novel Metric Discovery</h2>
    <p style={{ color: "#f0883e", fontSize: 12 }}>UNKNOWN codes are ranked only by investigation value. No sporting meaning is assigned.</p>
    <button onClick={onRun} disabled={loading} style={{ ...btnStyle, marginBottom: 14 }}>{loading ? "Analyzing…" : "Run bounded analysis"}</button>
    {!data ? <p style={{ color: "#8b949e" }}>Run the bounded analysis to inspect UNKNOWN aggregate codes.</p> : <>
      <div style={{ display: "flex", gap: 8, marginBottom: 12, flexWrap: "wrap" }}><StatChip label="RAW matches" value={data.rawMatchesAnalyzed} /><StatChip label="Player-match rows" value={data.playerMatchObservations} /><StatChip label="UNKNOWN candidates" value={data.candidates.length} /></div>
      <div style={{ overflowX: "auto" }}><table style={tableStyle}><thead><tr><th style={thStyle}>Code</th><th style={thStyle}>Aggregate</th><th style={thStyle}>Obs</th><th style={thStyle}>Active</th><th style={thStyle}>Matches</th><th style={thStyle}>Players</th><th style={thStyle}>Range</th><th style={thStyle}>Novelty</th><th style={thStyle}>Closest known relation</th><th style={thStyle}>Classification</th><th style={thStyle}>Warnings</th><th style={thStyle}></th></tr></thead><tbody>{data.candidates.map((c) => <tr key={`${c.aggregateIndex}-${c.code}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>agg{c.aggregateIndex}[{c.code}]</td><td style={tdStyle}>{c.aggregateIndex}</td><td style={tdStyle}>{c.observations}</td><td style={tdStyle}>{percent(c.activeRate)}</td><td style={tdStyle}>{c.matches}</td><td style={tdStyle}>{c.players}</td><td style={tdStyle}>{c.min}–{c.max}</td><td style={tdStyle}>{c.priority} · {c.noveltyScore.toFixed(1)}</td><td style={tdStyle}>{c.closestKnownRelation ? `${c.closestKnownRelation.name} (${c.closestKnownRelation.classification})` : "—"}</td><td style={tdStyle}>{c.classification}</td><td style={{ ...tdStyle, fontSize: 10 }}>{c.warnings.join(", ") || "—"}</td><td style={tdStyle}><button onClick={() => onInspect(c)} style={{ ...btnStyle, fontSize: 11, padding: "2px 8px" }}>Inspect</button></td></tr>)}</tbody></table></div>
      {data.families.length > 0 && <p style={{ color: "#8b949e", fontSize: 11, marginTop: 12 }}>Unknown families: {data.families.map((f) => `agg${f.aggregateIndex}[${f.relatedCodes.join(", ")}] → representative ${f.representativeCode}`).join("; ")}</p>}
      {detail && <NovelDetailView data={detail} onClose={onCloseDetail} />}
    </>}
  </div>;
}

function NovelDetailView({ data, onClose }: { data: NovelDetail; onClose: () => void }) {
  const rows = (title: string, values: NovelEvidence[]) => <section style={{ marginTop: 16 }}><h3 style={h3Style}>{title}</h3><table style={tableStyle}><thead><tr><th style={thStyle}>Match</th><th style={thStyle}>Player</th><th style={thStyle}>Completion</th><th style={thStyle}>Value</th></tr></thead><tbody>{values.map((e) => <tr key={`${title}-${e.matchId}-${e.playerId}`} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{e.matchId}</td><td style={tdStyle}>{e.playerName ?? e.playerId}</td><td style={tdStyle}>{e.completion ?? "—"}</td><td style={tdStyle}>{e.value}</td></tr>)}</tbody></table></section>;
  return <section style={detailStyle}><button onClick={onClose} style={{ ...btnStyle, fontSize: 11 }}>Close detail</button><h3 style={h3Style}>agg{data.candidate.aggregateIndex}[{data.candidate.code}]</h3><p style={{ color: "#f0883e", fontSize: 11 }}>Registry status: UNKNOWN. Statistical investigation only.</p><div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}><StatChip label="Classification" value={data.candidate.classification} /><StatChip label="Family" value={data.relatedFamily?.relatedCodes.join(", ") ?? "—"} /></div><h3 style={{ ...h3Style, marginTop: 16 }}>Closest known relations</h3><table style={tableStyle}><thead><tr><th style={thStyle}>Metric</th><th style={thStyle}>Class</th><th style={thStyle}>Pearson</th><th style={thStyle}>Spearman</th><th style={thStyle}>Equality</th></tr></thead><tbody>{data.knownRelations.map((r) => <tr key={r.name} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{r.name}</td><td style={tdStyle}>{r.classification}</td><td style={tdStyle}>{r.pearson?.toFixed(3) ?? "—"}</td><td style={tdStyle}>{r.spearman?.toFixed(3) ?? "—"}</td><td style={tdStyle}>{percent(r.exactEqualityRate)}</td></tr>)}</tbody></table>{rows("High values", data.highValues)}{rows("Low non-zero values", data.lowNonZeroValues)}{rows("Zero observations", data.zeroValues)}</section>;
}

export function PositionObservationsView({ data, onBack }: { data: PositionObservationsData | null; onBack: () => void }) {
  return <div><button onClick={onBack} style={{ ...btnStyle, marginBottom: 12, fontSize: 12 }}>← Back to player</button><h2 style={h2Style}>Position Observations</h2><p style={{ color: "#f0883e", fontSize: 12 }}>Raw EA data plus externally mapped candidates. This does not claim actual played position.</p>{!data ? <p style={{ color: "#8b949e" }}>No observations loaded.</p> : <><div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}><StatChip label="Coverage" value={data.coverage} /><StatChip label="Distinct codes" value={data.distinctCodes} /></div><p style={{ color: "#8b949e", fontSize: 12 }}>Distribution: {data.distribution.map((d) => `${d.eaPositionCode ?? "—"}/${d.candidate.candidateLabel ?? "—"}: ${d.observations}`).join(" · ") || "—"}</p><table style={tableStyle}><thead><tr><th style={thStyle}>Match</th><th style={thStyle}>Opponent</th><th style={thStyle}>EA code</th><th style={thStyle}>Candidate</th><th style={thStyle}>Classification</th><th style={thStyle}>Semantic status</th><th style={thStyle}>Rating</th></tr></thead><tbody>{data.observations.map((o) => <tr key={o.matchId} style={{ borderBottom: "1px solid #21262d" }}><td style={tdStyle}>{o.matchId}</td><td style={tdStyle}>{o.opponentName ?? "—"}</td><td style={tdStyle}>{o.eaPositionCode ?? "—"}</td><td style={tdStyle}>{o.candidate.candidateLabel ?? "—"}</td><td style={tdStyle}>{o.candidate.classification}</td><td style={tdStyle}>{o.candidate.semanticStatus}</td><td style={tdStyle}>{o.rating ?? "—"}</td></tr>)}</tbody></table></>}</div>;
}

const btnStyle: React.CSSProperties = {
  padding: "6px 14px",
  border: "1px solid #30363d",
  borderRadius: 6,
  background: "#21262d",
  color: "#c9d1d9",
  cursor: "pointer",
  fontSize: 13,
};

const filterLabel: React.CSSProperties = { display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "#8b949e" };
const selectStyle: React.CSSProperties = { background: "#0d1117", color: "#c9d1d9", border: "1px solid #30363d", borderRadius: 4, padding: "3px 5px" };
const inputStyle: React.CSSProperties = { ...selectStyle, minWidth: 150 };
const numberInputStyle: React.CSSProperties = { ...selectStyle, width: 52 };
const detailStyle: React.CSSProperties = { marginTop: 20, padding: 12, border: "1px solid #30363d", borderRadius: 6, background: "#0d1117" };

const tableStyle: React.CSSProperties = {
  width: "100%",
  borderCollapse: "collapse",
  fontSize: 13,
};

const thStyle: React.CSSProperties = {
  textAlign: "left",
  padding: "6px 8px",
  borderBottom: "2px solid #30363d",
  color: "#8b949e",
  fontSize: 12,
  fontWeight: 600,
};

const tdStyle: React.CSSProperties = {
  padding: "5px 8px",
  color: "#c9d1d9",
};

const h2Style: React.CSSProperties = {
  fontSize: 16,
  fontWeight: 600,
  marginBottom: 12,
  color: "#c9d1d9",
};

const h3Style: React.CSSProperties = {
  fontSize: 13,
  fontWeight: 600,
  marginBottom: 8,
  color: "#8b949e",
  textTransform: "uppercase" as const,
  letterSpacing: 1,
};
