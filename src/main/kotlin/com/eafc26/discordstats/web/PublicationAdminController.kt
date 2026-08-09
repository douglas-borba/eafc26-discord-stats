package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.PublicationReconciliationService
import com.eafc26.discordstats.service.PublicationStateClassifier
import com.eafc26.discordstats.store.PublishedMatchStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Administrative endpoints for manual publication resolution.
 *
 * These endpoints are used when automatic publication fails or is uncertain.
 * They require explicit user action and are protected by CSRF.
 */
@RestController
class PublicationAdminController(
    private val store: PublishedMatchStore,
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val publicationService: DiscordMatchPublicationService,
    private val reconciliationService: PublicationReconciliationService,
    private val defaultClubProvider: DefaultClubProvider,
) {

    /**
     * Marks a match as delivered without calling Discord.
     *
     * Use this when you manually verified that the message reached Discord
     * and want to mark it as delivered to prevent future resend attempts.
     */
    @PostMapping("/api/publication/{matchId}/resolve-delivered")
    fun resolveAsDelivered(@PathVariable matchId: String): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            store.resolveAsDelivered(defaultClubProvider.get().clubId, matchId)
            ResponseEntity.ok<Map<String, Any>>(mapOf(
                "status" to "resolved",
                "message" to "Partida marcada como publicada"
            ))
        }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Forces a resend of a match, bypassing deduplication.
     *
     * Use this when you manually verified that the message did NOT reach Discord
     * and want to attempt delivery again. This may cause duplication if the
     * previous attempt actually succeeded.
     */
    @PostMapping("/api/publication/{matchId}/force-publish")
    fun forcePublish(@PathVariable matchId: String): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val canonical = canonicalMatchRepository.findById(defaultClubProvider.get().clubId, MatchId(matchId))
                ?: return@fromCallable ResponseEntity.notFound().build<Map<String, Any>>()

            val result = publicationService.forcePublish(canonical)

            val message = when {
                result.delivered -> "Partida reenviada com sucesso"
                result.errorMessage != null -> "Falha ao reenviar: ${result.errorMessage}"
                else -> "Falha ao reenviar"
            }

            ResponseEntity.ok<Map<String, Any>>(mapOf(
                "status" to if (result.delivered) "success" else "failed",
                "message" to message,
                "outcome" to result.outcome.name,
            ))
        }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Inspects the latest matches and their publication states.
     *
     * Returns detailed diagnostic information for administrative review.
     * Default limit is 5 matches as specified in requirements.
     */
    @GetMapping("/api/publication/reconcile/inspect")
    fun inspectPublications(): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val report = reconciliationService.inspectLatestPublications(defaultClubProvider.get().clubId, limit = 5)

            ResponseEntity.ok<Map<String, Any>>(mapOf(
                "inspections" to report.inspections.map { inspection ->
                    val display = PublicationStateClassifier.getDisplayInfo(inspection.publicationState)
                    mapOf(
                        "matchId" to inspection.matchId,
                        "matchDate" to inspection.matchDate.toString(),
                        "score" to "${inspection.ourScore} × ${inspection.oppScore}",
                        "publicationState" to (inspection.publicationState?.name ?: "NEVER_ATTEMPTED"),
                        "attemptCount" to inspection.attemptCount,
                        "lastAttemptAt" to inspection.lastAttemptAt?.toString(),
                        "lastError" to inspection.lastError,
                        "lastHttpStatus" to inspection.lastHttpStatus,
                        "safeToAutoPublish" to inspection.safeToAutoPublish,
                        "display" to mapOf(
                            "icon" to display.icon,
                            "label" to display.label,
                            "color" to display.color,
                            "requiresAction" to display.requiresAction,
                        ),
                    )
                },
                "summary" to mapOf(
                    "totalInspected" to report.summary.totalInspected,
                    "delivered" to report.summary.delivered,
                    "neverAttempted" to report.summary.neverAttempted,
                    "delivering" to report.summary.delivering,
                    "uncertain" to report.summary.uncertain,
                    "failedPermanent" to report.summary.failedPermanent,
                ),
            ))
        }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Automatically publishes all safe matches.
     *
     * Safe conditions:
     * - No publication record exists (never attempted)
     * - FAILED_PERMANENT (Discord explicitly rejected, safe to retry)
     *
     * NEVER auto-publishes DELIVERY_UNCERTAIN matches.
     */
    @PostMapping("/api/publication/reconcile/auto-publish")
    fun autoPublishSafe(): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val result = reconciliationService.autoPublishSafe(defaultClubProvider.get().clubId)

            ResponseEntity.ok<Map<String, Any>>(mapOf(
                "status" to "completed",
                "publishedCount" to result.publishedCount,
                "skippedCount" to result.skippedCount,
                "errorCount" to result.errorCount,
                "published" to result.published,
                "errors" to result.errors.map { error ->
                    mapOf(
                        "matchId" to error.matchId,
                        "error" to error.error,
                        "outcome" to error.outcome,
                    )
                },
                "message" to when {
                    result.publishedCount > 0 && result.errorCount == 0 ->
                        "${result.publishedCount} partida(s) publicada(s) com sucesso"
                    result.publishedCount > 0 && result.errorCount > 0 ->
                        "${result.publishedCount} publicada(s), ${result.errorCount} falha(s)"
                    result.errorCount > 0 ->
                        "Todas as tentativas falharam (${result.errorCount})"
                    else ->
                        "Nenhuma partida segura para publicar automaticamente"
                },
            ))
        }.subscribeOn(Schedulers.boundedElastic())
}
