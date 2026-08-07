# 🔍 Guia de Execução da Investigação de Panorama LLM

**Data**: 06/08/2026  
**Status**: Logs de debug implementados - Pronto para execução

---

## 📋 O Que Foi Implementado

### 1. Migration V7 - Campos de Debug na View
**Arquivo**: `src/main/resources/db/migration/V7__add_debug_fields_to_panoramas_view.sql`

Adicionados à view `dashboard_panoramas`:
- `context_key` - Hash que identifica o conjunto de partidas
- `match_ids` - Array com IDs das partidas do contexto

### 2. Logs Detalhados no Frontend
**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts`

A função `fetchAiPanorama()` agora exibe:
- ✅ 10 partidas mais recentes (ordered by played_at DESC)
- ✅ Context key calculado para essas partidas
- ✅ Todos os panoramas encontrados no banco
- ✅ Qual panorama está sendo selecionado
- ✅ Diagnóstico automático do problema

---

## 🚀 Como Executar a Investigação

### Passo 1: Aplicar Migration (V7)

A migration precisa ser aplicada ao banco de dados. Existem 2 opções:

#### Opção A: Iniciar Spring Boot (recomendado)
```bash
cd "/Users/dougborba_/Documents/EA FC STATS"
./gradlew bootRun
```

O Spring Boot aplicará automaticamente a migration V7 via Flyway ao iniciar.

**Aguarde até ver no log**:
```
Flyway Community Edition ... by Redgate
Successfully applied 1 migration to schema "public", now at version v7
```

#### Opção B: Aplicar manualmente via SQL (se Spring Boot não iniciar)

Se você tiver acesso direto ao PostgreSQL:

```bash
# Conectar ao banco
psql -h localhost -U seu_usuario -d seu_banco

