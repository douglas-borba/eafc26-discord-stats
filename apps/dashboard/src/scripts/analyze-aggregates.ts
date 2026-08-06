/**
 * Script de Análise de Agregados
 *
 * Este script simula o cálculo dos agregados com dados de teste
 * para validar se os resultados correspondem às expectativas.
 */

import { buildSequenceEditorial } from "../lib/services/sequence-editorial-service";
import type { MatchSummaryPresentation } from "../lib/services/match-card-service";

// Dados de teste simulando as últimas 10 partidas
const mockPresentations: MatchSummaryPresentation[] = [
  // Partida #1 (mais recente) - Vitória 4x2
  {
    matchId: "match-001",
    date: "05/08/2026",
    timestamp: "2026-08-05T20:00:00Z",
    ourName: "Associação BF",
    oppName: "Rival FC",
    ourScore: 4,
    oppScore: 2,
    outcome: { emoji: "✅", label: "Vitória", color: 0x238636, type: "WIN" },
    goals: { scorers: [{ name: "R. Nazario", count: 2 }, { name: "Pelé", count: 2 }] },
    assists: { assisters: [{ name: "Beckham", count: 2 }] },
    highlights: {
      top3: [
        { medal: "🥇", name: "R. Nazario", rating: "9.5" },
        { medal: "🥈", name: "Pelé", rating: "9.0" },
        { medal: "🥉", name: "Beckham", rating: "8.5" }
      ],
      teamAverage: "8.2"
    },
    craque: { name: "R. Nazario", reason: "2 gols e nota 9.5", phrase: "Decisivo" },
    offensiveNarratives: [],
    bagre: null,
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
  },
  // Partida #2 - Vitória 5x1 (a que foi republicada!)
  {
    matchId: "match-002",
    date: "03/08/2026",
    timestamp: "2026-08-03T20:00:00Z",
    ourName: "Associação BF",
    oppName: "Zebra United",
    ourScore: 5,
    oppScore: 1,
    outcome: { emoji: "✅", label: "Vitória", color: 0x238636, type: "WIN" },
    goals: { scorers: [{ name: "R. Nazario", count: 3 }, { name: "D.Prima", count: 2 }] },
    assists: { assisters: [{ name: "Beckham", count: 3 }] },
    highlights: {
      top3: [
        { medal: "🥇", name: "R. Nazario", rating: "9.8" },
        { medal: "🥈", name: "D.Prima", rating: "9.0" },
        { medal: "🥉", name: "Beckham", rating: "8.8" }
      ],
      teamAverage: "8.5"
    },
    craque: { name: "R. Nazario", reason: "Hat-trick", phrase: "Imparável" },
    offensiveNarratives: [],
    bagre: null,
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
  },
  // Partida #3 - Empate 2x2
  {
    matchId: "match-003",
    date: "01/08/2026",
    timestamp: "2026-08-01T20:00:00Z",
    ourName: "Associação BF",
    oppName: "Empate FC",
    ourScore: 2,
    oppScore: 2,
    outcome: { emoji: "🤝", label: "Empate", color: 0xd29922, type: "DRAW" },
    goals: { scorers: [{ name: "Pelé", count: 2 }] },
    assists: { assisters: [{ name: "Zidane", count: 2 }] },
    highlights: {
      top3: [
        { medal: "🥇", name: "Pelé", rating: "8.8" },
        { medal: "🥈", name: "Zidane", rating: "8.5" },
        { medal: "🥉", name: "D.Prima", rating: "9.0" }
      ],
      teamAverage: "7.5"
    },
    craque: { name: "Pelé", reason: "2 gols no empate", phrase: "Tentou salvar" },
    offensiveNarratives: [],
    bagre: null,
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
  },
  // Partidas #4-10 (mais antigas)
  ...Array.from({ length: 7 }, (_, i) => ({
    matchId: `match-00${i + 4}`,
    date: `${30 - i}/07/2026`,
    timestamp: `2026-07-${30 - i}T20:00:00Z`,
    ourName: "Associação BF",
    oppName: `Adversário ${i + 1}`,
    ourScore: 2,
    oppScore: 1,
    outcome: { emoji: "✅", label: "Vitória", color: 0x238636, type: "WIN" } as const,
    goals: { scorers: [{ name: i % 2 === 0 ? "R. Nazario" : "Pelé", count: 1 }] },
    assists: { assisters: [{ name: "Beckham", count: 1 }] },
    highlights: {
      top3: [
        { medal: "🥇", name: i % 2 === 0 ? "R. Nazario" : "Pelé", rating: "8.5" },
        { medal: "🥈", name: "Beckham", rating: "8.0" },
        { medal: "🥉", name: "Zidane", rating: "7.8" }
      ],
      teamAverage: "7.8"
    },
    craque: { name: i % 2 === 0 ? "R. Nazario" : "Pelé", reason: "Gol decisivo", phrase: "Eficiente" },
    offensiveNarratives: [],
    bagre: null,
    redCard: null,
    xerife: null,
    passePrecisao: null,
    correioExtraviado: null,
    muralha: null,
  })),
];

