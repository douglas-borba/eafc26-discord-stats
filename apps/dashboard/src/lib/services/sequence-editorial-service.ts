import type { MatchSummaryPresentation } from "./match-card-service";

export interface SequenceEditorial {
  title: string;
  subtitle: string;
  narrative: string;
  stats: SequenceStats;
  topScorer: { name: string; goals: number } | null;
  topHighlight: { name: string; appearances: number } | null;
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
      stats: { wins: 0, draws: 0, losses: 0, goalsScored: 0, goalsConceded: 0, goalDifference: 0, matchCount: 0, avgGoalsScored: "0.0" },
      topScorer: null,
      topHighlight: null,
    };
  }

  const stats = computeStats(presentations);
  const topScorer = computeTopScorer(presentations);
  const topHighlight = computeTopHighlight(presentations);
  const currentRun = computeCurrentRun(presentations);
  const clubName = presentations[0].ourName;

  const title = pickTitle(stats, currentRun);
  const subtitle = pickSubtitle(matchCount);
  const narrative = buildNarrative(clubName, stats, currentRun, topScorer, topHighlight);

  return { title, subtitle, narrative, stats, topScorer, topHighlight };
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

  return {
    wins, draws, losses,
    goalsScored, goalsConceded,
    goalDifference: goalsScored - goalsConceded,
    matchCount,
    avgGoalsScored,
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

function pickTitle(stats: SequenceStats, run: CurrentRun): string {
  if (stats.wins === stats.matchCount) return "Fase impecável";
  if (stats.losses === stats.matchCount) return "Momento difícil";
  if (stats.draws === stats.matchCount) return "Equilíbrio absoluto";
  if (run.count >= 2 && run.type === "WIN") return "Em alta";
  if (run.count >= 2 && run.type === "LOSS") return "Fase complicada";
  if (stats.wins > stats.losses) return "Balanço positivo";
  if (stats.losses > stats.wins) return "Momento de atenção";
  return "O momento do clube";
}

function pickSubtitle(matchCount: number): string {
  if (matchCount === 1) return "A partida mais recente";
  if (matchCount === 2) return "As duas últimas partidas";
  return "As três últimas partidas";
}

function buildNarrative(
  clubName: string,
  stats: SequenceStats,
  run: CurrentRun,
  topScorer: { name: string; goals: number } | null,
  topHighlight: { name: string; appearances: number } | null,
): string {
  const parts: string[] = [];

  if (stats.wins === stats.matchCount) {
    parts.push(`${clubName} vem de ${stats.matchCount} ${stats.matchCount === 1 ? "vitória consecutiva" : "vitórias consecutivas"} e atravessa grande fase.`);
  } else if (stats.losses === stats.matchCount) {
    parts.push(`${clubName} acumula ${stats.matchCount} ${stats.matchCount === 1 ? "derrota" : "derrotas"} ${stats.matchCount === 1 ? "recente" : "consecutivas"} e vive momento delicado.`);
  } else if (run.count >= 2 && run.type === "WIN") {
    parts.push(`${clubName} embalou com ${run.count} vitórias seguidas nas últimas partidas.`);
  } else if (run.count >= 2 && run.type === "LOSS") {
    parts.push(`${clubName} atravessa um momento complicado com ${run.count} derrotas consecutivas.`);
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
