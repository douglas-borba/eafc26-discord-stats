package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.presentation.opponent.OpponentDetailResponse
import com.eafc26.discordstats.presentation.opponent.OpponentHistoryPresenter
import com.eafc26.discordstats.presentation.opponent.OpponentIndexResponse
import com.eafc26.discordstats.service.OpponentHistoryService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class OpponentHistoryController(
    private val opponentHistoryService: OpponentHistoryService,
    private val defaultClubProvider: DefaultClubProvider,
) {
    @GetMapping("/opponents", "/opponents/{clubId}")
    fun page(@PathVariable(required = false) clubId: String?): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).header("Location", "/history").build()

    @GetMapping("/insights")
    fun legacyRedirect(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.FOUND).header("Location", "/history").build()

    @GetMapping("/api/opponents", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(): Mono<ResponseEntity<OpponentIndexResponse>> = Mono.fromCallable {
        ResponseEntity.ok(OpponentHistoryPresenter.index(opponentHistoryService.listOpponents(defaultClubProvider.get().clubId)))
    }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/api/opponents/{clubId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun detail(@PathVariable clubId: String): Mono<ResponseEntity<OpponentDetailResponse>> = Mono.fromCallable {
        val history = opponentHistoryService.findByClubId(defaultClubProvider.get().clubId, ClubId(clubId))
        if (history == null) ResponseEntity.status(HttpStatus.NOT_FOUND).body(OpponentDetailResponse("not_found", message = "Adversário não encontrado."))
        else ResponseEntity.ok(OpponentHistoryPresenter.detail(history))
    }.subscribeOn(Schedulers.boundedElastic())
}
