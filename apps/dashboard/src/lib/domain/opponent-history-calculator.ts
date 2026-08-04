/**
 * Pure domain logic for opponent history aggregations.
 * Ported from OpponentHistoryService.kt
 *
 * This module calculates:
 * - currentRun: terminal sequence (winning, unbeaten, or winless)
 * - runRecords: historical maximum sequences of each type
 * - playerLeaders: top performers in goals, assists, Craques, and Xerifes
 *
 * All functions are pure and server-side only. No React dependencies.
 */

import type {
  OpponentRun,
  OpponentRunRecord,
  OpponentPlayerLeaders,
  MatchSummary,
} from "@/lib/domain/types";

/**
 * Match data required for run calculations.
 */
interface MatchForRuns {
  matchId: string;
  playedAt: string;
  outcome: "WIN" | "DRAW" | "LOSS";
}

/**
 * Player statistics for aggregation.
 */
interface PlayerStats {
  playerId: string;
  displayName: string;
  goals: number;
  assists: number;
}

/**
 * Award winner data from payload.
 */
interface AwardWinner {
  type: "CRAQUE" | "XERIFE";
  winnerId: string | null;
  awarded: boolean;
}

/**
 * Run types matching Kotlin OpponentRunType enum.
 */
type RunType = "WINNING" | "UNBEATEN" | "WINLESS";

/**
 * Sort matches chronologically (oldest first).
 * Kotlin: sortedCanonicalNewestFirst().asReversed()
 */
function sortChronologically(matches: MatchForRuns[]): MatchForRuns[] {
  return [...matches].sort((a, b) => {
    const timeCompare = a.playedAt.localeCompare(b.playedAt);
    if (timeCompare !== 0) return timeCompare;
    return a.matchId.localeCompare(b.matchId);
  });
}

/**
 * Check if a match satisfies a run type.
 * Kotlin: CanonicalMatch.matches(type: OpponentRunType)
 */
function matchesRunType(match: MatchForRuns, type: RunType): boolean {
  switch (type) {
    case "WINNING":
      return match.outcome === "WIN";
    case "UNBEATEN":
      return match.outcome !== "LOSS";
    case "WINLESS":
      return match.outcome !== "WIN";
  }
}

/**
 * Get label for current run.
 * Kotlin: OpponentRunType.currentLabel(count: Int)
 */
function getCurrentRunLabel(type: RunType, count: number): string {
  switch (type) {
    case "WINNING":
      return `${count} vitórias consecutivas`;
    case "UNBEATEN":
      return `${count} jogos sem perder`;
    case "WINLESS":
      return `${count} jogos sem vencer`;
  }
}

/**
 * Get label for historical record.
 * Kotlin: OpponentRunType.recordLabel(count: Int)
 */
function getRecordRunLabel(type: RunType, count: number): string {
  switch (type) {
    case "WINNING":
      return `Maior sequência de vitórias: ${count}`;
    case "UNBEATEN":
      return `Maior sequência invicta: ${count}`;
    case "WINLESS":
      return `Maior sequência sem vencer: ${count}`;
  }
}

/**
 * Calculate current (terminal) run.
 * Kotlin: List<CanonicalMatch>.currentRun()
 *
 * Returns the longest terminal sequence among all run types.
 * If tied, prefers: WINNING > UNBEATEN > WINLESS (by ordinal).
 * Requires at least 2 total matches and 2 consecutive matches.
 */
export function calculateCurrentRun(matches: MatchSummary[]): OpponentRun | null {
  if (matches.length < 2) return null;

  const ordered = sortChronologically(matches);
  const runTypes: RunType[] = ["WINNING", "UNBEATEN", "WINLESS"];

  // Collect terminal sequences for each type
  const candidates: Array<{ type: RunType; sequence: MatchForRuns[] }> = runTypes
    .map((type) => {
      // takeLastWhile equivalent
      const sequence: MatchForRuns[] = [];
      for (let i = ordered.length - 1; i >= 0; i--) {
        if (matchesRunType(ordered[i], type)) {
          sequence.unshift(ordered[i]);
        } else {
          break;
        }
      }
      return { type, sequence };
    })
    .filter((candidate) => candidate.sequence.length >= 2);

  if (candidates.length === 0) return null;

  // Select best: longest first, then by type priority (WINNING=0, UNBEATEN=1, WINLESS=2)
  const best = candidates.reduce((prev, curr) => {
    if (curr.sequence.length > prev.sequence.length) return curr;
    if (curr.sequence.length < prev.sequence.length) return prev;
    // Tie: prefer lower ordinal (WINNING > UNBEATEN > WINLESS)
    const prevOrdinal = runTypes.indexOf(prev.type);
    const currOrdinal = runTypes.indexOf(curr.type);
    return currOrdinal < prevOrdinal ? curr : prev;
  });

  return {
    type: best.type,
    label: getCurrentRunLabel(best.type, best.sequence.length),
    count: best.sequence.length,
    matchIds: best.sequence.map((m) => m.matchId),
    tiedRuns: 1,
  };
}

/**
 * Calculate historical record for a specific run type.
 * Kotlin: List<CanonicalMatch>.recordRun(type: OpponentRunType)
 *
 * Finds all sequences of the given type, returns the maximum length.
 * Preserves all sequences that tie for maximum.
 * Requires at least 2 total matches and maximum >= 2.
 */
