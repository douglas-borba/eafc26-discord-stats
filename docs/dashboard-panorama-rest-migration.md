# Dashboard Panorama REST Migration

**Data**: 2026-08-07
**Status**: ✅ Concluído

## Contexto

O dashboard estava consultando diretamente a view `dashboard_panoramas` do Supabase, sem validação de `contextKey`. Isso causava inconsistências, pois panoramas de contextos diferentes (diferentes conjuntos de partidas) podiam ser exibidos incorretamente.

## Problema Identificado

- Dashboard fazia acesso direto ao Supabase via `fetchAiPanorama()` em `sequence-editorial-service.ts`
- Não havia validação de `contextKey` no frontend
- A lógica de validação de contexto estava duplicada entre frontend e backend
- Violava o princípio de single source of truth

## Solução Implementada

### 1. Cliente HTTP para o Backend

Criado `panorama-client.ts` que:
- Consome o endpoint REST `/api/panorama` do backend Spring Boot
- Delega a validação de `contextKey` para o backend
- Retorna `null` quando o status é `unavailable` ou `no_matches`
- Implementa tratamento de erros robusto

### 2. Remoção de Código Morto

- Removido import de `createServerSupabase` do `sequence-editorial-service.ts`
- Mantida apenas a interface `SequenceEditorial` com suporte para `aiNarrative`
- Dashboard usa apenas o cliente REST, nunca acessa Supabase diretamente para panoramas

### 3. Fallback Determinístico

Quando o endpoint retorna `unavailable`:
- O dashboard exibe `editorial.narrative` (narrativa determinística)
- Mantém a UX consistente
- Sem mensagens de erro ao usuário

### 4. Single Source of Truth

A validação de `contextKey` está **apenas** no backend (`LlmEditorialService`):
```kotlin
fun getPersistedPanorama(clubId: String): String? {
    val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)
    val contextKey = computeContextKey(/* ... */)
    val record = panoramaRepository.findSuccessfulByContextKey(clubId, contextKey)
    return record?.narrative
}
```

## Arquitetura Final

```
┌──────────────────────────────────────────────┐
│          Dashboard (Next.js)                 │
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  panorama-client.ts                    │ │
│  │  - fetchAiPanorama()                   │ │
│  │  - Consome /api/panorama               │ │
│  │  - Retorna string | null               │ │
│  └────────────────────────────────────────┘ │
│                    ▲                         │
└────────────────────┼─────────────────────────┘
                     │ HTTP GET
                     │
┌────────────────────▼─────────────────────────┐
│     Backend (Spring Boot)                    │
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  PanoramaController                    │ │
│  │  - GET /api/panorama                   │ │
│  │  - Retorna PanoramaResponse            │ │
│  └────────────────────────────────────────┘ │
│                    ▲                         │
│                    │                         │
│  ┌────────────────▼─────────────────────┐   │
│  │  LlmEditorialService                 │   │
│  │  - getPersistedPanorama()            │   │
│  │  - Valida contextKey                 │   │
│  │  - Consulta panoramaRepository       │   │
│  └──────────────────────────────────────┘   │
│                    ▲                         │
│                    │                         │
│  ┌────────────────▼─────────────────────┐   │
│  │  PanoramaRepository                  │   │
│  │  - findSuccessfulByContextKey()      │   │
│  └──────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │   Supabase/Postgres  │
         │  editorial_panoramas │
         └──────────────────────┘
```

## Testes

✅ Todos os 146 testes do dashboard passaram:
- `editorial-architecture.test.ts` (10 testes)
- `ux-restoration.test.ts` (62 testes)
- `security.test.ts` (26 testes)
- `sequence-editorial.test.ts` (22 testes)
- `opponent-history-calculator.test.ts` (26 testes)

## Notas

- A view `dashboard_panoramas` ainda existe no Supabase, mas não é mais acessada diretamente pelo dashboard
- O endpoint REST é stateless e pode ser cacheado se necessário
- A validação de `contextKey` garante que apenas panoramas do contexto correto sejam exibidos
- O fallback para `editorial.narrative` garante que sempre há conteúdo editorial, mesmo sem AI

## Próximos Passos (Opcional)

1. Considerar remover a view `dashboard_panoramas` em migração futura (V8)
2. Adicionar cache HTTP no endpoint `/api/panorama` se necessário
3. Adicionar métricas de uso do endpoint para monitoramento