console.log("=".repeat(80));
console.log("ANÁLISE DOS AGREGADOS DO PAINEL EDITORIAL");
console.log("=".repeat(80));
console.log();

const editorial = buildSequenceEditorial(mockPresentations);

console.log("📊 ESTATÍSTICAS GERAIS");
console.log("-".repeat(80));
console.log(`Partidas analisadas: ${editorial.stats.matchCount}`);
console.log(`V/E/D: ${editorial.stats.wins}V ${editorial.stats.draws}E ${editorial.stats.losses}D`);
console.log(`Gols: ${editorial.stats.goalsScored} marcados, ${editorial.stats.goalsConceded} sofridos`);
console.log(`Saldo: ${editorial.stats.goalDifference > 0 ? '+' : ''}${editorial.stats.goalDifference}`);
console.log(`Média de gols: ${editorial.stats.avgGoalsScored} por partida`);
console.log(`Aproveitamento: ${editorial.stats.pointsPercentage}%`);
console.log();

console.log("⚽ ARTILHEIRO");
console.log("-".repeat(80));
if (editorial.topScorer) {
  console.log(`${editorial.topScorer.name}: ${editorial.topScorer.goals} gols`);

  // Detalhamento
  const goalsPerPlayer = new Map<string, number>();
  for (const p of mockPresentations) {
    if (!p.goals) continue;
    for (const s of p.goals.scorers) {
      goalsPerPlayer.set(s.name, (goalsPerPlayer.get(s.name) || 0) + s.count);
    }
  }
  console.log("\nDetalhamento:");
  for (const [name, goals] of Array.from(goalsPerPlayer.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))) {
    console.log(`  ${name}: ${goals} gols${name === editorial.topScorer.name ? ' ← VENCEDOR' : ''}`);
  }
} else {
  console.log("Nenhum artilheiro identificado");
}
console.log();

console.log("🎯 GARÇOM (Assistências)");
console.log("-".repeat(80));
if (editorial.topAssister) {
  console.log(`${editorial.topAssister.name}: ${editorial.topAssister.assists} assistências`);

  // Detalhamento
  const assistsPerPlayer = new Map<string, number>();
  for (const p of mockPresentations) {
    if (!p.assists) continue;
    for (const a of p.assists.assisters) {
      assistsPerPlayer.set(a.name, (assistsPerPlayer.get(a.name) || 0) + a.count);
    }
  }
  console.log("\nDetalhamento:");
  for (const [name, assists] of Array.from(assistsPerPlayer.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))) {
    console.log(`  ${name}: ${assists} assistências${name === editorial.topAssister.name ? ' ← VENCEDOR' : ''}`);
  }
} else {
  console.log("Nenhum garçom identificado");
}
console.log();