export function calculateRunRecord(
  matches: MatchSummary[],
  type: RunType
): OpponentRunRecord | null {
  if (matches.length < 2) return null;

  const ordered = sortChronologically(matches);
  const runs: MatchForRuns[][] = [];
  let current: MatchForRuns[] = [];

  for (const match of ordered) {
    if (matchesRunType(match, type)) {
      current.push(match);
    } else {
      if (current.length > 0) {
        runs.push(current);
        current = [];
      }
    }
  }
  if (current.length > 0) runs.push(current);

  const maximum = Math.max(...runs.map((run) => run.length), 0);
  if (maximum < 2) return null;

  const recordRuns = runs.filter((run) => run.length === maximum);

  return {
    type,
    label: getRecordRunLabel(type, maximum),
    count: maximum,
    matchIds: recordRuns.flat().map((m) => m.matchId),
    tiedRuns: recordRuns.length,
  };
}

/**
 * Calculate all run records (WINNING, UNBEATEN, WINLESS).
 * Kotlin: OpponentRunType.entries.mapNotNull { type -> oldest.recordRun(type) }
 */
export function calculateRunRecords(matches: MatchSummary[]): OpponentRunRecord[] {
  const types: RunType[] = ["WINNING", "UNBEATEN", "WINLESS"];
  return types
    .map((type) => calculateRunRecord(matches, type))
    .filter((record): record is OpponentRunRecord => record !== null);
}

/**
 * Get label for leader type.
 * Kotlin: OpponentLeaderType.label()
 */
function getLeaderLabel(type: "GOALS" | "ASSISTS" | "CRAQUES" | "XERIFES"): string {
  switch (type) {
    case "GOALS":
      return "Artilharia contra o adversário";
    case "ASSISTS":
      return "Liderança em assistências";
    case "CRAQUES":
      return "Mais vezes Craque";
    case "XERIFES":
      return "Mais vezes Xerife";
  }
}

/**
 * Build a leader entry from aggregated data.
 * Kotlin: leaders(type, values, names)
 *
 * Returns null if no players have positive values.
 * Preserves all players tied for maximum.
 * Sorts by displayName (case-insensitive) then playerId.
 */
function buildLeaders(
  type: "GOALS" | "ASSISTS" | "CRAQUES" | "XERIFES",
  values: Map<string, number>,
  names: Map<string, string>
): OpponentPlayerLeaders | null {
  const maximum = Math.max(...Array.from(values.values()), 0);
  if (maximum <= 0) return null;

  const topPlayers = Array.from(values.entries())
    .filter(([, value]) => value === maximum)
    .map(([playerId]) => ({
      playerId,
      name: names.get(playerId) ?? playerId,
    }))
    .sort((a, b) => {
      const nameCompare = a.name.toLowerCase().localeCompare(b.name.toLowerCase());
      if (nameCompare !== 0) return nameCompare;
      return a.playerId.localeCompare(b.playerId);
    });

  return {
    type,
    label: getLeaderLabel(type),
    value: maximum,
    players: topPlayers,
  };
}

/**
 * Calculate player leaders from statistics.
 * Kotlin: List<CanonicalMatch>.playerLeaders()
 *
 * Aggregates goals, assists, and awards (Craque, Xerife) across all matches.
 * Returns leaders for each category that has positive values.
 */
export function calculatePlayerLeaders(
  playerStats: PlayerStats[],
  awards: AwardWinner[]
): OpponentPlayerLeaders[] {
  const names = new Map<string, string>();
  const goals = new Map<string, number>();
  const assists = new Map<string, number>();
  const craques = new Map<string, number>();
  const xerifes = new Map<string, number>();

  // Aggregate player statistics
  for (const stat of playerStats) {
    names.set(stat.playerId, stat.displayName);
    goals.set(stat.playerId, (goals.get(stat.playerId) ?? 0) + stat.goals);
    assists.set(stat.playerId, (assists.get(stat.playerId) ?? 0) + stat.assists);
  }

  // Aggregate awards
  for (const award of awards) {
    if (!award.awarded || !award.winnerId) continue;

    names.set(award.winnerId, names.get(award.winnerId) ?? award.winnerId);

    if (award.type === "CRAQUE") {
      craques.set(award.winnerId, (craques.get(award.winnerId) ?? 0) + 1);
    } else if (award.type === "XERIFE") {
      xerifes.set(award.winnerId, (xerifes.get(award.winnerId) ?? 0) + 1);
    }
  }

  // Build leader entries (filter out nulls)
  return [
    buildLeaders("GOALS", goals, names),
    buildLeaders("ASSISTS", assists, names),
    buildLeaders("CRAQUES", craques, names),
    buildLeaders("XERIFES", xerifes, names),
  ].filter((leader): leader is OpponentPlayerLeaders => leader !== null);
}

/**
 * Extract awards from match payload.
 * Returns Craque and Xerife winners if awarded.
 */
export function extractAwards(payload: {
  interpretation?: {
    awards?: {
      craque?: { awarded?: boolean; winnerId?: string | null };
      xerife?: { awarded?: boolean; winnerId?: string | null };
    };
  };
}): AwardWinner[] {
  const awards: AwardWinner[] = [];
  const interpretation = payload.interpretation;
  if (!interpretation?.awards) return awards;

  if (interpretation.awards.craque?.awarded && interpretation.awards.craque.winnerId) {
    awards.push({
      type: "CRAQUE",
      winnerId: interpretation.awards.craque.winnerId,
      awarded: true,
    });
  }

  if (interpretation.awards.xerife?.awarded && interpretation.awards.xerife.winnerId) {
    awards.push({
      type: "XERIFE",
      winnerId: interpretation.awards.xerife.winnerId,
      awarded: true,
    });
  }

  return awards;
}


