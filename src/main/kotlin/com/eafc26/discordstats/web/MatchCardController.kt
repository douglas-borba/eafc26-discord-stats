package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.service.MatchCardService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class MatchCardController(
    private val matchCardService: MatchCardService,
    private val defaultClubProvider: DefaultClubProvider,
) {

    @GetMapping("/match-card", produces = [MediaType.TEXT_HTML_VALUE])
    fun matchCardPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("match-card.html"))

    @GetMapping("/api/match-card/latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLatestMatchCard(): Mono<ResponseEntity<MatchCardResponse>> =
        Mono.fromCallable {
            val clubId = defaultClubProvider.get().clubId
            clubId to matchCardService.getLatestMatchCard(clubId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .map { (clubId, result) ->
                when (result) {
                    is MatchCardService.MatchCardResult.Success -> {
                        val pubStatus = matchCardService.getPublicationStatus(result.presentation.matchId)
                        ResponseEntity.ok(MatchCardResponse(
                            status = "success",
                            presentation = result.presentation,
                            version = matchCardService.version(clubId),
                            simulated = result.simulated,
                            publicationStatus = pubStatus,
                        ))
                    }
                    MatchCardService.MatchCardResult.NoMatches ->
                        ResponseEntity.ok(MatchCardResponse(
                            status = "no_matches",
                            message = "Nenhuma partida encontrada. Aguarde a primeira aquisição.",
                            version = matchCardService.version(clubId),
                        ))
                }
            }
}

data class MatchCardResponse(
    val status: String,
    val message: String? = null,
    val presentation: MatchSummaryPresentation? = null,
    val version: Long? = null,
    val simulated: Boolean = false,
    val publicationStatus: PublicationStatus? = null,
)

data class PublicationStatus(
    val state: String,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val lastHttpStatus: Int? = null,
)
