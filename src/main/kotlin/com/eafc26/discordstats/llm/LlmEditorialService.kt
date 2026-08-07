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
        return panoramaRepository?.findLatestSuccessful(clubId)?.narrative
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
     * Detecta se o texto parece ser um echo do prompt em vez de uma resposta real.
     * 
     * Alguns modelos gratuitos/instáveis ocasionalmente retornam o próprio prompt.
     * Esta função valida baseada em padrões comuns de instruções.
     */
    private fun isPromptEcho(text: String): Boolean {
        val lowerText = text.lowercase()
        
        // Padrões de instruções em inglês
        val englishPatterns = listOf(
            "we need to",
            "must compare",
            "must mention",
            "must use",
            "use exact values",
            "should not",
            "between 350 and 550 characters",
            "between 120 and 220 characters",
            "in prose",
            "no lists",
            "no markdown",
            "no emojis",
        )
        
        // Padrões de instruções em português
        val portuguesePatterns = listOf(
            "escreva 2-3 frases",
            "escreva entre",
            "utilize apenas os fatos",
            "utilize apenas os dados",
            "não utilize markdown",
            "compare as partidas",
            "compare as 10 partidas",
            "entre 350 e 550 caracteres",
            "entre 120 e 220 caracteres",
            "sem listas",
            "sem markdown",
            "sem emoji",
            "você deve analisar",
            "você deve comparar",
        )
        
        // Contar quantos padrões foram encontrados
        val englishMatches = englishPatterns.count { lowerText.contains(it) }
        val portugueseMatches = portuguesePatterns.count { lowerText.contains(it) }
        
        // Se encontrar 3 ou mais padrões de qualquer idioma, provavelmente é o prompt
        return englishMatches >= 3 || portugueseMatches >= 3
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