# Executar a migration
\i src/main/resources/db/migration/V7__add_debug_fields_to_panoramas_view.sql
```

---

### Passo 2: Iniciar Dashboard Next.js

```bash
cd apps/dashboard
npm run dev
```

Aguarde até ver:
```
✓ Ready in X ms
○ Local: http://localhost:3000
```

---

### Passo 3: Acessar a Página Overview

Abra o navegador e acesse:
```
http://localhost:3000/clubs/1104972/overview
```

**Onde**: `1104972` é o club_id configurado no `application.yml`

---

### Passo 4: Visualizar Logs de Debug

Os logs aparecerão no **terminal onde o Next.js está rodando**:

```
🔍 [PANORAMA DEBUG] ========================================
🔍 [PANORAMA DEBUG] Investigando seleção de panorama
🔍 [PANORAMA DEBUG] ========================================
🔍 [PANORAMA DEBUG] 1. PARTIDAS UTILIZADAS (10 mais recentes):
🔍 [PANORAMA DEBUG]    1. match-123 (played_at: 2026-08-06T18:00:00Z)
🔍 [PANORAMA DEBUG]    2. match-122 (played_at: 2026-08-05T18:00:00Z)
...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 2. CONTEXT KEY ESPERADO:
🔍 [PANORAMA DEBUG]    abc123...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 3. TODOS OS PANORAMAS ENCONTRADOS:
🔍 [PANORAMA DEBUG]    👉 SELECIONADO Panorama 1:
🔍 [PANORAMA DEBUG]       - generated_at: 2026-08-06T20:00:00Z
🔍 [PANORAMA DEBUG]       - context_key: abc123...
🔍 [PANORAMA DEBUG]       - matches expected: SIM ✅
...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 4. RESULTADO DA SELEÇÃO:
🔍 [PANORAMA DEBUG]    ✅ Panorama selecionado está CORRETO
...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    🔴 PROBLEMA: Panorama ERRADO sendo selecionado
🔍 [PANORAMA DEBUG]       → Existe panorama correto, mas outro está sendo escolhido
🔍 [PANORAMA DEBUG]       → Causa: Ordenação por generated_at em vez de relevância
🔍 [PANORAMA DEBUG] ========================================
```

---

## 🔍 Interpretando os Resultados

### ✅ Cenário 1: Panorama Correto Sendo Exibido
```
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    ✅ OK: Panorama correto está sendo exibido
```

**Significado**: O problema não foi reproduzido. O sistema está funcionando corretamente.

---

### 🔴 Cenário 2: Panorama Errado Sendo Selecionado
```
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    🔴 PROBLEMA: Panorama ERRADO sendo selecionado
🔍 [PANORAMA DEBUG]       → Existe panorama correto, mas outro está sendo escolhido
🔍 [PANORAMA DEBUG]       → Causa: Ordenação por generated_at em vez de relevância
```

**Significado**: **PROBLEMA CONFIRMADO**
- Existe um panorama com o context_key correto (corresponde às 10 partidas atuais)
- MAS o dashboard está exibindo outro panorama (mais recente por generated_at)
- **Solução**: Implementar correção conforme proposto no relatório

---

### 🔴 Cenário 3: Panorama para Contexto Atual Não Existe
```
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    🔴 PROBLEMA: Panorama para contexto atual NÃO EXISTE
🔍 [PANORAMA DEBUG]       → Panorama precisa ser gerado para as 10 partidas atuais
```

**Significado**: 
- O panorama das 10 partidas mais recentes nunca foi gerado
- **Ação**: Acionar `/api/panorama/regenerate` para gerar

---

### 🔴 Cenário 4: Nenhum Panorama Gerado
```
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    🔴 PROBLEMA: Nenhum panorama gerado para este clube
```

**Significado**: 
- Tabela `editorial_panoramas` está vazia para este clube
- LLM pode estar desabilitado ou nunca foi acionado
- **Ação**: Verificar configuração `EAFC_LLM_ENABLED` e gerar primeiro panorama

---

## 📊 Exemplo de Output Completo

```
🔍 [PANORAMA DEBUG] ========================================
🔍 [PANORAMA DEBUG] Investigando seleção de panorama
🔍 [PANORAMA DEBUG] ========================================
🔍 [PANORAMA DEBUG] 1. PARTIDAS UTILIZADAS (10 mais recentes):
🔍 [PANORAMA DEBUG]    1. match-atual-4x2 (played_at: 2026-08-06T18:00:00Z)
🔍 [PANORAMA DEBUG]    2. match-anterior (played_at: 2026-08-05T18:00:00Z)
🔍 [PANORAMA DEBUG]    3. match-anterior2 (played_at: 2026-08-04T18:00:00Z)
🔍 [PANORAMA DEBUG]    4. match-anterior3 (played_at: 2026-08-03T18:00:00Z)
🔍 [PANORAMA DEBUG]    5. match-anterior4 (played_at: 2026-08-02T18:00:00Z)
🔍 [PANORAMA DEBUG]    6. match-anterior5 (played_at: 2026-08-01T18:00:00Z)
🔍 [PANORAMA DEBUG]    7. match-anterior6 (played_at: 2026-07-31T18:00:00Z)
🔍 [PANORAMA DEBUG]    8. match-anterior7 (played_at: 2026-07-30T18:00:00Z)
🔍 [PANORAMA DEBUG]    9. match-anterior8 (played_at: 2026-07-29T18:00:00Z)
🔍 [PANORAMA DEBUG]    10. match-antiga-5x1 (played_at: 2026-07-20T18:00:00Z)
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 2. CONTEXT KEY ESPERADO:
🔍 [PANORAMA DEBUG]    a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 3. TODOS OS PANORAMAS ENCONTRADOS:
🔍 [PANORAMA DEBUG]    👉 SELECIONADO Panorama 1:
🔍 [PANORAMA DEBUG]       - generated_at: 2026-08-06T20:00:00Z
🔍 [PANORAMA DEBUG]       - context_key: x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4
🔍 [PANORAMA DEBUG]       - matches expected: NÃO ❌
🔍 [PANORAMA DEBUG]       - match_ids (10): match-antiga-5x1, match-very-old, match-even-older...
🔍 [PANORAMA DEBUG]       - narrative preview: Associação BF vem de vitória por 5×1 na partida mais recente contra...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG]      ✅ CORRETO Panorama 2:
🔍 [PANORAMA DEBUG]       - generated_at: 2026-08-06T18:30:00Z
🔍 [PANORAMA DEBUG]       - context_key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
🔍 [PANORAMA DEBUG]       - matches expected: SIM ✅
🔍 [PANORAMA DEBUG]       - match_ids (10): match-atual-4x2, match-anterior, match-anterior2...
🔍 [PANORAMA DEBUG]       - narrative preview: Associação BF vem de vitória por 4×2 na última partida contra...
🔍 [PANORAMA DEBUG]
🔍 [PANORAMA DEBUG] 4. RESULTADO DA SELEÇÃO:
🔍 [PANORAMA DEBUG]    ❌ Panorama selecionado está INCORRETO
🔍 [PANORAMA DEBUG]    - generated_at: 2026-08-06T20:00:00Z
🔍 [PANORAMA DEBUG]    - context_key: x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4
🔍 [PANORAMA DEBUG]    - matches expected: NÃO ❌
🔍 [PANORAMA DEBUG]    - narrative preview: Associação BF vem de vitória por 5×1 na partida mais recente...
🔍 [PANORAMA DEBUG] ========================================
🔍 [PANORAMA DEBUG] 5. DIAGNÓSTICO:
🔍 [PANORAMA DEBUG]    🔴 PROBLEMA: Panorama ERRADO sendo selecionado
🔍 [PANORAMA DEBUG]       → Existe panorama correto, mas outro está sendo escolhido
🔍 [PANORAMA DEBUG]       → Causa: Ordenação por generated_at em vez de relevância
🔍 [PANORAMA DEBUG] ========================================
```

---

## 🎯 Próximos Passos Após Investigação

### Se Problema For Confirmado:

1. **Capturar os logs** - Salvar o output completo
2. **Executar correção** conforme proposto no relatório:
   - Adicionar coluna `latest_match_played_at` 
   - Alterar ordenação em `PanoramaRepository.kt` e `sequence-editorial-service.ts`
3. **Remover logs de debug** após correção validada

### Se Problema NÃO For Reproduzido:

1. **Verificar histórico** - Pode ter sido corrigido automaticamente
2. **Monitorar** - Problema pode ser intermitente
3. **Aguardar nova ocorrência** com mais dados

---

## 📁 Arquivos Modificados

1. `apps/dashboard/src/lib/services/sequence-editorial-service.ts`
   - Adicionado import do módulo `crypto`
   - Modificada função `fetchAiPanorama()` com logs detalhados
   - Adicionada função `computeContextKey()` para calcular hash

2. `src/main/resources/db/migration/V7__add_debug_fields_to_panoramas_view.sql`
   - Nova migration para expor `context_key` e `match_ids` na view

---

## ✅ Commit Realizado

```
commit 795fe8e
debug: adicionar logs temporarios para investigar selecao de panorama LLM

2 files changed, 142 insertions(+), 2 deletions(-)
```

---

**Status**: 🎯 **PRONTO PARA EXECUÇÃO**

Execute os passos acima e verifique os logs no terminal do Next.js!

