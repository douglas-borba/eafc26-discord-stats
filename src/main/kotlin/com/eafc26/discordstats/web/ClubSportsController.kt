package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.comparison.MatchComparisonResult
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.presentation.comparison.MatchComparisonOptionsResponse
import com.eafc26.discordstats.presentation.comparison.MatchComparisonPresenter
import com.eafc26.discordstats.presentation.comparison.MatchComparisonResponse
import com.eafc26.discordstats.presentation.history.HistoricalMatchDetailResponse
import com.eafc26.discordstats.presentation.history.HistoricalMatchListResponse
import com.eafc26.discordstats.presentation.history.HistoricalMatchPresenter
import com.eafc26.discordstats.presentation.history.HistoricalRepositoryMetadata
import com.eafc26.discordstats.presentation.opponent.OpponentDetailResponse
import com.eafc26.discordstats.presentation.opponent.OpponentHistoryPresenter
import com.eafc26.discordstats.presentation.opponent.OpponentIndexResponse
import com.eafc26.discordstats.presentation.profile.PlayerProfileListResponse
import com.eafc26.discordstats.presentation.profile.PlayerProfilePresenter
import com.eafc26.discordstats.presentation.profile.PlayerProfileResponse
import com.eafc26.discordstats.service.MatchCardService
import com.eafc26.discordstats.service.MatchComparisonService
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.service.OpponentHistoryService
import com.eafc26.discordstats.service.PlayerProfileService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** Definitive, explicitly club-scoped sports read API. */
@RestController
@RequestMapping("/api/clubs/{clubId}", produces = [MediaType.APPLICATION_JSON_VALUE])
class ClubSportsController(
    private val monitoredClubs: MonitoredClubService,
    private val history: MatchHistoryService,
    private val players: PlayerProfileService,
    private val opponents: OpponentHistoryService,
    private val comparisons: MatchComparisonService,
    private val cards: MatchCardService,
    private val editorial: LlmEditorialService,
) {
    @GetMapping
    fun club(@PathVariable clubId: String): SportsClubResponse = requireClub(clubId).let {
        SportsClubResponse(it.clubId.value, it.displayName.value, it.platform.value, it.monitoringEnabled)
    }

    @GetMapping("/history/matches")
    fun matches(@PathVariable clubId: String): HistoricalMatchListResponse = scope(clubId) { id ->
        val matches = history.list(id)
        HistoricalMatchListResponse(
            status = if (matches.isEmpty()) "empty" else "success",
            matches = matches.map(HistoricalMatchPresenter::summary),
            metadata = HistoricalMatchPresenter.metadata(history.metadata(id)),
        )
    }

    @GetMapping("/history/matches/{matchId}")
    fun match(@PathVariable clubId: String, @PathVariable matchId: String): ResponseEntity<HistoricalMatchDetailResponse> =
        scope(clubId) { id ->
            history.findById(id, MatchId(matchId))?.let {
                ResponseEntity.ok(HistoricalMatchDetailResponse("success", HistoricalMatchPresenter.detail(it)))
            } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                HistoricalMatchDetailResponse("not_found", message = "Partida não encontrada no histórico."),
            )
        }

    @GetMapping("/history/metadata")
    fun metadata(@PathVariable clubId: String): HistoricalRepositoryMetadata =
        scope(clubId) { HistoricalMatchPresenter.metadata(history.metadata(it)) }

    @GetMapping("/players")
    fun players(@PathVariable clubId: String): SportsPlayerListResponse = scope(clubId) { id ->
        val result = players.listPlayers(id).mapNotNull { players.findById(id, it.playerId) }.map(PlayerProfilePresenter::profile)
        SportsPlayerListResponse(if (result.isEmpty()) "empty" else "success", result)
    }

    @GetMapping("/players/{playerId}")
    fun player(@PathVariable clubId: String, @PathVariable playerId: String): ResponseEntity<PlayerProfileResponse> =
        scope(clubId) { id ->
            players.findById(id, PlayerId(playerId))?.let {
                ResponseEntity.ok(PlayerProfileResponse("success", PlayerProfilePresenter.profile(it)))
            } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(PlayerProfileResponse("not_found", message = "Jogador não encontrado no histórico."))
        }

    @GetMapping("/opponents")
    fun opponents(@PathVariable clubId: String): OpponentIndexResponse = scope(clubId) { id ->
        OpponentHistoryPresenter.index(opponents.listOpponents(id))
    }

    @GetMapping("/opponents/{opponentId}")
    fun opponent(@PathVariable clubId: String, @PathVariable opponentId: String): ResponseEntity<OpponentDetailResponse> =
        scope(clubId) { id ->
            opponents.findByClubId(id, ClubId(opponentId))?.let {
                ResponseEntity.ok(OpponentHistoryPresenter.detail(it))
            } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(OpponentDetailResponse("not_found", message = "Adversário não encontrado."))
        }

    @GetMapping("/comparisons")
    fun comparisons(
        @PathVariable clubId: String,
        @RequestParam(required = false) firstMatchId: String?,
        @RequestParam(required = false) secondMatchId: String?,
    ): ResponseEntity<Any> = scope(clubId) { id ->
        if (firstMatchId == null && secondMatchId == null) {
            val options = comparisons.listOptions(id)
            return@scope ResponseEntity.ok(MatchComparisonOptionsResponse(if (options.isEmpty()) "empty" else "success", options.map(MatchComparisonPresenter::option)))
        }
        if (firstMatchId == null || secondMatchId == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Both match ids are required")
        when (val result = comparisons.compare(id, MatchId(firstMatchId), MatchId(secondMatchId))) {
            is MatchComparisonResult.Success -> ResponseEntity.ok(MatchComparisonResponse("success", MatchComparisonPresenter.comparison(result.comparison)))
            is MatchComparisonResult.NotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                MatchComparisonResponse("not_found", missingMatchIds = result.missingMatchIds.map { it.value }.sorted(), message = "Uma ou mais partidas não foram encontradas no histórico."),
            )
        }
    }

    @GetMapping("/panorama")
    fun panorama(@PathVariable clubId: String): PanoramaResponse = scope(clubId) { id ->
        val text = editorial.getPersistedPanorama(id)
        PanoramaResponse(if (text == null) "unavailable" else "success", text)
    }

    @GetMapping("/latest-match")
    fun latest(@PathVariable clubId: String): MatchCardResponse = scope(clubId) { id ->
        when (val result = cards.getLatestMatchCard(id)) {
            is MatchCardService.MatchCardResult.Success -> MatchCardResponse(
                status = "success", presentation = result.presentation, version = cards.version(id), simulated = result.simulated,
                publicationStatus = cards.getPublicationStatus(id, result.presentation.matchId),
            )
            MatchCardService.MatchCardResult.NoMatches -> MatchCardResponse("no_matches", "Nenhuma partida encontrada.", version = cards.version(id))
        }
    }

    private fun requireClub(raw: String): MonitoredClub {
        val id = try { ClubId(raw) } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid clubId")
        }
        return monitoredClubs.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored club not found")
    }

    private fun <T> scope(raw: String, block: (ClubId) -> T): T = block(requireClub(raw).clubId)
}

data class SportsClubResponse(val clubId: String, val displayName: String, val platform: String, val monitoringEnabled: Boolean)
data class SportsPlayerListResponse(val status: String, val players: List<com.eafc26.discordstats.presentation.profile.PlayerProfileView>)

@RestController
class PublicClubCatalogController(private val monitoredClubs: MonitoredClubService) {
    @GetMapping("/api/clubs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(): List<SportsClubResponse> = monitoredClubs.list().sortedBy { it.displayName.value.lowercase() }.map {
        SportsClubResponse(it.clubId.value, it.displayName.value, it.platform.value, it.monitoringEnabled)
    }
}
