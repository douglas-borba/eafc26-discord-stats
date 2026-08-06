# 📊 RELATÓRIO TÉCNICO FINAL - CONSISTÊNCIA DOS AGREGADOS DO PAINEL EDITORIAL

**Data**: 6 de agosto de 2026  
**Status**: Investigação Completa - Aguardando Aprovação para Implementação  
**Autor**: Análise Técnica Automatizada

---

## 🎯 RESUMO EXECUTIVO

### Questões Investigadas

1. ✅ **Todos os agregados usam a mesma fonte de dados?** → **SIM**
2. ✅ **A ordenação é cronológica por `played_at`?** → **SIM**
3. ⚠️ **Melhor média (D.Prima com 9.0) está correto?** → **PARCIALMENTE** (fórmula diverge da expectativa)
4. ⚠️ **Craque mais frequente (Beckham) considera apenas prêmios de Craque?** → **NÃO** (conta todo o top3)
5. ✅ **Editorial do LLM usa as mesmas 10 partidas?** → **QUASE** (usa fonte diferente, mas ordem correta)
6. ⚠️ **Existe critério mínimo de participação?** → **NÃO** (problema identificado)

### Problemas Identificados

| # | Problema | Severidade | Status |
|---|----------|------------|--------|
| 1 | Melhor Média calcula apenas sobre top3 | 🔴 Alta | Aguardando correção |
| 2 | Sem critério mínimo de participação | 🟡 Média | Aguardando correção |
| 3 | Nome "Craque mais frequente" é enganoso | 🟡 Média | Aguardando renomeação |
| 4 | LLM usa fonte de dados diferente | 🟡 Média | Mitigado com correção de hoje |
| 5 | LLM recebe 9 partidas no recentForm | 🟢 Baixa | Clarificar documentação |

---

## 📋 PARTE 1: FONTE ÚNICA - VALIDAÇÃO COMPLETA

### 1.1 Arquitetura Atual

```
┌─────────────────────────────────────────────────────────────┐
│ ORIGEM: Tabela match_editorial_presentations                │
│ - Campo club_id: identificador do clube                     │
│ - Campo played_at: timestamp canônico da EA                 │
│ - Campo presentation: JSONB com MatchSummaryPresentation   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ FUNÇÃO: getRecentMatchCards(clubId, limit)                  │
│                                                              │
│ Query:                                                       │
│   SELECT presentation                                        │
│   FROM dashboard_editorial_presentations                    │
│   WHERE club_id = ?                                         │
│   ORDER BY played_at DESC    ← Ordem cronológica           │
│   LIMIT ?                                                    │
└─────────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
┌───────────────────────┐   ┌───────────────────────┐
│ presentations (3)     │   │ editorialPresentations│
│                       │   │ (10)                  │
│ Para Match Cards      │   │ Para Agregados        │
│ visuais               │   │ e Editorial           │
└───────────────────────┘   └───────────────────────┘
                                        │
                                        ▼
                    ┌───────────────────────────────┐
                    │ buildSequenceEditorial()      │
                    │                               │
                    │ Recebe: presentations[]       │
                    │ Retorna: SequenceEditorial    │
                    └───────────────────────────────┘
                                        │
        ┌───────────┬──────────┬───────┴──────┬──────────┐
        │           │          │              │          │
        ▼           ▼          ▼              ▼          ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│Artilheiro│  │  Garçom  │  │ Craque   │  │  Média   │  │  Stats   │
│   (gols) │  │(assists) │  │   Freq.  │  │(ratings) │  │  (V/E/D) │
└──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

### 1.2 Código de Busca

**Arquivo**: `apps/dashboard/src/lib/services/match-card-service.ts`

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
    .order("played_at", { ascending: false })  // ← ORDEM CRONOLÓGICA
    .limit(limit);

  if (error || !data) {
    return [];
  }

  return data
    .map((row) => row.presentation as MatchSummaryPresentation | null)
    .filter((p): p is MatchSummaryPresentation => p != null);
}
```

### 1.3 Uso na Página

**Arquivo**: `apps/dashboard/src/app/clubs/[clubId]/(fullwidth)/overview/page.tsx`

```typescript
const [presentations, editorialPresentations, aiNarrative] = await Promise.all([
  getRecentMatchCards(clubId, 3),   // ← 3 para cards visuais
  getRecentMatchCards(clubId, 10),  // ← 10 para análise editorial
  fetchAiPanorama(clubId),          // ← Narrativa do LLM
]);

const editorial = buildSequenceEditorial(editorialPresentations);
editorial.aiNarrative = aiNarrative;
```

### ✅ CONFIRMAÇÃO

