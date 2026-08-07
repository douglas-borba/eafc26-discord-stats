package com.eafc26.discordstats.llm

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.service.MatchHistoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

@Service
class LlmEditorialService(
    private val contextBuilder: EditorialContextBuilder,
    private val provider: EditorialLlmProvider?,
    private val historyService: MatchHistoryService,
    private val properties: LlmProperties,
    private val panoramaRepository: PanoramaRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val discordNarrativeCache = ConcurrentHashMap<String, String?>()

    fun generateAndPersistPanorama(canonical: CanonicalMatch) {
        if (!isEnabled()) return
        if (panoramaRepository == null) {
            log.debug("Panorama persistence unavailable (Postgres mirror disabled)")
            return
        }

        try {
            val clubId = canonical.interpretation.perspectiveClubId.value
            val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)
            // IMPORTANT: Use chronological order (newest first), NOT sorted alphabetically
            // This ensures cache invalidation when match order changes due to reconciliation/republication
            val matchIds = recentMatches.map { it.matchId.value }
            val contextKey = computeContextKey(clubId, matchIds, PROMPT_VERSION, properties.model)

            val existing = panoramaRepository.findByContextKey(clubId, contextKey)
            if (existing != null) {
                // Cache resiliente: validar se o panorama existente é válido
                if (existing.status == "success" && existing.narrative != null) {
                    if (isPromptEcho(existing.narrative)) {
                        log.warn(
                            "Cached prompt echo detected: contextKey={} model={} - Regenerating",
                            contextKey.take(12),
                            existing.model
                        )
                        // Não retornar - continuar para regenerar
                    } else {
                        log.debug("Panorama already exists for context key {} (status={})", contextKey.take(12), existing.status)
                        return
                    }
                } else if (existing.status == "failed" && existing.errorCategory == "llm_prompt_echo") {
                    // Prompt echo failures devem permitir retry (não bloquear)
                    log.debug("Previous prompt echo failure for context key {} - Retrying", contextKey.take(12))
                    // Não retornar - continuar para regenerar
                } else {
                    log.debug("Panorama already exists for context key {} (status={})", contextKey.take(12), existing.status)
                    return
                }
            }

            val context = contextBuilder.buildPanoramaContext(canonical, recentMatches)
            val result = provider!!.generatePanorama(context)

            val record = when (result) {
                is LlmEditorialResult.Success -> {
                    val validated = validateAndTrim(result.text, PANORAMA_MAX_CHARS)
                    
                    if (isPromptEcho(validated)) {
                        log.warn(
                            "Prompt echo detected: provider={} model={} promptVersion={} - Response rejected",
                            result.metadata.provider,
                            result.metadata.model,
                            PROMPT_VERSION
                        )
                        PanoramaRecord(
                            clubId = clubId,
                            contextKey = contextKey,
                            matchIds = matchIds,
                            narrative = null,
                            provider = properties.provider,
                            model = result.metadata.model,
                            promptVersion = PROMPT_VERSION,
                            status = "failed",
                            errorCategory = "llm_prompt_echo",
                            generatedAt = clock.instant(),
                        )
                    } else {
                        log.info(
                            "Panorama generated: provider={} model={} prompt={} latency={}ms tokens={}in/{}out chars={}",
                            result.metadata.provider, result.metadata.model, PROMPT_VERSION,
                            result.metadata.latencyMs ?: "?",
                            result.metadata.inputTokens ?: "?",
                            result.metadata.outputTokens ?: "?",
                            validated.length,
                        )
                        PanoramaRecord(
                            clubId = clubId,
                            contextKey = contextKey,
                            matchIds = matchIds,
                            narrative = validated,
                            provider = result.metadata.provider,
                            model = result.metadata.model,
                            promptVersion = PROMPT_VERSION,
                            status = "success",
                            inputTokens = result.metadata.inputTokens,
                            outputTokens = result.metadata.outputTokens,
                            generatedAt = clock.instant(),
                        )
                    }
                }
                is LlmEditorialResult.Failure -> {
                    val category = classifyError(result)
                    log.warn(
                        "Panorama failed: provider={} model={} prompt={} status={} reason={}",
                        properties.provider, properties.model, PROMPT_VERSION, category, result.reason,
                    )
                    PanoramaRecord(
                        clubId = clubId,
                        contextKey = contextKey,
                        matchIds = matchIds,
                        narrative = null,
                        provider = properties.provider,
                        model = properties.model,
                        promptVersion = PROMPT_VERSION,
                        status = "failed",
                        errorCategory = category,
                        generatedAt = clock.instant(),
                    )
                }
            }


            panoramaRepository.upsert(record)
        } catch (ex: Exception) {
            log.error("Panorama generation error for match {}: {}", canonical.matchId.value, ex.message)
        }
    }

    fun getPersistedPanorama(clubId: String): String? {
        if (panoramaRepository == null) return null
        
        // Calculate current contextKey based on latest matches
        val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)
        if (recentMatches.isEmpty()) return null
        
        val matchIds = recentMatches.map { it.matchId.value }
        val contextKey = computeContextKey(clubId, matchIds, PROMPT_VERSION, properties.model)
        
        // Only return panorama if it belongs to the current context
        return panoramaRepository.findSuccessfulByContextKey(clubId, contextKey)?.narrative
    }

    fun generateMatchNarrative(canonical: CanonicalMatch): String? {
        if (!isEnabled()) return null

        val matchId = canonical.matchId.value
        val cached = discordNarrativeCache[matchId]
        if (cached != null) {
            log.debug("Returning cached Discord narrative for match {}", matchId)
            return cached
        }
        if (discordNarrativeCache.containsKey(matchId)) {
            return null
        }

        return try {
            val recentMatches = historyService.latest(PANORAMA_MATCH_COUNT)
            val context = contextBuilder.buildFullContext(canonical, recentMatches)
            when (val result = provider!!.generateMatchNarrative(context)) {
                is LlmEditorialResult.Success -> {
                    val validated = validateAndTrim(result.text, DISCORD_MAX_CHARS)
                    log.info(
                        "Discord narrative generated: model={} prompt={} latency={}ms tokens={}in/{}out chars={}",
                        result.metadata.model, PROMPT_VERSION,
                        result.metadata.latencyMs ?: "?",
                        result.metadata.inputTokens ?: "?",
                        result.metadata.outputTokens ?: "?",
                        validated.length,
                    )
                    discordNarrativeCache[matchId] = validated
                    validated
                }
                is LlmEditorialResult.Failure -> {
                    log.warn("Discord narrative failed: model={} reason={}", properties.model, result.reason)
                    discordNarrativeCache[matchId] = null
                    null
                }
            }
        } catch (ex: Exception) {
            log.warn("Discord narrative error: model={} error={}", properties.model, ex.message)
            discordNarrativeCache[matchId] = null
            null
        }
    }

    private fun isEnabled(): Boolean {
        if (!properties.enabled) return false
        if (provider == null) {
            log.debug("LLM enabled but no provider configured")
            return false
        }
        return true
    }

    private fun classifyError(failure: LlmEditorialResult.Failure): String {
        val reason = failure.reason.lowercase()
        return when {
            reason.contains("timeout") -> "timeout"
            reason.contains("429") || reason.contains("rate") -> "rate_limited"
            reason.contains("http 4") -> "client_error"
            reason.contains("http 5") || reason.contains("unavailable") -> "server_error"
            reason.contains("parse") -> "parse_error"
            else -> "unknown"
        }
    }

    /**
     * Valida semanticamente a resposta do panorama.
     * 
     * Uma resposta só pode ser considerada válida se parecer um panorama editorial:
     * - Deve estar em português
     * - Deve conter análise do clube
     * - NÃO deve conter instruções ao modelo
     * - NÃO deve conter restrições de formato ("2-3 sentences", "350-550 characters", "no markdown", "must", "should", etc.)
     * - NÃO se parecer com um prompt
     * 
     * Se o texto não passar na validação, é tratado como erro de geração (PromptEcho).
     */
    private fun isPromptEcho(text: String): Boolean {
        if (text.isBlank()) {
            log.debug("Validation failed: empty or blank text")
            return true
        }
        
        val lowerText = text.lowercase()
        
        // 1. Validar que está em português (indicadores básicos)
        val portugueseIndicators = listOf(
            " é ", " são ", " está ", " estão ", " foi ", " foram ", " tem ", " têm ",
            " com ", " para ", " pela ", " pelo ", " dos ", " das ", " aos ", " nas ", " nos ",
            " o ", " a ", " os ", " as ", " do ", " da ", " de ", " em ",
            "clube", "time", "equipe", "partida", "jogo", "vitória", "derrota",
            "gols", "gol", "desempenho", "momento", "fase", "sequência", "campanha"
        )
        val portugueseCount = portugueseIndicators.count { lowerText.contains(it) }
        
        // Deve ter pelo menos 4 indicadores de português (mais flexível)
        if (portugueseCount < 4) {
            log.debug("Validation failed: insufficient Portuguese indicators (found: {})", portugueseCount)
            return true // É prompt echo se não estiver em português
        }
        
        // 2. Detectar instruções ao modelo (imperativos em inglês e português)
        val instructionVerbs = listOf(
            // Inglês - verbos imperativos e modais de instrução
            "write", "produce", "must", "should", "need to", "have to",
            "use only", "do not", "don't", "avoid", "ensure", "make sure",
            "need", "needs", "require", "required",
            // Português - formas imperativas diretas (não usar "deve/devem" sozinho pois é comum em análises)
            "escreva", "escrever", "produza", "produzir",
            "você deve", "você precisa", "é necessário", "certifique-se"
        )
        val hasInstructions = instructionVerbs.any { lowerText.contains(it) }
        if (hasInstructions) {
            log.debug("Validation failed: contains model instructions")
            return true
        }
        
        // 3. Detectar restrições de formato (meta-instruções sobre o texto)
        val formatRestrictions = listOf(
            // Referências a caracteres e frases
            "character", "sentence", "frase", "caractere",
            // Números associados a limites de formato
            "between", "entre", "2-3", "350", "450", "550", "120", "220",
            // Restrições de estilo
            "no lists", "no markdown", "no emoji", "sem lista", "sem markdown", "sem emoji",
            "in prose", "prose format", "formato de prosa", "prose",
            // Meta-instruções
            "including spaces", "com espaços", "incluindo espaços"
        )
        val formatRestrictionCount = formatRestrictions.count { lowerText.contains(it) }
        
        // Mais de 2 restrições de formato = definitivamente é prompt
        if (formatRestrictionCount >= 2) {
            log.debug("Validation failed: contains format restrictions (found: {})", formatRestrictionCount)
            return true
        }
        
        // 4. Detectar estrutura de prompt (padrões meta-textuais)
        val promptPatterns = listOf(
            // Marcadores de tarefa/instrução
            "task:", "instruction:", "prompt:", "system:",
            "tarefa:", "instrução:", "exemplo:",
            // Referências a dados fornecidos (contexto de prompt)
            "given facts", "fatos fornecidos", "dados fornecidos", "given data",
            // Formas imperativas de segunda pessoa
            "you must", "you should", "você deve", "você precisa",
            // Comandos de análise (típicos de prompts)
            "compare the", "analyze the", "compare as", "analise as",
            // Verbos imperativos diretos comuns em prompts
            "list", "mention", "include", "cite", "mencione", "liste", "inclua"
        )
        val promptPatternCount = promptPatterns.count { lowerText.contains(it) }
        
        // Mais de 1 padrão de prompt = suspeito
        if (promptPatternCount > 1) {
            log.debug("Validation failed: contains prompt patterns (found: {})", promptPatternCount)
            return true
        }
        
        // 5. Validar que contém análise do clube (palavras-chave de análise)
        val analysisKeywords = listOf(
            "vitória", "vitórias", "derrota", "derrotas", "empate",
            "gol", "gols", "marcou", "marcaram", "sofreu", "sofreram", "produziu",
            "momento", "fase", "desempenho", "sequência", "campanha",
            "ofensiv", "defensiv", "solidez", "fragilidade",
            "domínio", "consistência", "inconsistência", "irregularidade",
            "partida", "partidas", "resultado", "aproveitamento", "revela", "busca",
            "atravessa", "evidenci", "consolida", "compromet", "exige"
        )
        val analysisCount = analysisKeywords.count { lowerText.contains(it) }
        
        // Deve ter pelo menos 2 palavras de análise (mais flexível)
        if (analysisCount < 2) {
            log.debug("Validation failed: insufficient analysis content (found: {} keywords)", analysisCount)
            return true
        }
        
        // 6. Verificar se parece com prompt ao invés de narrativa editorial
        val sentences = text.split(Regex("[.!?]")).filter { it.trim().isNotEmpty() }
        if (sentences.size > 6 && sentences.count { it.length < 30 } > 3) {
            // Muitas frases muito curtas = suspeito de ser lista de instruções
            log.debug("Validation failed: text structure resembles instruction list")
            return true
        }
        
        // Se passou em todas as validações, é um panorama válido
        log.debug("Validation passed: text appears to be a valid editorial panorama")
        return false
    }

    companion object {
        const val PANORAMA_MATCH_COUNT = 10
        const val PROMPT_VERSION = "v3"
        const val PANORAMA_MAX_CHARS = 550
        const val DISCORD_MAX_CHARS = 220

        /**
         * Computes a deterministic context key for panorama caching.
         *
         * @param clubId The club ID
         * @param chronologicalMatchIds Match IDs in chronological order (newest first).
         *        MUST NOT be sorted alphabetically - order matters for cache invalidation.
         * @param promptVersion The prompt version identifier
         * @param model The LLM model identifier
         * @return SHA-256 hash of the context parameters
         */
        fun computeContextKey(clubId: String, chronologicalMatchIds: List<String>, promptVersion: String, model: String): String {
            val input = "$clubId|${chronologicalMatchIds.joinToString(",")}|$promptVersion|$model"
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun validateAndTrim(text: String, maxChars: Int): String {
            val cleaned = text.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[*#_`~>]"), "")
            if (cleaned.length <= maxChars) return cleaned
            val truncated = cleaned.take(maxChars)
            val lastPeriod = truncated.lastIndexOf('.')
            return if (lastPeriod > maxChars / 2) truncated.substring(0, lastPeriod + 1)
            else "$truncated…"
        }
    }
}
