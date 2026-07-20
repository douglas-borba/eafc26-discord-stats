package com.eafc26.discordstats.web

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
) {

    @GetMapping("/match-card", produces = [MediaType.TEXT_HTML_VALUE])
    fun matchCardPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("match-card.html"))

    @GetMapping("/api/match-card/latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLatestMatchCard(): Mono<ResponseEntity<MatchCardResponse>> =
        Mono.fromCallable { matchCardService.getLatestMatchCard() }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result ->
                when (result) {
                    is MatchCardService.MatchCardResult.Success ->
                        ResponseEntity.ok(MatchCardResponse(
                            status = "success",
                            presentation = result.presentation,
                            version = matchCardService.version(),
                        ))
                    MatchCardService.MatchCardResult.NoMatches ->
                        ResponseEntity.ok(MatchCardResponse(
                            status = "no_matches",
                            message = "Nenhuma partida encontrada. Aguarde a primeira aquisição.",
                            version = matchCardService.version(),
                        ))
                }
            }
}

data class MatchCardResponse(
    val status: String,
    val message: String? = null,
    val presentation: MatchSummaryPresentation? = null,
    val version: Long? = null,
)

