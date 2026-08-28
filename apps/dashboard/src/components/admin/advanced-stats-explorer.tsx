"use client";

import { useState, useCallback } from "react";

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
  dribblesCompleted: number;
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
  unknownFields: UnknownFieldsData;
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

type View = "matches" | "players" | "detail" | "compare" | "discovery";

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
          showZeros={showZeros}
          showRaw={showRaw}
          onToggleZeros={() => setShowZeros(!showZeros)}
          onToggleRaw={() => setShowRaw(!showRaw)}
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
    </div>
  );
}

function PlayerDetailView({
  data,
  showZeros,
  showRaw,
  onToggleZeros,
  onToggleRaw,
  onBack,
}: {
  data: PlayerExplorerData;
  showZeros: boolean;
  showRaw: boolean;
  onToggleZeros: () => void;
  onToggleRaw: () => void;
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
        <StatChip label="Dribbles completed" value={data.knownStats.dribblesCompleted} />
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
            {JSON.stringify({ aggregate_0: data.rawAggregate0, aggregate_1: data.rawAggregate1 }, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
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

function percent(value: number): string { return `${(value * 100).toFixed(1)}%`; }

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
