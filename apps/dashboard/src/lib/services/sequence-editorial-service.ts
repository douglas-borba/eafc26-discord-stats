import type { MatchSummaryPresentation } from "./match-card-service";

// Critérios mínimos
const MIN_GOALS = 2;
const MIN_ASSISTS = 2;
const MIN_TOP3_APPEARANCES = 3;
const MIN_MATCHES_FOR_AVG = 5;

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

/**
 * Agregado de estatísticas de um jogador durante o período.
 * Evita loops duplicados e centraliza toda a lógica de agregação.
 */
interface PlayerPeriodAggregate {
  name: string;
  matchesPlayed: number;
  goals: number;
  assists: number;
  ratingSum: number;
  ratingCount: number;
  averageRating: number;
  mvpCount: number;
  top3Count: number;
}

/**
 * Constrói agregados para todos os jogadores em um único loop.
 * Esta é a ÚNICA função que percorre as partidas para estatísticas de jogadores.
 */
function buildPlayerAggregates(presentations: MatchSummaryPresentation[]): Map<string, PlayerPeriodAggregate> {
  const aggregates = new Map<string, PlayerPeriodAggregate>();

  for (const p of presentations) {
    // Gols
    if (p.goals) {
      for (const scorer of p.goals.scorers) {
        const agg = getOrCreate(aggregates, scorer.name);
        agg.goals += scorer.count;
      }
    }

    // Assistências
    if (p.assists) {
      for (const assister of p.assists.assisters) {
        const agg = getOrCreate(aggregates, assister.name);
        agg.assists += assister.count;
      }
    }

    // MVP (prêmio de Craque - medalha de ouro)
    if (p.craque) {
      const agg = getOrCreate(aggregates, p.craque.name);
      agg.mvpCount += 1;
    }

    // Top3 e Notas (de todos os jogadores, não só top3)
    if (p.highlights?.top3) {
      for (const highlight of p.highlights.top3) {
        const agg = getOrCreate(aggregates, highlight.name);
        agg.top3Count += 1;
        const rating = parseFloat(highlight.rating.replace(',', '.'));
        if (!isNaN(rating) && rating > 0) {
          agg.ratingSum += rating;
          agg.ratingCount += 1;
        }
      }
    }

    // Notas de TODOS os jogadores (não só top3)
    if (p.allPlayers) {
      for (const player of p.allPlayers) {
        const agg = getOrCreate(aggregates, player.name);
        agg.matchesPlayed += 1;

        const rating = parseFloat(player.rating.replace(',', '.'));
        if (!isNaN(rating) && rating > 0 && rating !== 3.0) { // Ignora sentinela 3.0
          agg.ratingSum += rating;
          agg.ratingCount += 1;
        }
      }
    } else {
      // Fallback: se allPlayers não existe, conta apenas quem apareceu no top3
      // (para compatibilidade com apresentações antigas)
      if (p.highlights?.top3) {
        for (const highlight of p.highlights.top3) {
          const agg = getOrCreate(aggregates, highlight.name);
          agg.matchesPlayed += 1;
        }
      }
    }
  }

  // Calcula médias
  for (const agg of aggregates.values()) {
    agg.averageRating = agg.ratingCount > 0 ? agg.ratingSum / agg.ratingCount : 0;
  }

  return aggregates;
}

function getOrCreate(map: Map<string, PlayerPeriodAggregate>, name: string): PlayerPeriodAggregate {
  if (!map.has(name)) {
    map.set(name, {
      name,
      matchesPlayed: 0,
      goals: 0,
      assists: 0,
      ratingSum: 0,
      ratingCount: 0,
      averageRating: 0,
      mvpCount: 0,
      top3Count: 0,
    });
  }
  return map.get(name)!;
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

  // Constrói agregados de TODOS os jogadores em um único loop
  const playerAggregates = buildPlayerAggregates(presentations);

  const stats = computeStats(presentations);
  const matchDetails = buildMatchDetails(presentations);
  const topScorer = computeTopScorer(playerAggregates);
  const topAssister = computeTopAssister(playerAggregates);
  const topHighlight = computeTopHighlight(playerAggregates);
  const topRatedPlayer = computeTopRatedPlayer(playerAggregates);
  const currentStreak = computeCurrentStreak(presentations);
  const clubName = presentations[0].ourName;

  const title = pickTitle(stats, currentStreak);
  const subtitle = pickSubtitle(matchCount);
  const narrative = buildNarrative(clubName, stats, currentStreak, topScorer, topHighlight);

  return { title, subtitle, narrative, aiNarrative: null, stats, matchDetails, topScorer, topAssister, topHighlight, topRatedPlayer, currentStreak };
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
  aggregates: Map<string, PlayerPeriodAggregate>,
): { name: string; goals: number } | null {
  const qualified = Array.from(aggregates.values())
    .filter(agg => agg.goals >= MIN_GOALS);

  if (qualified.length === 0) return null;

  let best: { name: string; goals: number } | null = null;
  for (const agg of qualified) {
    if (!best || agg.goals > best.goals || (agg.goals === best.goals && agg.name < best.name)) {
      best = { name: agg.name, goals: agg.goals };
    }
  }
  return best;
}

function computeTopHighlight(
  aggregates: Map<string, PlayerPeriodAggregate>,
): { name: string; appearances: number } | null {
  // Agora conta apenas quem ganhou o prêmio de Craque (MVP)
  const qualified = Array.from(aggregates.values())
    .filter(agg => agg.mvpCount > 0);

  if (qualified.length === 0) return null;

  let best: { name: string; appearances: number } | null = null;
  for (const agg of qualified) {
    if (!best || agg.mvpCount > best.appearances || (agg.mvpCount === best.appearances && agg.name < best.name)) {
      best = { name: agg.name, appearances: agg.mvpCount };
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
  aggregates: Map<string, PlayerPeriodAggregate>,
): { name: string; assists: number } | null {
  const qualified = Array.from(aggregates.values())
    .filter(agg => agg.assists >= MIN_ASSISTS);

  if (qualified.length === 0) return null;

  let best: { name: string; assists: number } | null = null;
  for (const agg of qualified) {
    if (!best || agg.assists > best.assists || (agg.assists === best.assists && agg.name < best.name)) {
      best = { name: agg.name, assists: agg.assists };
    }
  }
  return best;
}

function computeTopRatedPlayer(
  aggregates: Map<string, PlayerPeriodAggregate>,
): { name: string; avgRating: string } | null {
  // Requer mínimo de 3 aparições no Top3
  const qualified = Array.from(aggregates.values())
    .filter(agg => agg.top3Count >= MIN_TOP3_APPEARANCES && agg.ratingCount > 0);

  if (qualified.length === 0) return null;

  let best: { name: string; avgRating: number } | null = null;
  for (const agg of qualified) {
    const avg = agg.averageRating;
    if (!best || avg > best.avgRating || (avg === best.avgRating && agg.name < best.name)) {
      best = { name: agg.name, avgRating: avg };
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
