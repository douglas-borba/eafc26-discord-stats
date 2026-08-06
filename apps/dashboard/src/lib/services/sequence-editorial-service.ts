import { createServerSupabase } from "@/lib/supabase/server";
import type { MatchSummaryPresentation } from "./match-card-service";

export interface MatchDetail {
  matchId: string;
  date: string;
  opponent: string;
  ourScore: number;
  oppScore: number;
  outcome: "WIN" | "DRAW" | "LOSS";
}

export interface SequenceEditorial {
  title: string;
  subtitle: string;
  narrative: string;
  aiNarrative: string | null;
  stats: SequenceStats;
  matchDetails: MatchDetail[];
  topScorer: { name: string; goals: number } | null;
  topAssister: { name: string; assists: number } | null;
  topHighlight: { name: string; appearances: number } | null;
  topRatedPlayer: { name: string; avgRating: string } | null;
  currentStreak: { type: "WIN" | "DRAW" | "LOSS"; count: number; label: string } | null;
}

export interface SequenceStats {
  wins: number;
  draws: number;
  losses: number;
  goalsScored: number;
  goalsConceded: number;
  goalDifference: number;
  matchCount: number;
  avgGoalsScored: string;
  pointsPercentage: string;
}

export function buildSequenceEditorial(
  presentations: MatchSummaryPresentation[],
): SequenceEditorial {
  const matchCount = presentations.length;

  if (matchCount === 0) {
    return {
      title: "Sem partidas recentes",
      subtitle: "Nenhuma partida processada ainda",
      narrative: "Aguardando os primeiros resultados para construir o panorama editorial.",
      aiNarrative: null,
      stats: { wins: 0, draws: 0, losses: 0, goalsScored: 0, goalsConceded: 0, goalDifference: 0, matchCount: 0, avgGoalsScored: "0.0", pointsPercentage: "0.0" },
      matchDetails: [],
      topScorer: null,
      topAssister: null,
      topHighlight: null,
      topRatedPlayer: null,
      currentStreak: null,
    };
  }

  const stats = computeStats(presentations);
  const matchDetails = buildMatchDetails(presentations);
  const topScorer = computeTopScorer(presentations);
  const topAssister = computeTopAssister(presentations);
  const topHighlight = computeTopHighlight(presentations);
  const topRatedPlayer = computeTopRatedPlayer(presentations);
  const currentStreak = computeCurrentStreak(presentations);
  const clubName = presentations[0].ourName;

  const title = pickTitle(stats, currentStreak);
  const subtitle = pickSubtitle(matchCount);
  const narrative = buildNarrative(clubName, stats, currentStreak, topScorer, topHighlight);

  return { title, subtitle, narrative, aiNarrative: null, stats, matchDetails, topScorer, topAssister, topHighlight, topRatedPlayer, currentStreak };
}

export async function fetchAiPanorama(clubId: string): Promise<string | null> {
  try {
    const supabase = createServerSupabase();
    const { data, error } = await supabase
      .from("dashboard_panoramas")
      .select("narrative")
      .eq("club_id", clubId)
      .order("generated_at", { ascending: false })
      .limit(1)
      .single();

    if (error || !data?.narrative) return null;
    return data.narrative as string;
  } catch {
    return null;
  }
}

function computeStats(presentations: MatchSummaryPresentation[]): SequenceStats {
  let wins = 0, draws = 0, losses = 0, goalsScored = 0, goalsConceded = 0;

  for (const p of presentations) {
    if (p.outcome.type === "WIN") wins++;
    else if (p.outcome.type === "DRAW") draws++;
    else losses++;
    goalsScored += p.ourScore;
    goalsConceded += p.oppScore;
  }

  const matchCount = presentations.length;
  const avgGoalsScored = (goalsScored / matchCount).toFixed(1);
  const points = wins * 3 + draws;
  const maxPoints = matchCount * 3;
  const pointsPercentage = ((points / maxPoints) * 100).toFixed(1);

  return {
    wins, draws, losses,
    goalsScored, goalsConceded,
    goalDifference: goalsScored - goalsConceded,
    matchCount,
    avgGoalsScored,
    pointsPercentage,
  };
}

function computeTopScorer(
  presentations: MatchSummaryPresentation[],
): { name: string; goals: number } | null {
  const tally = new Map<string, number>();

  for (const p of presentations) {
    if (!p.goals) continue;
    for (const s of p.goals.scorers) {
      tally.set(s.name, (tally.get(s.name) ?? 0) + s.count);
    }
  }

  if (tally.size === 0) return null;

  let best: { name: string; goals: number } | null = null;
  for (const [name, goals] of tally) {
    if (!best || goals > best.goals || (goals === best.goals && name < best.name)) {
      best = { name, goals };
    }
  }
  return best;
}

function computeTopHighlight(
  presentations: MatchSummaryPresentation[],
): { name: string; appearances: number } | null {
  const tally = new Map<string, number>();

  for (const p of presentations) {
    if (!p.highlights) continue;
    for (const h of p.highlights.top3) {
      tally.set(h.name, (tally.get(h.name) ?? 0) + 1);
    }
  }

  if (tally.size === 0) return null;

  let best: { name: string; appearances: number } | null = null;
  for (const [name, appearances] of tally) {
    if (!best || appearances > best.appearances || (appearances === best.appearances && name < best.name)) {
      best = { name, appearances };
    }
  }
  return best;
}

function buildMatchDetails(presentations: MatchSummaryPresentation[]): MatchDetail[] {
  return presentations.map(p => ({
    matchId: p.matchId,
    date: p.date,
    opponent: p.oppName,
    ourScore: p.ourScore,
    oppScore: p.oppScore,
    outcome: p.outcome.type,
  }));
}

