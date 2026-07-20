package com.eafc26.discordstats.web

import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * REST controller for match operations.
 *
 * All acquisition operations flow through [MatchAcquisitionService],
 * which is the single orchestrator for the acquisition pipeline.
 */
@RestController
class MatchController(
    private val acquisitionService: MatchAcquisitionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/api/health", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "ok"))

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun index(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("index.html"))

    /**
     * Manual trigger to check and publish the latest match.
     *
     * Uses [AcquisitionTrigger.MANUAL] which processes only the latest match
     * (unlike SCHEDULER which processes all new matches).
     */
    @PostMapping("/api/matches/notify-latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun notifyLatest(): Mono<ResponseEntity<NotifyResponse>> =
        Mono.fromCallable { acquisitionService.acquire(AcquisitionTrigger.MANUAL) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> mapAcquisitionResult(result) }

    /**
     * Force-resend the latest match to Discord, bypassing the deduplication check.
     *
     * Uses [AcquisitionTrigger.FORCE_RESEND] which sends to Discord without
     * checking if the match was already published, and does not persist the match ID.
     */
    @PostMapping("/api/matches/resend-latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun resendLatest(): Mono<ResponseEntity<NotifyResponse>> =
        Mono.fromCallable { acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> mapAcquisitionResult(result) }

    /**
     * Maps [AcquisitionResult] to the REST response format.
     *
     * This maintains backward compatibility with the existing API contract
     * that the dashboard expects.
     */
    private fun mapAcquisitionResult(result: AcquisitionResult): ResponseEntity<NotifyResponse> =
        when (result) {
            is AcquisitionResult.Processed -> {
                when {
                    result.hasPublished() -> {
                        val summary = result.latestSummary()
                        val persisted = result.published.lastOrNull()?.persistedSuccessfully ?: true
                        if (persisted) {
                            ResponseEntity.ok(NotifyResponse("sent", "Notificação enviada com sucesso.", summary))
                        } else {
                            ResponseEntity.ok(NotifyResponse("sent_persistence_error", 
                                "A partida foi enviada ao Discord, mas não foi possível salvar o histórico local.", summary))
                        }
                    }
                    result.allSkipped() -> {
                        val summary = result.latestSummary()
                        ResponseEntity.ok(NotifyResponse("already_published", "Partida já publicada anteriormente.", summary))
                    }
                    result.failed.isNotEmpty() -> {
                        val failure = result.failed.first()
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(NotifyResponse("discord_error", "Falha ao enviar para o Discord: ${failure.reason}"))
                    }
                    result.baselineEstablished -> {
                        ResponseEntity.ok(NotifyResponse("baseline_established", "Histórico inicial configurado."))
                    }
                    else -> {
                        ResponseEntity.ok(NotifyResponse("no_matches", "Nenhuma partida encontrada."))
                    }
                }
            }
            is AcquisitionResult.ForceResent -> {
                ResponseEntity.ok(NotifyResponse("force_sent", "Partida reenviada com sucesso.", result.match.summary))
            }
            AcquisitionResult.NoMatches -> {
                ResponseEntity.ok(NotifyResponse("no_matches", "Nenhuma partida encontrada."))
            }
            is AcquisitionResult.EaUnavailable -> {
                ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(NotifyResponse("ea_unavailable", "API da EA indisponível. Tente novamente mais tarde."))
            }
            AcquisitionResult.WebhookNotConfigured -> {
                ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(NotifyResponse("webhook_not_configured", "Webhook do Discord não configurado."))
            }
            AcquisitionResult.Busy -> {
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(NotifyResponse("busy", "Outra notificação já está em andamento."))
            }
        }
}
