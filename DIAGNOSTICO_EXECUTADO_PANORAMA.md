# 🔍 DIAGNÓSTICO EXECUTADO: Panorama LLM Desatualizado

**Data**: 06 de Agosto de 2026, 21:49  
**Execução**: Completa - Backend + Frontend + Acesso à Overview

---

## 📊 EVIDÊNCIAS CAPTURADAS

### 1️⃣ 10 matchIds Atuais (ordem cronológica - newest first)

```
1.  939084756850013 (played_at: 2026-08-05T21:10:53+00:00)
2.  939044773390220 (played_at: 2026-08-05T20:52:06+00:00)
3.  939084293010461 (played_at: 2026-08-05T20:40:51+00:00)
4.  938962502380148 (played_at: 2026-08-05T20:22:53+00:00)
5.  938690978850420 (played_at: 2026-08-05T20:04:26+00:00)
6.  938710639700053 (played_at: 2026-08-05T19:45:13+00:00)
7.  938710400720440 (played_at: 2026-08-05T19:25:10+00:00)
8.  935420663600456 (played_at: 2026-08-04T21:26:44+00:00)
9.  935336701630198 (played_at: 2026-08-04T21:09:01+00:00)
10. 935340370500182 (played_at: 2026-08-04T20:47:03+00:00)
```

---

### 2️⃣ Context Key Calculado pelo Frontend

```
e02f60b36269611f5588d3d8c1e3e9ba8a5c80715b3da4c317a6584939f3e024
```

**Inputs usados pelo TypeScript:**
- `clubId`: "1104972"
- `matchIds`: [939084756850013, 939044773390220, ..., 935340370500182]
- `promptVersion`: **"v1"** ❌ (INCORRETO!)
- `model`: **"claude-3-5-sonnet-20241022"** ❌ (INCORRETO!)

---

### 3️⃣ Panoramas Existentes no Banco

**Total encontrado:** 5 panoramas

#### Panorama 1 (SELECIONADO - mas ERRADO)
- **generated_at**: 2026-08-05T20:26:32.626375+00:00
- **context_key**: `6823a519b8734914432ec172fbbed9ce19502b64f412468eee8dc87922aa512c`
- **match_ids**: [938690978850420, 938710400720440, 938710639700053] (apenas 4 partidas!)
- **matches expected**: ❌ NÃO
- **Preview**: "Após três rodadas marcadas pela oscilação, o clube vive um momento de transição,..."

#### Panorama 2
- **generated_at**: 2026-08-05T20:11:32.580476+00:00
- **context_key**: `8ad335fa577ebf44f29403310eea2a39e99a48e00d00a82e001fc092bc058da3`
- **match_ids**: [935420663600456, 938690978850420, 938710400720440] (apenas 4 partidas!)
- **matches expected**: ❌ NÃO

#### Panorama 3
- **generated_at**: 2026-08-05T19:28:22.342041+00:00
- **context_key**: `9f502449755930f4e6a104125b5bd132ffb8ae8ab02849af7e25147a47a01c6a`
- **match_ids**: [935336701630198, 935340370500182, 935420663600456] (apenas 4 partidas!)
- **matches expected**: ❌ NÃO

#### Panorama 4
- **generated_at**: 2026-08-05T13:58:55.050355+00:00
- **context_key**: `81a28137d2ad287647ad89504c02945f7790c544290d0818fee27cfb94d58e52`
- **match_ids**: [931268382550195, 935336701630198, 935340370500182] (apenas 4 partidas!)
- **matches expected**: ❌ NÃO

#### Panorama 5
- **generated_at**: 2026-08-05T13:19:51.295614+00:00
- **context_key**: `20ad68a0870b3c46efe8473633160093f6ed50bb8c460a33046318676cbc0e8c`
- **match_ids**: [931268382550195, 935336701630198, 935340370500182] (apenas 4 partidas!)
- **matches expected**: ❌ NÃO

---

### 4️⃣ Panorama Selecionado

**Panorama 1** foi selecionado (ordenado por `generated_at DESC`)

- **Status**: ❌ INCORRETO
- **Context Key**: `6823a519b8734914432ec172fbbed9ce19502b64f412468eee8dc87922aa512c`
- **Corresponde ao contexto atual?**: NÃO

---

### 5️⃣ Existe Panorama com Context Key Atual?

**NÃO** ❌