function computeTopAssister(
  presentations: MatchSummaryPresentation[],
): { name: string; assists: number } | null {
  const tally = new Map<string, number>();

  for (const p of presentations) {
    if (!p.assists) continue;
    for (const a of p.assists.assisters) {
      tally.set(a.name, (tally.get(a.name) ?? 0) + a.count);
    }
  }

  if (tally.size === 0) return null;

  let best: { name: string; assists: number } | null = null;
  for (const [name, assists] of tally) {
    if (!best || assists > best.assists || (assists === best.assists && name < best.name)) {
      best = { name, assists };
    }
  }
  return best;
}

function computeTopRatedPlayer(
  presentations: MatchSummaryPresentation[],
): { name: string; avgRating: string } | null {
  const ratingSum = new Map<string, number>();
  const ratingCount = new Map<string, number>();

  for (const p of presentations) {
    if (!p.highlights?.top3) continue;
    for (const h of p.highlights.top3) {
      const rating = parseFloat(h.rating);
      if (!isNaN(rating) && rating > 0) {
        ratingSum.set(h.name, (ratingSum.get(h.name) ?? 0) + rating);
        ratingCount.set(h.name, (ratingCount.get(h.name) ?? 0) + 1);
      }
    }
  }

  if (ratingSum.size === 0) return null;

  let best: { name: string; avgRating: number } | null = null;
  for (const [name, sum] of ratingSum) {
    const count = ratingCount.get(name)!;
    const avg = sum / count;
    if (!best || avg > best.avgRating || (avg === best.avgRating && name < best.name)) {
      best = { name, avgRating: avg };
    }
  }

  return best ? { name: best.name, avgRating: best.avgRating.toFixed(2) } : null;
}

function computeCurrentStreak(
  presentations: MatchSummaryPresentation[],
): { type: "WIN" | "DRAW" | "LOSS"; count: number; label: string } | null {
  if (presentations.length === 0) return null;

  const first = presentations[0].outcome.type;
  let count = 1;
  for (let i = 1; i < presentations.length; i++) {
    if (presentations[i].outcome.type === first) count++;
    else break;
  }

  const labels: Record<string, string> = {
    WIN: count === 1 ? "1 vitória" : `${count} vitórias`,
    DRAW: count === 1 ? "1 empate" : `${count} empates`,
    LOSS: count === 1 ? "1 derrota" : `${count} derrotas`,
  };

  return { type: first, count, label: labels[first] };
}

interface CurrentRun {
  type: "WIN" | "DRAW" | "LOSS";
  count: number;
}

function computeCurrentRun(presentations: MatchSummaryPresentation[]): CurrentRun {
  const first = presentations[0].outcome.type;
  let count = 1;
  for (let i = 1; i < presentations.length; i++) {
    if (presentations[i].outcome.type === first) count++;
    else break;
  }
  return { type: first, count };
}

function pickTitle(stats: SequenceStats, streak: { type: "WIN" | "DRAW" | "LOSS"; count: number } | null): string {
  if (!streak) return "O momento do clube";
  if (stats.wins === stats.matchCount) return "Fase impecável";
  if (stats.losses === stats.matchCount) return "Momento difícil";
  if (stats.draws === stats.matchCount) return "Equilíbrio absoluto";
  if (streak.count >= 3 && streak.type === "WIN") return "Em alta";
  if (streak.count >= 3 && streak.type === "LOSS") return "Fase complicada";
  if (stats.wins > stats.losses) return "Balanço positivo";
  if (stats.losses > stats.wins) return "Momento de atenção";
  return "O momento do clube";
}

function pickSubtitle(matchCount: number): string {
  if (matchCount === 1) return "A partida mais recente";
  if (matchCount <= 3) return `As ${matchCount} últimas partidas`;
  return `As últimas ${matchCount} partidas`;
}

function buildNarrative(
  clubName: string,
  stats: SequenceStats,
  streak: { type: "WIN" | "DRAW" | "LOSS"; count: number } | null,
  topScorer: { name: string; goals: number } | null,
  topHighlight: { name: string; appearances: number } | null,
): string {
  const parts: string[] = [];

  if (!streak) return `${clubName} ainda não possui histórico suficiente.`;

  if (stats.wins === stats.matchCount) {
    parts.push(`${clubName} vem de ${stats.matchCount} ${stats.matchCount === 1 ? "vitória consecutiva" : "vitórias consecutivas"} e atravessa grande fase.`);
  } else if (stats.losses === stats.matchCount) {
    parts.push(`${clubName} acumula ${stats.matchCount} ${stats.matchCount === 1 ? "derrota" : "derrotas"} ${stats.matchCount === 1 ? "recente" : "consecutivas"} e vive momento delicado.`);
  } else if (streak.count >= 3 && streak.type === "WIN") {
    parts.push(`${clubName} embalou com ${streak.count} vitórias seguidas nas últimas partidas.`);
  } else if (streak.count >= 3 && streak.type === "LOSS") {
    parts.push(`${clubName} atravessa um momento complicado com ${streak.count} derrotas consecutivas.`);
  } else {
    parts.push(`${clubName} alterna resultados nas últimas partidas.`);
  }

  if (topScorer && topScorer.goals >= 2) {
    parts.push(`${topScorer.name} é o destaque ofensivo com ${topScorer.goals} gols no período.`);
  } else if (topScorer) {
    parts.push(`${topScorer.name} marcou nas últimas partidas.`);
  }

  if (topHighlight && topHighlight.appearances >= 2) {
    parts.push(`${topHighlight.name} apareceu ${topHighlight.appearances} vezes entre os destaques.`);
  }

  return parts.join(" ");
}