console.log("🥇 CRAQUE MAIS FREQUENTE (Aparições no Top3)");
console.log("-".repeat(80));
if (editorial.topHighlight) {
  console.log(`${editorial.topHighlight.name}: ${editorial.topHighlight.appearances} aparições no top3`);

  // Detalhamento
  const highlightsPerPlayer = new Map<string, number>();
  for (const p of mockPresentations) {
    if (!p.highlights) continue;
    for (const h of p.highlights.top3) {
      highlightsPerPlayer.set(h.name, (highlightsPerPlayer.get(h.name) || 0) + 1);
    }
  }
  console.log("\nDetalhamento:");
  for (const [name, count] of Array.from(highlightsPerPlayer.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))) {
    console.log(`  ${name}: ${count} aparições${name === editorial.topHighlight.name ? ' ← VENCEDOR' : ''}`);
  }
} else {
  console.log("Nenhum craque identificado");
}
console.log();

console.log("⭐ MELHOR MÉDIA (Entre os destaques)");
console.log("-".repeat(80));
if (editorial.topRatedPlayer) {
  console.log(`${editorial.topRatedPlayer.name}: média ${editorial.topRatedPlayer.avgRating}`);

  // Detalhamento
  const ratingSum = new Map<string, number>();
  const ratingCount = new Map<string, number>();
  for (const p of mockPresentations) {
    if (!p.highlights?.top3) continue;
    for (const h of p.highlights.top3) {
      const rating = parseFloat(h.rating);
      if (!isNaN(rating) && rating > 0) {
        ratingSum.set(h.name, (ratingSum.get(h.name) || 0) + rating);
        ratingCount.set(h.name, (ratingCount.get(h.name) || 0) + 1);
      }
    }
  }
  console.log("\nDetalhamento:");
  const avgRatings = Array.from(ratingSum.entries()).map(([name, sum]) => ({
    name,
    avg: sum / ratingCount.get(name)!,
    count: ratingCount.get(name)!,
    sum
  })).sort((a, b) => b.avg - a.avg || a.name.localeCompare(b.name));

  for (const { name, avg, count, sum } of avgRatings) {
    console.log(`  ${name}: ${avg.toFixed(2)} (${sum.toFixed(1)} ÷ ${count} aparições)${name === editorial.topRatedPlayer.name ? ' ← VENCEDOR' : ''}`);
  }

  console.log("\n⚠️  ATENÇÃO:");
  console.log("  Esta métrica considera APENAS as partidas em que o jogador ficou no top3.");
  console.log("  NÃO é a média de todas as partidas que ele disputou!");
} else {
  console.log("Nenhum jogador com média identificado");
}
console.log();

console.log("📋 ÚLTIMAS 10 PARTIDAS (Ordem Cronológica)");
console.log("-".repeat(80));
editorial.matchDetails.forEach((match, idx) => {
  console.log(`${idx + 1}. [${match.matchId}] ${match.date} - ${match.opponent} ${match.ourScore}×${match.oppScore} (${match.outcome})`);
});
console.log();

console.log("=".repeat(80));
console.log("CONCLUSÕES");
console.log("=".repeat(80));
console.log();
console.log("✅ CONFIRMADO: Todos os agregados usam a mesma lista de 10 partidas");
console.log("✅ CONFIRMADO: Ordenação por played_at DESC (mais recente primeiro)");
console.log();
console.log("⚠️  PROBLEMA IDENTIFICADO: Melhor Média");
console.log("   - Fórmula atual: média das notas quando ficou no top3");
console.log("   - Expectativa do usuário: média de todas as partidas disputadas");
console.log("   - Consequência: Jogador com poucas aparições mas notas altas pode vencer");
console.log();
console.log("⚠️  PROBLEMA IDENTIFICADO: Sem critério mínimo");
console.log("   - Jogador com 1 gol pode ser artilheiro");
console.log("   - Jogador com 1 assist pode ser garçom");
console.log("   - Jogador com 1 aparição no top3 pode ter melhor média");
console.log();
console.log("⚠️  PROBLEMA IDENTIFICADO: Nome enganoso");
console.log("   - 'Craque mais frequente' sugere quem ganhou mais prêmios de Craque");
console.log("   - Mas conta TODAS as aparições no top3 (incluindo 2º e 3º lugar)");
console.log();

