/**
 * RELATÓRIO DE INVESTIGAÇÃO - CONSISTÊNCIA DOS AGREGADOS DO PAINEL EDITORIAL
 * Data: 6 de agosto de 2026
 * 
 * Este arquivo documenta a investigação completa da cadeia de cálculo dos agregados.
 */

## ANÁLISE DA ARQUITETURA

### 1. FONTE DE DADOS

**Arquivo**: `apps/dashboard/src/app/clubs/[clubId]/(fullwidth)/overview/page.tsx`

```typescript
const [presentations, editorialPresentations, aiNarrative] = await Promise.all([
  getRecentMatchCards(clubId, 3),  // ← 3 partidas para os cards visuais
  getRecentMatchCards(clubId, 10), // ← 10 partidas para análise editorial
  fetchAiPanorama(clubId),         // ← Narrativa do LLM
]);
```

**DESCOBERTA CRÍTICA #1**: 
- Os **match cards visuais** usam apenas as **3 últimas partidas**
- Os **agregados editoriais** usam as **10 últimas partidas**
- Ambos chamam a mesma função `getRecentMatchCards()` com limites diferentes

### 2. FUNÇÃO DE BUSCA

**Arquivo**: `apps/dashboard/src/lib/services/match-card-service.ts` (linhas 168-188)

```typescript
export async function getRecentMatchCards(
  clubId: string,
  limit: number = 3,
): Promise<MatchSummaryPresentation[]> {
  const supabase = createServerSupabase();

  const { data, error } = await supabase
    .from("dashboard_editorial_presentations")
    .select("presentation")
    .eq("club_id", clubId)
    .order("played_at", { ascending: false })  // ← ORDEM CRONOLÓGICA DECRESCENTE
    .limit(limit);

  if (error || !data) {
    return [];
  }

  return data
    .map((row) => row.presentation as MatchSummaryPresentation | null)
    .filter((p): p is MatchSummaryPresentation => p != null);
}
```

**CONFIRMAÇÃO**: 
✅ A função ordena por `played_at DESC` (mais recente primeiro)
✅ Retorna exatamente `limit` partidas
✅ Fonte: tabela `match_editorial_presentations`