**TODOS os agregados do painel editorial utilizam EXATAMENTE a mesma lista de 10 partidas:**
- ✅ Artilheiro
- ✅ Garçom
- ✅ Craque mais frequente
- ✅ Melhor média
- ✅ Estatísticas gerais (V/E/D, gols, aproveitamento)
- ✅ Últimas 10 partidas (dots com resultado)

**Fonte única**: `getRecentMatchCards(clubId, 10)`  
**Ordenação**: `played_at DESC` (mais recente primeiro)  
**Origem**: Tabela `match_editorial_presentations`

---

## ⚽ PARTE 2: ARTILHEIRO - ANÁLISE DETALHADA

### 2.1 Fórmula Implementada

```
artilheiro = jogador com MAIS GOLS nas últimas 10 partidas
critério_desempate = ordem alfabética (A < Z)
critério_mínimo = NENHUM ⚠️
```

### 2.2 Código

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (linhas 121-142)

```typescript
function computeTopScorer(
  presentations: MatchSummaryPresentation[],
): { name: string; goals: number } | null {
  const tally = new Map<string, number>();

  // Soma gols de cada jogador
  for (const p of presentations) {
    if (!p.goals) continue;
    for (const s of p.goals.scorers) {
      tally.set(s.name, (tally.get(s.name) ?? 0) + s.count);
    }
  }

  if (tally.size === 0) return null;

  // Encontra o melhor
  let best: { name: string; goals: number } | null = null;
  for (const [name, goals] of tally) {
    if (!best || goals > best.goals || (goals === best.goals && name < best.name)) {
      best = { name, goals };
    }
  }
  return best;
}
```

### 2.3 Exemplo de Cálculo

**Entrada**: 10 partidas

| Partida | Gols |
|---------|------|
| #1 | R. Nazario (2), Pelé (2) |
| #2 | R. Nazario (3), D.Prima (2) |
| #3 | Pelé (2) |
| #4-10 | R. Nazario (1) em 4 partidas, Pelé (1) em 3 partidas |

**Cálculo**:
```
R. Nazario: 2 + 3 + 1 + 1 + 1 + 1 = 9 gols
Pelé:       2 + 2 + 1 + 1 + 1     = 7 gols
D.Prima:    2                     = 2 gols
```

**Resultado**: R. Nazario com 9 gols ✅

### ⚠️ 2.4 PROBLEMA IDENTIFICADO

**Ausência de critério mínimo de participação**

**Cenário problemático**:
```
Jogador A: 1 gol em 1 partida
Jogador B: 1 gol em 10 partidas
```

**Resultado atual**: Empate → Ganha alfabeticamente  
**Problema**: Não considera consistência ou número de partidas disputadas

### 💡 2.5 PROPOSTA DE CORREÇÃO

```typescript
const MIN_GOALS = 2; // Mínimo 2 gols no período

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

  // ✅ NOVO: Filtrar jogadores que não atingiram o mínimo
  const qualified = Array.from(tally.entries())
    .filter(([, goals]) => goals >= MIN_GOALS);

  if (qualified.length === 0) return null;

  // Encontrar o melhor entre os qualificados
  let best: { name: string; goals: number } | null = null;
  for (const [name, goals] of qualified) {
    if (!best || goals > best.goals || (goals === best.goals && name < best.name)) {
      best = { name, goals };
    }
  }
  return best;
}
```

**Benefícios**:
- Exclui jogadores com participação irrelevante
- Garante que o artilheiro teve impacto significativo no período
- Evita situações onde 1 gol esporádico vence por ordem alfabética

**UI**:
```
Artilheiro: R. Nazario (9 gols)
ℹ️ Mínimo 2 gols no período
```

---

## 🎯 PARTE 3: GARÇOM (ASSISTÊNCIAS) - ANÁLISE DETALHADA

### 3.1 Fórmula Implementada

```
garçom = jogador com MAIS ASSISTÊNCIAS nas últimas 10 partidas
critério_desempate = ordem alfabética (A < Z)
critério_mínimo = NENHUM ⚠️
```

### 3.2 Código

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (linhas 178-199)

```typescript
function computeTopAssister(
  presentations: MatchSummaryPresentation[],
): { name: string; assists: number } | null {
  const tally = new Map<string, number>();

  // Soma assistências de cada jogador
  for (const p of presentations) {
    if (!p.assists) continue;
    for (const a of p.assists.assisters) {
      tally.set(a.name, (tally.get(a.name) ?? 0) + a.count);
    }
  }

  if (tally.size === 0) return null;

  // Encontra o melhor
  let best: { name: string; assists: number } | null = null;
  for (const [name, assists] of tally) {
    if (!best || assists > best.assists || (assists === best.assists && name < best.name)) {
      best = { name, assists };
    }
  }
  return best;
}
```

### 3.3 Exemplo de Cálculo

**Entrada**: 10 partidas

