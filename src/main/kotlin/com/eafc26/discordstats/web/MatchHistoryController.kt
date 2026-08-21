package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.presentation.history.HistoricalMatchDetailResponse
import com.eafc26.discordstats.presentation.history.HistoricalMatchListResponse
import com.eafc26.discordstats.presentation.history.HistoricalMatchPresenter
import com.eafc26.discordstats.service.MatchHistoryService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class MatchHistoryController(
    private val matchHistoryService: MatchHistoryService,
    private val defaultClubProvider: DefaultClubProvider,
) {
    @GetMapping("/history", produces = [MediaType.TEXT_HTML_VALUE])
    fun historyPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("history.html"))

    @GetMapping("/api/history/matches", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listMatches(): Mono<ResponseEntity<HistoricalMatchListResponse>> =
        Mono.fromCallable {
            val clubId = defaultClubProvider.get().clubId
            val matches = matchHistoryService.listSummaries(clubId)
            val response = HistoricalMatchListResponse(
                status = if (matches.isEmpty()) "empty" else "success",
                matches = matches.map(HistoricalMatchPresenter::summary),
                metadata = HistoricalMatchPresenter.metadata(matchHistoryService.metadata(clubId)),
            )
            ResponseEntity.ok(response)
        }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/api/history/matches/{matchId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMatch(
        @PathVariable matchId: String,
    ): Mono<ResponseEntity<HistoricalMatchDetailResponse>> =
        Mono.fromCallable {
            val canonical = matchHistoryService.findById(defaultClubProvider.get().clubId, MatchId(matchId))
            if (canonical == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    HistoricalMatchDetailResponse(
                        status = "not_found",
                        message = "Partida não encontrada no histórico.",
                    )
                )
            } else {
                ResponseEntity.ok(
                    HistoricalMatchDetailResponse(
                        status = "success",
                        match = HistoricalMatchPresenter.detail(canonical),
                    )
                )
            }
        }.subscribeOn(Schedulers.boundedElastic())
}