### 3. CÁLCULO DOS AGREGADOS

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts`

#### 3.1 Artilheiro (linhas 121-142)

```typescript
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
```

**FÓRMULA ARTILHEIRO**:
- Soma todos os gols de cada jogador nas N partidas fornecidas
- Desempate: ordem alfabética (menor nome vence)
- Nenhum critério mínimo de participação

**PROBLEMA POTENCIAL**: Não há critério mínimo de participação

#### 3.2 Garçom (linhas 178-199)

```typescript
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
```

**FÓRMULA GARÇOM**:
- Soma todas as assistências de cada jogador nas N partidas fornecidas
- Desempate: ordem alfabética (menor nome vence)
- Nenhum critério mínimo de participação

**PROBLEMA POTENCIAL**: Não há critério mínimo de participação

#### 3.3 Craque Mais Frequente (linhas 144-165)

```typescript
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
```

**FÓRMULA CRAQUE MAIS FREQUENTE**:
- **NÃO conta apenas quem ganhou o prêmio de Craque**
- Conta quantas vezes cada jogador apareceu no **top 3 (highlights)** de cada partida
- Um jogador pode aparecer até N vezes (N = número de partidas)
- Desempate: ordem alfabética (menor nome vence)

**DESCOBERTA CRÍTICA #2**: 
⚠️ O nome "Craque mais frequente" é **ENGANOSO**
- Não é "quem ganhou mais prêmios de Craque"
- É "quem mais apareceu entre os 3 destaques"
- Um jogador que foi 🥈 em 10 partidas ganha de outro que foi 🥇 em 5

#### 3.4 Melhor Média (linhas 201-230)

```typescript
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
```

**FÓRMULA MELHOR MÉDIA**:
```
média do jogador = soma das notas quando apareceu no top3 / vezes que apareceu no top3
```

**DESCOBERTA CRÍTICA #3**:
⚠️ A fórmula está **ERRADA** para a expectativa do usuário

**Expectativa do usuário**:
```
média = soma das notas em TODAS as partidas / partidas que disputou
```

**Implementação real**:
```
média = soma das notas quando ficou no TOP 3 / vezes que ficou no TOP 3
```

**PROBLEMA**:
- Se D.Prima aparece como 9.0, isso significa:
  - Ele esteve no top3 em algumas partidas
  - A média das notas SOMENTE dessas partidas é 9.0
  - **NÃO** é a média de todas as 10 partidas que ele disputou

**EXEMPLO DO PROBLEMA**:
- Jogador A: Disputou 10 partidas, ficou no top3 em 2 (notas 9.0 e 9.0) → Média: 9.0
- Jogador B: Disputou 10 partidas, ficou no top3 em 8 (notas 8.5 todas) → Média: 8.5
- **Resultado atual**: Jogador A ganha (9.0 > 8.5)
- **Resultado esperado**: Jogador B deveria ganhar (mais consistente)

**CRITÉRIO MÍNIMO**: 
⚠️ Não existe critério mínimo de participação!
- Um jogador que disputou 1 partida e tirou 9.5 ganha de outro que disputou 10 e teve média 9.0

#### 3.5 Estatísticas Gerais (linhas 94-119)

```typescript
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
```

**FÓRMULA ESTATÍSTICAS**:
- Vitórias, empates, derrotas: contagem simples
- Gols marcados/sofridos: soma simples
- Saldo de gols: gols marcados - gols sofridos
- Média de gols: gols marcados / número de partidas
- Aproveitamento: (vitórias × 3 + empates) / (partidas × 3) × 100%

✅ **Correto e esperado**

### 4. EDITORIAL DO LLM

**Arquivo Backend**: `src/main/kotlin/com/eafc26/discordstats/llm/LlmEditorialService.kt` (linha 31)

```kotlin
val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)  // PANORAMA_MATCH_COUNT = 10
val matchIds = recentMatches.map { it.matchId.value }
```

**Arquivo Backend**: `src/main/kotlin/com/eafc26/discordstats/service/MatchHistoryService.kt` (linhas 21-28)

```kotlin
fun list(query: MatchHistoryQuery = MatchHistoryQuery()): List<CanonicalMatch> {
    val comparator = if (query.order == MatchHistoryOrder.NEWEST_FIRST) {
        compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }
            .thenBy { it.matchId.value }
    } else {
        compareBy<CanonicalMatch> { it.footballMatch.playedAt }
            .thenBy { it.matchId.value }
    }
    // ...
}

fun latest(limit: Int): List<CanonicalMatch> =
    list(MatchHistoryQuery(order = MatchHistoryOrder.NEWEST_FIRST, limit = limit))