| Partida | Assistências |
|---------|--------------|
| #1 | Beckham (2) |
| #2 | Beckham (3) |
| #3 | Zidane (2) |
| #4-10 | Beckham (1) em cada uma = 7 |

**Cálculo**:
```
Beckham: 2 + 3 + 7 = 12 assistências
Zidane:  2         = 2 assistências
```

**Resultado**: Beckham com 12 assistências ✅

### ⚠️ 3.4 PROBLEMA IDENTIFICADO

**Mesmo problema do artilheiro**: Ausência de critério mínimo

### 💡 3.5 PROPOSTA DE CORREÇÃO

```typescript
const MIN_ASSISTS = 2; // Mínimo 2 assistências no período

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

  // ✅ NOVO: Filtrar jogadores que não atingiram o mínimo
  const qualified = Array.from(tally.entries())
    .filter(([, assists]) => assists >= MIN_ASSISTS);

  if (qualified.length === 0) return null;

  // Encontrar o melhor entre os qualificados
  let best: { name: string; assists: number } | null = null;
  for (const [name, assists] of qualified) {
    if (!best || assists > best.assists || (assists === best.assists && name < best.name)) {
      best = { name, assists };
    }
  }
  return best;
}
```

---

## 🥇 PARTE 4: CRAQUE MAIS FREQUENTE - ANÁLISE DETALHADA

### 4.1 Fórmula Implementada

```
craque_frequente = jogador que MAIS APARECEU NO TOP3
                   (qualquer posição: 🥇 ou 🥈 ou 🥉)
critério_desempate = ordem alfabética
```

### 4.2 Código

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (linhas 144-165)

```typescript
function computeTopHighlight(
  presentations: MatchSummaryPresentation[],
): { name: string; appearances: number } | null {
  const tally = new Map<string, number>();

  for (const p of presentations) {
    if (!p.highlights) continue;
    for (const h of p.highlights.top3) {  // ← Conta TODOS do top3!
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

### 4.3 Exemplo de Cálculo

**Entrada**: 10 partidas com top3

| Partida | Top3 |
|---------|------|
| #1 | 🥇 R. Nazario, 🥈 Pelé, 🥉 Beckham |
| #2 | 🥇 R. Nazario, 🥈 D.Prima, 🥉 Beckham |
| #3 | 🥇 Pelé, 🥈 Zidane, 🥉 D.Prima |
| #4-10 | 🥇 alternando, 🥈 Beckham (5×), 🥉 Zidane (4×) |

**Cálculo**:
```
R. Nazario: 2 aparições (🥇 2×)
Beckham:    8 aparições (🥉 2× + 🥈 6×)
Pelé:       2 aparições (🥈 1× + 🥇 1×)
Zidane:     6 aparições (🥈 1× + 🥉 5×)
D.Prima:    2 aparições (🥈 1× + 🥉 1×)
```

**Resultado**: Beckham com 8 aparições ✅

### ⚠️ 4.4 PROBLEMA IDENTIFICADO

**Nome enganoso e semântica confusa**

**O que o nome sugere**:
```
"Craque mais frequente" = Quem ganhou mais vezes o prêmio de Craque (🥇)
```

**O que a implementação faz**:
```
"Craque mais frequente" = Quem mais apareceu no top3 (qualquer posição)
```

**Consequência**: 
- Um jogador que ficou 10× em 2º lugar ganha de outro que ficou 5× em 1º lugar
- Isso pode parecer contra-intuitivo para o usuário

**Comparação**:

| Jogador | Desempenho | Aparições no Top3 | Prêmios de Craque |
|---------|------------|-------------------|-------------------|
| Beckham | 10× 🥈 (2º lugar) | 10 | 0 |
| R. Nazario | 5× 🥇 (1º lugar) | 5 | 5 |

**Resultado atual**: Beckham vence (10 > 5) ✅ conforme implementação  
**Expectativa do usuário**: R. Nazario deveria vencer (5 × Craque vs 0)

### 💡 4.5 PROPOSTAS DE CORREÇÃO

#### Opção A - Renomear (RECOMENDADA)

**Mais simples e preserva a lógica atual**

```
De: "Craque mais frequente"
Para: "Destaque mais frequente"
Ou: "Mais vezes no top3"
Ou: "Mais vezes entre os melhores"
```

**UI com tooltip**:
```
🥇 Destaque mais frequente
Beckham (8 aparições no top3)
ℹ️ Jogador que mais apareceu entre os 3 melhores das partidas
```

#### Opção B - Alterar Lógica

**Contar apenas quem ganhou o prêmio de Craque (🥇)**

```typescript
function computeTopCraque(
  presentations: MatchSummaryPresentation[],
): { name: string; craqueWins: number } | null {
  const tally = new Map<string, number>();

  for (const p of presentations) {
    if (!p.craque) continue;
    // ✅ NOVO: Conta apenas quem ganhou o prêmio de Craque
    tally.set(p.craque.name, (tally.get(p.craque.name) ?? 0) + 1);
  }

  if (tally.size === 0) return null;

  let best: { name: string; craqueWins: number } | null = null;
  for (const [name, wins] of tally) {
    if (!best || wins > best.craqueWins || (wins === best.craqueWins && name < best.name)) {
      best = { name, craqueWins: wins };
    }
  }
  return best;
}
```

**Prós**: Alinha com a expectativa do nome  
**Contras**: Perde informação sobre jogadores consistentes que sempre ficam no top3

#### Opção C - Mostrar Ambos

```
🥇 Craque mais frequente: R. Nazario (5 prêmios)
⭐ Destaque mais consistente: Beckham (8 aparições no top3)
```

**Recomendação**: **Opção A** (renomear para "Destaque mais frequente")

---

## ⭐ PARTE 5: MELHOR MÉDIA - ANÁLISE CRÍTICA

### 5.1 Fórmula Implementada

```
melhor_média = média das notas QUANDO O JOGADOR FICOU NO TOP3
             = (soma das notas no top3) ÷ (vezes que ficou no top3)