Nenhum dos 5 panoramas no banco possui o context_key:
```
e02f60b36269611f5588d3d8c1e3e9ba8a5c80715b3da4c317a6584939f3e024
```

---

## 🎯 CENÁRIO CONFIRMADO

### ⚠️ **CENÁRIO C + B: Cálculo Incorreto E Panorama Não Existe**

**Problema 1: Cálculo do Context Key está ERRADO no Frontend**

O TypeScript usa valores **hardcoded incorretos**:
- ❌ `promptVersion = "v1"` → Backend usa **"v3"**
- ❌ `model = "claude-3-5-sonnet-20241022"` → Backend usa **"openrouter/free"**

**Problema 2: Todos os Panoramas no Banco Têm Apenas 4 Partidas**

Os panoramas existentes foram gerados quando `PANORAMA_MATCH_COUNT` era diferente ou havia apenas 4 partidas no histórico.

**Problema 3: Panorama com 10 Partidas Atuais Nunca Foi Gerado**

Mesmo corrigindo o cálculo, o panorama correto não existe no banco.

---

## 🔍 ANÁLISE DETALHADA

### Por que o Context Key Frontend Está Errado?

**Código TypeScript atual:**
```typescript
const promptVersion = "v1"; // ❌ ERRADO
const model = "claude-3-5-sonnet-20241022"; // ❌ ERRADO
```

**Backend Kotlin:**
```kotlin
const val PROMPT_VERSION = "v3" // ✅ CORRETO
properties.model // = "openrouter/free" (da configuração)
```

### Por que Todos os Panoramas Têm 4 Partidas?

Possíveis causas:
1. Backend estava configurado com `PANORAMA_MATCH_COUNT = 4` no passado
2. Havia apenas 4 partidas no histórico quando foram gerados
3. Alguma restrição ou bug antigo

### Por que Não Existe Panorama Atual?

1. O código foi recentemente atualizado para `PANORAMA_MATCH_COUNT = 10`
2. Nenhuma aquisição nova foi executada desde a mudança
3. O contexto de 10 partidas é novo e nunca foi processado

---

## 💡 CORREÇÃO NECESSÁRIA

### 1️⃣ URGENTE: Corrigir Cálculo do Context Key no Frontend

**Arquivo**: `apps/dashboard/src/lib/services/sequence-editorial-service.ts`

❌ **Código Atual (ERRADO):**
```typescript
const promptVersion = "v1";
const model = "claude-3-5-sonnet-20241022";
const expectedContextKey = computeContextKey(clubId, matchIds, promptVersion, model);
```

✅ **Código Correto:**
```typescript
// Ler valores reais da configuração do backend ou do banco
// Por enquanto, usar os valores atuais do backend:
const promptVersion = "v3";
const model = "openrouter/free";
const expectedContextKey = computeContextKey(clubId, matchIds, promptVersion, model);
```

**Problema**: O frontend não tem acesso direto às configurações do backend. A melhor solução é:

**OPÇÃO A (Recomendada)**: Ler `promptVersion` e `model` do panorama mais recente no banco
```typescript
const latestPanorama = allPanoramas[0];
const promptVersion = latestPanorama.prompt_version || "v3";
const model = latestPanorama.model || "openrouter/free";
```

**OPÇÃO B**: Backend expor um endpoint `/api/panorama/config` retornando:
```json
{
  "promptVersion": "v3",
  "model": "openrouter/free",
  "matchCount": 10
}
```

### 2️⃣ Gerar Panorama para o Contexto Atual (10 partidas)

**Opção A**: Chamar manualmente:
```bash
curl -X POST http://localhost:8080/api/panorama/regenerate
```

**Opção B**: Aguardar próxima aquisição automática (scheduler)

---

## 📌 CONCLUSÃO

**Cenário**: **C) Cálculo do context_key no frontend diverge do backend** + **B) Panorama atual não existe**

**Ação Imediata**: 
1. ✅ Corrigir cálculo do context_key no TypeScript
2. ✅ Gerar panorama para contexto atual (10 partidas)
3. ✅ Remover hardcoded values do frontend

**Solução Definitiva**:
- Backend expor configuração ou frontend ler do primeiro panorama válido
- Garantir que panoramas sejam gerados após cada nova partida

---

**Status**: 🎯 **DIAGNÓSTICO COMPLETO - CAUSA RAIZ CONFIRMADA**