```

**CONFIRMAÇÃO**:
✅ O LLM recebe exatamente as 10 partidas mais recentes ordenadas por `playedAt DESC`
✅ A correção que fizemos hoje garantiu que a ordem cronológica seja preservada

**Arquivo Backend**: `src/main/kotlin/com/eafc26/discordstats/llm/EditorialContextBuilder.kt` (linhas 124-151)

```kotlin
private fun recentForm(recentMatches: List<CanonicalMatch>, excluding: CanonicalMatch): RecentFormContext? {
    val others = recentMatches.filter { it.matchId != excluding.matchId }
    if (others.isEmpty()) return null

    val results = others.map { canonical ->
        val interp = canonical.interpretation
        val opponent = canonical.footballMatch.participants
            .first { it.club.id == interp.result.opponentClub }
        RecentMatchResult(
            opponent = opponent.club.name?.value ?: "Adversário",
            ourScore = interp.result.ourScore.goals,
            opponentScore = interp.result.opponentScore.goals,
            outcome = interp.result.outcome,
        )
    }
    // ...
}
```

**DESCOBERTA CRÍTICA #4**:
⚠️ O contexto do LLM **exclui a partida atual** da lista de "recent form"
- Se `recentMatches` tem 10 partidas, e a partida atual é a #1
- O `recentForm` terá apenas 9 partidas (#2 até #10)

### 5. FONTE ÚNICA - VALIDAÇÃO

**RESUMO DA CADEIA**:

```
┌─────────────────────────────────────────────────────────────┐
│ ORIGEM: Tabela match_editorial_presentations                │
│ Campo de ordenação: played_at DESC                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ FUNÇÃO: getRecentMatchCards(clubId, limit)                  │
│ - Busca limit partidas                                      │
│ - Ordena por played_at DESC                                 │
│ - Retorna MatchSummaryPresentation[]                        │
└─────────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
┌───────────────────────┐   ┌───────────────────────┐
│ presentations (3)     │   │ editorialPresentations│
│ - Cards visuais       │   │ (10)                  │
│ - Não usado para      │   │ - buildSequenceEditor │
│   agregados           │   │ - Todos os agregados  │
└───────────────────────┘   └───────────────────────┘
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        │                               │                               │
        ▼                               ▼                               ▼
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│ Artilheiro   │              │ Garçom       │              │ Craque freq. │
│ computeTop   │              │ computeTop   │              │ computeTop   │
│ Scorer       │              │ Assister     │              │ Highlight    │
└──────────────┘              └──────────────┘              └──────────────┘
        │                               │                               │
        └───────────────────────────────┼───────────────────────────────┘
                                        │
                                        ▼
                            ┌──────────────────────┐
                            │ Melhor Média         │
                            │ computeTopRatedPlayer│
                            └──────────────────────┘
                                        │
                                        ▼
                            ┌──────────────────────┐
                            │ Estatísticas         │
                            │ computeStats         │
                            └──────────────────────┘