```

**⚠️ ATENÇÃO**: Esta NÃO é a média de todas as partidas disputadas!

### 5.2 Código

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (linhas 201-230)

```typescript
function computeTopRatedPlayer(
  presentations: MatchSummaryPresentation[],
): { name: string; avgRating: string } | null {
  const ratingSum = new Map<string, number>();
  const ratingCount = new Map<string, number>();

  for (const p of presentations) {
    if (!p.highlights?.top3) continue;
    for (const h of p.highlights.top3) {  // ← Apenas top3!
      const rating = parseFloat(h.rating);
      if (!isNaN(rating) && rating > 0) {
        ratingSum.set(h.name, (ratingSum.get(h.name) ?? 0) + rating);
        ratingCount.set(h.name, (ratingCount.get(h.name) ?? 0) + 1);
      }
    }
  }

  if (ratingSum.size === 0) return null;

  // Calcula média: soma ÷ contagem
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

### 5.3 Exemplo de Cálculo - D.Prima com 9.0

**Dados de entrada**: 10 partidas

**D.Prima**:
- Partida #2: Ficou no top3 com nota 9.0 (🥈)
- Partida #3: Ficou no top3 com nota 9.0 (🥉)
- Partidas #1, #4-10: **NÃO ficou no top3** (notas desconhecidas)

**Cálculo**:
```
soma = 9.0 + 9.0 = 18.0
contagem = 2 aparições no top3
média = 18.0 ÷ 2 = 9.0 ✅
```

**Resultado**: D.Prima tem média 9.0 ✅ conforme fórmula implementada

### ⚠️ 5.4 PROBLEMA CRÍTICO IDENTIFICADO

**Expectativa do usuário**:
```
"Melhor média" = média de TODAS as partidas que o jogador disputou
               = (soma de todas as notas) ÷ (partidas disputadas)
```

**Implementação real**:
```
"Melhor média" = média das partidas em que ficou no TOP3
               = (soma das notas no top3) ÷ (aparições no top3)
```

**Comparação problemática**:

| Jogador | Partidas Disputadas | Aparições no Top3 | Notas no Top3 | Média Calculada | Média Real (todas partidas) |
|---------|---------------------|-------------------|---------------|-----------------|------------------------------|
| D.Prima | 10 | 2 | 9.0, 9.0 | **9.0** | ~7.0 (estimado) |
| R. Nazario | 10 | 8 | 9.5, 9.8, 8.5, 8.5, 8.5, 8.5, 8.5, 8.5 | **8.66** | ~8.4 (estimado) |

**Problema**: D.Prima "ganha" com 9.0, mas foi destaque em apenas 2 partidas, enquanto R. Nazario foi consistentemente bom em 8 partidas.

### 5.5 Por Que Isso Acontece?

**Limitação dos dados disponíveis**:

`MatchSummaryPresentation` contém:
```typescript
{
  highlights: {
    top3: [
      { name: "Player A", rating: "9.0" },  // Apenas top3
      { name: "Player B", rating: "8.5" },  // Apenas top3
      { name: "Player C", rating: "8.0" }   // Apenas top3
    ]
  }
}
```

**NÃO temos acesso a**:
- Notas de jogadores que não ficaram no top3
- Lista completa de todos os jogadores que disputaram a partida
- Informação de quem jogou e quem não jogou

**Portanto, é IMPOSSÍVEL calcular a média de todas as partidas disputadas com os dados atuais.**

### 💡 5.6 PROPOSTAS DE CORREÇÃO

#### Opção A - Ideal mas Requer Refatoração (NÃO RECOMENDADA no curto prazo)

**Adicionar mais dados em `MatchSummaryPresentation`**:

```typescript
interface MatchSummaryPresentation {
  // ...campos existentes
  allPlayers: {
    name: string;
    rating: string;
    played: boolean;
  }[];  // ← NOVO campo com todos os jogadores
}
```

**Prós**: Permitiria cálculo correto da média real  
**Contras**: 
- Requer mudança no backend (MatchSummaryBuilder)
- Aumento significativo do tamanho do JSON
- Impacto em armazenamento e performance

#### Opção B - Adicionar Critério Mínimo (RECOMENDADA)

```typescript
const MIN_TOP3_APPEARANCES = 3; // Mínimo 3 aparições no top3

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

  // ✅ NOVO: Filtrar jogadores com menos aparições
  const qualified = Array.from(ratingSum.entries())
    .filter(([name]) => ratingCount.get(name)! >= MIN_TOP3_APPEARANCES);

  if (qualified.length === 0) return null;

  // Encontrar o melhor entre os qualificados
  let best: { name: string; avgRating: number } | null = null;
  for (const [name, sum] of qualified) {
    const count = ratingCount.get(name)!;
    const avg = sum / count;
    if (!best || avg > best.avgRating || (avg === best.avgRating && name < best.name)) {
      best = { name, avgRating: avg };
    }
  }

  return best ? { name: best.name, avgRating: best.avgRating.toFixed(2) } : null;
}
```

**Benefícios**:
- Exclui jogadores com participação esporádica no top3
- Mantém a lógica atual (viável com dados disponíveis)
- Melhora a representatividade da métrica

#### Opção C - Renomear e Documentar (RECOMENDADA em conjunto com B)

```
De: "Melhor média"
Para: "Melhor média entre os destaques"
```

**UI com tooltip**:
```
⭐ Melhor média entre os destaques
R. Nazario (8.66)
ℹ️ Média das notas quando o jogador ficou no top3 (mínimo 3 aparições)
```

#### Opção D - Remover o Agregado

**Se a métrica é muito confusa ou problemática, simplesmente não exibir até termos dados completos.**

**Recomendação**: **Opção B + C** (critério mínimo + renomear + tooltip)

---

## 📊 PARTE 6: ESTATÍSTICAS GERAIS - VALIDAÇÃO

### 6.1 Fórmulas Implementadas

```
vitórias = contagem de partidas com outcome.type === "WIN"
empates = contagem de partidas com outcome.type === "DRAW"
derrotas = contagem de partidas com outcome.type === "LOSS"
gols_marcados = soma de ourScore
gols_sofridos = soma de oppScore
saldo = gols_marcados - gols_sofridos
média_gols = gols_marcados ÷ número_partidas
aproveitamento = (vitórias × 3 + empates) ÷ (partidas × 3) × 100%
```

### 6.2 Código

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (linhas 94-119)

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

### 6.3 Exemplo de Cálculo

**Entrada**: 10 partidas

| Resultado | Placar |
|-----------|--------|
| 8 vitórias | 4-2, 5-1, 2-2, 2-1, 2-1, 2-1, 2-1, 2-1 |
| 1 empate | 2-2 |
| 1 derrota | 0-3 |

**Cálculos**:
```
Vitórias: 8
Empates: 1
Derrotas: 1
Gols marcados: 4+5+2+2+2+2+2+2+2+0 = 23
Gols sofridos: 2+1+2+1+1+1+1+1+2+3 = 15
Saldo: 23 - 15 = +8
Média gols: 23 ÷ 10 = 2.3
Pontos: 8×3 + 1×1 = 25
Aproveitamento: 25 ÷ 30 × 100% = 83.3%
```

### ✅ 6.4 VALIDAÇÃO

**TODAS as fórmulas estão corretas e alinhadas com a expectativa padrão de estatísticas de futebol.**

Nenhuma correção necessária.

---

## 📝 PARTE 7: EDITORIAL DO LLM - FONTE DE DADOS

### 7.1 Fluxo no Backend

**Arquivo**: `src/main/kotlin/com/eafc26/discordstats/llm/LlmEditorialService.kt`

```kotlin
fun generateAndPersistPanorama(canonical: CanonicalMatch) {
    if (!isEnabled()) return
    if (panoramaRepository == null) return

    try {
        val clubId = canonical.interpretation.perspectiveClubId.value
        val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)  // 10 partidas
        val matchIds = recentMatches.map { it.matchId.value }
        val contextKey = computeContextKey(clubId, matchIds, PROMPT_VERSION, properties.model)

        // Verifica se já existe panorama para este contexto
        val existing = panoramaRepository.findByContextKey(clubId, contextKey)
        if (existing != null) {
            log.debug("Panorama already exists...")
            return
        }

        // Gera contexto editorial
        val context = contextBuilder.buildFullContext(canonical, recentMatches)
        val result = provider!!.generatePanorama(context)

        // Persiste resultado
        // ...
    } catch (ex: Exception) {
        log.error("Panorama generation error...")
    }
}
```

**Arquivo**: `src/main/kotlin/com/eafc26/discordstats/service/MatchHistoryService.kt`

```kotlin
fun latest(limit: Int): List<CanonicalMatch> =
    list(MatchHistoryQuery(order = MatchHistoryOrder.NEWEST_FIRST, limit = limit))

fun list(query: MatchHistoryQuery = MatchHistoryQuery()): List<CanonicalMatch> {
    val comparator = if (query.order == MatchHistoryOrder.NEWEST_FIRST) {
        compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }
            .thenBy { it.matchId.value }
    } else {
        compareBy<CanonicalMatch> { it.footballMatch.playedAt }
            .thenBy { it.matchId.value }
    }
    
    val matches = repository.findAll().asSequence()
        // ... filtros
        .sortedWith(comparator)

    return query.limit?.let(matches::take)?.toList() ?: matches.toList()
}
```

### 7.2 Construção do Contexto

**Arquivo**: `src/main/kotlin/com/eafc26/discordstats/llm/EditorialContextBuilder.kt`

```kotlin
fun buildFullContext(
    current: CanonicalMatch,
    recentMatches: List<CanonicalMatch>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): EditorialContext = EditorialContext(
    match = matchContext(current, zoneId),
    recentForm = recentForm(recentMatches, current),  // ← Exclui a partida atual!
)

private fun recentForm(recentMatches: List<CanonicalMatch>, excluding: CanonicalMatch): RecentFormContext? {
    val others = recentMatches.filter { it.matchId != excluding.matchId }  // ← Filtra!
    if (others.isEmpty()) return null

    val results = others.map { canonical ->
        // Converte para RecentMatchResult
        // ...
    }

    return RecentFormContext(
        results = results,  // ← Apenas 9 partidas (10 - atual)
        // ...
    )
}
```

### ⚠️ 7.3 PROBLEMAS IDENTIFICADOS

#### Problema #1: Fonte Diferente

| Componente | Fonte de Dados |
|------------|---------------|
| **Dashboard** | PostgreSQL via Supabase<br/>`match_editorial_presentations` |
| **LLM Backend** | Memória (H2 ou Map)<br/>`CanonicalMatchRepository` |

**Risco**: Se houver dessincronização entre as fontes, o editorial do LLM pode mencionar partidas diferentes das exibidas no dashboard.

**Mitigação atual**: 
- Ambos ordenam por `played_at DESC` ✅
- A correção de hoje garantiu que o LLM preserva ordem cronológica ✅

**Recomendação**: 
- ✅ Manter como está por ora (fontes sincronizadas via processamento)
- 🔄 Avaliar migração do LLM para buscar direto do PostgreSQL no futuro

#### Problema #2: LLM Recebe Apenas 9 Partidas no `recentForm`

**Fluxo**:
1. Backend busca **10 partidas** (incluindo a mais recente)
2. `buildFullContext` recebe essas 10 partidas
3. A partida #1 (mais recente) vai para `context.match`
4. As partidas #2 a #10 (**9 partidas**) vão para `context.recentForm`

**Prompt enviado ao LLM** (`EditorialPrompts.kt`):
```
PARTIDA MAIS RECENTE:
Associação BF 4×2 Rival FC — Vitória (05/08/2026)

SEQUÊNCIA DAS ÚLTIMAS 9 PARTIDAS:  ← Apenas 9!
Vitória: 5×1 vs Zebra United
Empate: 2×2 vs Empate FC
...

BALANÇO GERAL:
Resultados: 7V 1E 1D  ← De 9 partidas, não 10!
```

**Impacto**: 
- O prompt menciona "últimas 10 partidas" mas `recentForm` tem apenas 9
- Tecnicamente está correto (1 atual + 9 anteriores = 10 total)
- Mas a descrição pode ser confusa

### 💡 7.4 PROPOSTA DE CORREÇÃO

#### Para Problema #2: Clarificar no Prompt

```kotlin
// EditorialPrompts.kt

fun panoramaPrompt(context: EditorialContext): Pair<String, String> {
    val m = context.match
    val userPrompt = buildString {
        append("CONTEXTO: Abertura de matéria sobre o MOMENTO ATUAL do clube baseado nas últimas 10 partidas.\n\n")
        append("PARTIDA MAIS RECENTE:\n")
        append("${m.ourClub} ${m.ourScore}×${m.opponentScore} ${m.opponent} — ${outcomeLabel(m.outcome)} (${m.date})\n")
        // ...
        
        context.recentForm?.let { form ->
            // ✅ CORREÇÃO: Clarificar que são N partidas ANTERIORES
            append("\nCONTEXTO RECENTE (${form.results.size} partidas anteriores à atual):\n")
            form.results.forEach { r ->
                append("${outcomeLabel(r.outcome)}: ${r.ourScore}×${r.opponentScore} vs ${r.opponent}\n")
            }
            append("\nBALANÇO GERAL (${form.results.size} partidas anteriores):\n")
            append("Resultados: ${form.wins}V ${form.draws}E ${form.losses}D\n")
            append("Gols: ${form.goalsScored} marcados, ${form.goalsConceded} sofridos\n")
            append("Sequência: ${form.streak}\n")
        }
        
        // ...
        
        append("\nFOCO:\n")
        append("Este NÃO é um resumo da última partida.\n")
        append("Este é uma análise do momento do clube através das últimas 10 partidas.\n")
        append("A partida mais recente pode ter ênfase, mas o texto deve comparar e analisar a sequência de 10 jogos.\n")
        append("Narre a tendência geral baseada em evidências, não liste resultados individuais.")
    }
    return PANORAMA_SYSTEM to userPrompt
}
```

---

## 🎯 CONCLUSÕES E RECOMENDAÇÕES FINAIS

### ✅ CONFIRMAÇÕES

1. **✅ Fonte única no Dashboard**: Todos os agregados usam `getRecentMatchCards(clubId, 10)`
2. **✅ Ordenação correta**: `played_at DESC` garante cronologia correta
3. **✅ Estatísticas gerais**: Fórmulas alinhadas com expectativa padrão
4. **✅ Correção de hoje**: LLM agora preserva ordem cronológica no contexto

### ⚠️ PROBLEMAS IDENTIFICADOS (em ordem de prioridade)

| # | Problema | Severidade | Componente | Impacto |
|---|----------|------------|------------|---------|
| 1 | Melhor Média calcula apenas sobre top3 | 🔴 Alta | Dashboard | Métrica enganosa |
| 2 | Sem critério mínimo (artilheiro/garçom) | 🟡 Média | Dashboard | Não representativo |
| 3 | "Craque mais frequente" é nome enganoso | 🟡 Média | Dashboard | Confusão semântica |
| 4 | LLM usa fonte de dados diferente | 🟡 Média | Backend/Frontend | Risco de inconsistência |
| 5 | LLM recebe 9 partidas no recentForm | 🟢 Baixa | Backend | Descrição imprecisa |

### 💡 PLANO DE AÇÃO RECOMENDADO

#### 🔴 PRIORIDADE ALTA (Implementar imediatamente)

**1. Melhor Média - Adicionar critério mínimo + renomear**

```typescript
// Constantes
const MIN_TOP3_APPEARANCES = 3;

// Label alterado
"Melhor média entre os destaques"

// Tooltip
"Média das notas quando o jogador ficou no top3 (mínimo 3 aparições)"
```

**Arquivos a modificar**:
- `apps/dashboard/src/lib/services/sequence-editorial-service.ts` (função)
- `apps/dashboard/src/components/overview/overview-club-panel.tsx` (UI)
- `apps/dashboard/src/__tests__/sequence-editorial.test.ts` (testes)

**2. Artilheiro - Adicionar critério mínimo**

```typescript
const MIN_GOALS = 2;

// Tooltip
"Artilheiro do período (mínimo 2 gols)"
```

**3. Garçom - Adicionar critério mínimo**

```typescript
const MIN_ASSISTS = 2;

// Tooltip
"Assistências no período (mínimo 2 assistências)"
```

#### 🟡 PRIORIDADE MÉDIA (Implementar em seguida)

**4. Craque Mais Frequente - Renomear**

```
De: "Craque mais frequente"
Para: "Destaque mais frequente"

// Tooltip
"Jogador que mais apareceu entre os 3 melhores das partidas"
```

**5. LLM Prompt - Clarificar contagem**

```kotlin
// Em EditorialPrompts.kt
"CONTEXTO RECENTE (9 partidas anteriores à atual)"
```

#### 🟢 PRIORIDADE BAIXA (Avaliar necessidade)

**6. Avaliar migração de fonte do LLM**
- Por ora, manter separado (funciona bem)
- Revisitar se houver casos recorrentes de dessincronização

---

### 📦 ENTREGÁVEIS PARA IMPLEMENTAÇÃO

#### 1️⃣ Código

```typescript
// apps/dashboard/src/lib/services/sequence-editorial-service.ts

// ✅ ADICIONAR no topo do arquivo
const MIN_GOALS = 2;
const MIN_ASSISTS = 2;
const MIN_TOP3_APPEARANCES = 3;

// ✅ MODIFICAR computeTopScorer
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

  // ✅ NOVO: Filtrar jogadores que não atingiram o mínimo
  const qualified = Array.from(tally.entries())
    .filter(([, goals]) => goals >= MIN_GOALS);

  if (qualified.length === 0) return null;

  let best: { name: string; goals: number } | null = null;
  for (const [name, goals] of qualified) {
    if (!best || goals > best.goals || (goals === best.goals && name < best.name)) {
      best = { name, goals };
    }
  }
  return best;
}

// ✅ MODIFICAR computeTopAssister (similar)

// ✅ MODIFICAR computeTopRatedPlayer
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

  // ✅ NOVO: Filtrar jogadores com menos aparições
  const qualified = Array.from(ratingSum.entries())
    .filter(([name]) => ratingCount.get(name)! >= MIN_TOP3_APPEARANCES);

  if (qualified.length === 0) return null;

  let best: { name: string; avgRating: number } | null = null;
  for (const [name, sum] of qualified) {
    const count = ratingCount.get(name)!;
    const avg = sum / count;
    if (!best || avg > best.avgRating || (avg === best.avgRating && name < best.name)) {
      best = { name, avgRating: avg };
    }
  }

  return best ? { name: best.name, avgRating: best.avgRating.toFixed(2) } : null;
}
```

#### 2️⃣ Testes

```typescript
// apps/dashboard/src/__tests__/sequence-editorial.test.ts

describe("minimum participation criteria", () => {
  it("filters out scorers with less than MIN_GOALS", () => {
    const result = buildSequenceEditorial([
      makePresentation({ goals: { scorers: [{ name: "A", count: 1 }] } }),
      makePresentation({ matchId: "m2", goals: { scorers: [{ name: "B", count: 2 }] } }),
    ]);
    expect(result.topScorer).toEqual({ name: "B", goals: 2 });
  });

  it("returns null if no scorer reaches minimum", () => {
    const result = buildSequenceEditorial([
      makePresentation({ goals: { scorers: [{ name: "A", count: 1 }] } }),
    ]);
    expect(result.topScorer).toBeNull();
  });

  // Similar para assistências e média
});
```

#### 3️⃣ UI

```typescript
// apps/dashboard/src/components/overview/overview-club-panel.tsx

// ✅ MODIFICAR labels

// Artilheiro
<div className="flex items-center gap-2">
  <span className="text-[0.82rem] w-5 text-center shrink-0">⚽</span>
  <span className="text-[#9da5b0]">
    <span className="font-medium text-[#c9d1d9]" title="Mínimo 2 gols no período">
      {editorial.topScorer.name}
    </span>
    <span className="text-[#6e7681]"> — </span>
    {editorial.topScorer.goals} {editorial.topScorer.goals === 1 ? "gol" : "gols"}
  </span>
</div>

// Melhor Média (renomeado)
{editorial.topRatedPlayer && (
  <div className="flex items-center gap-2">
    <span className="text-[0.82rem] w-5 text-center shrink-0">⭐</span>
    <span className="text-[#9da5b0]">
      <span className="font-medium text-[#c9d1d9]" title="Média entre os destaques (mín. 3 aparições)">
        {editorial.topRatedPlayer.name}
      </span>
      <span className="text-[#6e7681]"> — </span>
      média {editorial.topRatedPlayer.avgRating}
    </span>
  </div>
)}

// Craque (renomeado)
{editorial.topHighlight && (
  <div className="flex items-center gap-2">
    <span className="text-[0.82rem] w-5 text-center shrink-0">🥇</span>
    <span className="text-[#9da5b0]">
      <span className="font-medium text-[#c9d1d9]" title="Mais aparições no top3">
        {editorial.topHighlight.name}
      </span>
      <span className="text-[#6e7681]"> — </span>
      {editorial.topHighlight.appearances}× destaque
    </span>
  </div>
)}
```

---

### 📊 MÉTRICAS DE SUCESSO

Após implementação, validar:

1. **Artilheiro**: ✅ Apenas jogadores com 2+ gols aparecem
2. **Garçom**: ✅ Apenas jogadores com 2+ assistências aparecem
3. **Melhor Média**: ✅ Apenas jogadores com 3+ aparições no top3
4. **Labels**: ✅ Nomes mais claros e descritivos
5. **Tooltips**: ✅ Explicações visíveis no hover
6. **Testes**: ✅ Todos passando com novos critérios
7. **UX**: ✅ Usuário entende claramente o que cada métrica representa

---

**FIM DO RELATÓRIO TÉCNICO FINAL**

*Gerado em 6 de agosto de 2026 após investigação completa da arquitetura e lógica de cálculo dos agregados do painel editorial.*

