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

type View = "matches" | "players" | "detail" | "compare";

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
