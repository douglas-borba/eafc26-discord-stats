package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.service.MatchHistoryService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class PanoramaController(
    private val llmEditorialService: LlmEditorialService,
    private val matchHistoryService: MatchHistoryService,
    private val defaultClubProvider: DefaultClubProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/api/panorama", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPanorama(
        @RequestParam(required = false) clubId: String? = null,
    ): Mono<ResponseEntity<PanoramaResponse>> =
        Mono.fromCallable {
            val resolvedClubId = resolveClubId(clubId)
            val text = llmEditorialService.getPersistedPanorama(resolvedClubId)
            ResponseEntity.ok(
                PanoramaResponse(
                    status = if (text != null) "success" else "unavailable",
                    text = text,
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/api/panorama/regenerate", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun regeneratePanorama(
        @RequestParam(required = false) clubId: String? = null,
    ): Mono<ResponseEntity<PanoramaResponse>> =
        Mono.fromCallable {
            val resolvedClubId = resolveClubId(clubId)
            val latest = matchHistoryService.latest(resolvedClubId, 1).firstOrNull()
                ?: return@fromCallable ResponseEntity.ok(
                    PanoramaResponse(status = "no_matches", text = null)
                )

            llmEditorialService.generateAndPersistPanorama(latest)
            val text = llmEditorialService.getPersistedPanorama(resolvedClubId)
            ResponseEntity.ok(
                PanoramaResponse(
                    status = if (text != null) "success" else "failed",
                    text = text,
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())

    private fun resolveClubId(requestedClubId: String?): ClubId =
        requestedClubId
            ?.takeIf(String::isNotBlank)
            ?.let(::ClubId)
            ?: defaultClubProvider.get().clubId
}

data class PanoramaResponse(
    val status: String,
    val text: String?,
)