```

**RESPOSTA FINAL**: 
✅ **SIM, TODOS OS AGREGADOS USAM A MESMA LISTA DE PARTIDAS**
✅ **A lista vem de `getRecentMatchCards(clubId, 10)`**
✅ **Ordenada por `played_at DESC`**
✅ **Fonte única: `match_editorial_presentations`**

### 6. EDITORIAL DO LLM - FONTE SEPARADA

```
┌─────────────────────────────────────────────────────────────┐
│ BACKEND: historyService.latest(10)                          │
│ - Busca 10 CanonicalMatch                                   │
│ - Ordena por footballMatch.playedAt DESC                    │
│ - Fonte: memória (CanonicalMatchRepository)                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ EditorialContextBuilder.buildFullContext                    │
│ - Exclui a partida atual do recentForm                      │
│ - Cria contexto com 9 partidas antigas                      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ LLM recebe prompt com:                                       │
│ - Partida atual                                             │
│ - SEQUÊNCIA DAS ÚLTIMAS N PARTIDAS (N = 9)                  │
└─────────────────────────────────────────────────────────────┘
```

**DESCOBERTA CRÍTICA #5**:
⚠️ **O editorial do LLM usa uma fonte DIFERENTE**
- Dashboard: busca de `match_editorial_presentations` (PostgreSQL)
- LLM Backend: busca de `CanonicalMatchRepository` (memória/H2)

**POSSÍVEL INCONSISTÊNCIA**:
- Se houver diferença entre o que está em PostgreSQL e o que está em memória
- O editorial do LLM pode mencionar partidas diferentes das que aparecem no dashboard

---

## PROBLEMAS IDENTIFICADOS

### PROBLEMA #1: Melhor Média - Fórmula Errada

**Expectativa**: Média de todas as partidas disputadas
**Realidade**: Média apenas das partidas em que ficou no top3

**Exemplo**:
```
D.Prima com média 9.0:
- Ficou no top3 em 2 partidas com notas 9.0 e 9.0
- Média: (9.0 + 9.0) / 2 = 9.0
- Mas pode ter disputado 10 partidas com notas baixas nas outras 8!
```

**Impacto**: Jogadores com poucas aparições no top3 mas notas altas podem ganhar de jogadores mais consistentes.

### PROBLEMA #2: Ausência de Critério Mínimo

**Artilheiro, Garçom, Melhor Média**: Nenhum requer mínimo de participação

**Impacto**: 
- Jogador que disputou 1 partida pode ganhar de jogador que disputou 10
- Estatística não representativa do período

### PROBLEMA #3: "Craque mais frequente" é enganoso

**Nome sugere**: Quem ganhou mais prêmios de Craque
**Realidade**: Quem mais apareceu no top3 (independente da posição)

**Impacto**: Confusão semântica. Um jogador sempre em 2º ou 3º ganha de outro que foi 1º menos vezes.

### PROBLEMA #4: Editorial do LLM usa fonte diferente

**Dashboard**: PostgreSQL (`match_editorial_presentations`)
**LLM**: Memória (`CanonicalMatchRepository`)

**Impacto potencial**: Inconsistência se houver dessincronização entre as fontes.

### PROBLEMA #5: LLM recebe 9 partidas, não 10

**Context Builder** exclui a partida atual do `recentForm`
- Busca 10 partidas
- Exclui a atual
- Envia 9 ao LLM no campo `recentForm`

**Impacto**: O prompt diz "últimas 10 partidas" mas envia apenas 9 no `recentForm` (+ 1 como "partida atual").

---

## PROPOSTAS DE CORREÇÃO

### CORREÇÃO #1: Melhor Média - Usar dados de todas as partidas

**Problema**: Atualmente só considera partidas em que o jogador ficou no top3

**Opção A - Ideal mas difícil**: 
- Buscar todas as notas de todos os jogadores em todas as 10 partidas
- Requer acesso a dados não disponíveis em `MatchSummaryPresentation`
- Requer refatoração significativa

**Opção B - Pragmática**:
- Manter a lógica atual (média do top3)
- **MAS adicionar critério mínimo de participação**
- Exemplo: "Mínimo 3 aparições no top3"
- Renomear para "Melhor média entre os destaques"

**Opção C - Remover**:
- Remover o agregado "Melhor média" completamente
- Manter apenas artilheiro, garçom e craque frequente

### CORREÇÃO #2: Adicionar Critério Mínimo de Participação

**Para Artilheiro**:
```typescript
const MIN_GOALS = 2; // Mínimo 2 gols no período
// Filtrar jogadores com menos de MIN_GOALS antes de escolher o melhor
```

**Para Garçom**:
```typescript
const MIN_ASSISTS = 2; // Mínimo 2 assistências no período
```

**Para Melhor Média**:
```typescript
const MIN_HIGHLIGHT_APPEARANCES = 3; // Mínimo 3 aparições no top3
```

### CORREÇÃO #3: Renomear "Craque mais frequente"

**Opções**:
- "Destaque mais frequente"
- "Top3 mais frequente"
- "Mais vezes entre os melhores"
- Manter atual mas adicionar tooltip explicativo

### CORREÇÃO #4: Alinhar fonte do LLM

**Opção A**: LLM também busca de PostgreSQL
**Opção B**: Dashboard também usa CanonicalMatchRepository (não factível no Vercel)
**Opção C**: Garantir sincronização entre as fontes

### CORREÇÃO #5: Clarificar contagem do LLM

**No prompt**, substituir:
```
"SEQUÊNCIA DAS ÚLTIMAS 10 PARTIDAS"
```

Por:
```
"SEQUÊNCIA DAS 9 PARTIDAS ANTERIORES À ATUAL"
```

Ou buscar 11 partidas e excluir a atual, mantendo 10 no recentForm.

---

## VALIDAÇÃO NECESSÁRIA

Para confirmar se D.Prima realmente tem média 9.0:

1. Verificar quantas partidas ele apareceu no top3
2. Verificar a nota em cada uma dessas aparições
3. Confirmar se (soma das notas) / (número de aparições) = 9.0

Para confirmar se Beckham é o craque mais frequente:

1. Contar quantas vezes cada jogador apareceu no top3 (qualquer posição)
2. Verificar se Beckham realmente tem o maior número
3. Se houver empate, verificar se Beckham ganha alfabeticamente

Para confirmar a ordem cronológica das 10 partidas:

1. Buscar as 10 últimas partidas de `match_editorial_presentations` ordenadas por `played_at DESC`
2. Comparar com as partidas enviadas ao LLM
3. Verificar se há divergência

---

**FIM DO RELATÓRIO**

