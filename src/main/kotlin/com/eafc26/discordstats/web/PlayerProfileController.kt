package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.presentation.profile.PlayerProfileListResponse
import com.eafc26.discordstats.presentation.profile.PlayerProfilePresenter
import com.eafc26.discordstats.presentation.profile.PlayerProfileResponse
import com.eafc26.discordstats.service.PlayerProfileService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class PlayerProfileController(
    private val playerProfileService: PlayerProfileService,
    private val defaultClubProvider: DefaultClubProvider,
) {
    @GetMapping("/players", produces = [MediaType.TEXT_HTML_VALUE])
    fun playersPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("players.html"))

    @GetMapping("/api/player-profiles", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listPlayers(): Mono<ResponseEntity<PlayerProfileListResponse>> =
        Mono.fromCallable {
            val players = playerProfileService.listPlayers(defaultClubProvider.get().clubId)
            ResponseEntity.ok(
                PlayerProfileListResponse(
                    status = if (players.isEmpty()) "empty" else "success",
                    players = players.map(PlayerProfilePresenter::listItem),
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/api/player-profiles/detail", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getProfile(
        @RequestParam playerId: String,
    ): Mono<ResponseEntity<PlayerProfileResponse>> =
        Mono.fromCallable {
            val profile = playerProfileService.findById(defaultClubProvider.get().clubId, PlayerId(playerId))
            if (profile == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    PlayerProfileResponse(
                        status = "not_found",
                        message = "Jogador não encontrado no histórico.",
                    )
                )
            } else {
                ResponseEntity.ok(
                    PlayerProfileResponse(
                        status = "success",
                        profile = PlayerProfilePresenter.profile(profile),
                    )
                )
            }
        }.subscribeOn(Schedulers.boundedElastic())
}
