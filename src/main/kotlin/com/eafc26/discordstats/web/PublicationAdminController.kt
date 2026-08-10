package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.PublicationReconciliationService
import com.eafc26.discordstats.service.PublicationStateClassifier
import com.eafc26.discordstats.store.PublishedMatchStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class PublicationAdminController(
    private val store: PublishedMatchStore,
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val publicationService: DiscordMatchPublicationService,
    private val reconciliationService: PublicationReconciliationService,
    private val monitoredClubRepository: MonitoredClubRepository,
) {

    private fun requireClub(clubId: String): ClubId {
        val id = ClubId(clubId)
        if (!monitoredClubRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found: $clubId")
        }
        return id
    }

    @PostMapping("/api/admin/clubs/{clubId}/publication/{matchId}/resolve-delivered")
    fun resolveAsDelivered(
        @PathVariable clubId: String,
        @PathVariable matchId: String,
    ): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val club = requireClub(clubId)
            store.resolveAsDelivered(club, matchId)
            ResponseEntity.ok<Map<String, Any>>(mapOf(
                "status" to "resolved",
                "message" to "Partida marcada como publicada"
            ))
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/api/admin/clubs/{clubId}/publication/{matchId}/force-publish")
    fun forcePublish(
        @PathVariable clubId: String,
        @PathVariable matchId: String,
    ): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val club = requireClub(clubId)
            val canonical = canonicalMatchRepository.findById(club, MatchId(matchId))
                ?: return@fromCallable ResponseEntity.notFound().build<Map<String, Any>>()

            val result = publicationService.forcePublish(club, canonical)

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

    @GetMapping("/api/admin/clubs/{clubId}/publication/reconcile/inspect")
    fun inspectPublications(@PathVariable clubId: String): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val club = requireClub(clubId)
            val report = reconciliationService.inspectLatestPublications(club, limit = 5)

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
                    "baselined" to report.summary.baselined,
                ),
            ))
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/api/admin/clubs/{clubId}/publication/reconcile/auto-publish")
    fun autoPublishSafe(@PathVariable clubId: String): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val club = requireClub(clubId)
            val result = reconciliationService.autoPublishSafe(club)

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
