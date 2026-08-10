package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.ClubCatalogResult
import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.discord.DiscordWebhookSecretStore
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.presentation.editorial.MatchEditorialPresentationRepository
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.service.AcquisitionPhase
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.LatestMatchHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/clubs", produces = [MediaType.APPLICATION_JSON_VALUE])
class ClubAdministrationController(
    private val monitoredClubs: MonitoredClubService,
    private val catalog: ClubCatalogService,
    private val secretStore: DiscordWebhookSecretStore,
    private val pollingStatus: PollingStatusHolder,
    private val acquisitionState: AcquisitionStateHolder,
    private val latestMatch: LatestMatchHolder,
    private val defaultClubProvider: DefaultClubProvider,
    private val editorialRepository: MatchEditorialPresentationRepository?,
) {
    @GetMapping
    fun list(): List<AdminClubResponse> =
        monitoredClubs.list().sortedBy { it.clubId.value }.map(::present)

    @GetMapping("/{clubId}")
    fun get(@PathVariable clubId: String): AdminClubResponse = present(requireClub(clubId))

    @GetMapping("/search")
    fun search(@RequestParam query: String): List<ClubSearchResponse> {
        if (query.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank")
        return when (val result = catalog.search(query)) {
            is ClubCatalogResult.Found -> result.candidates.map {
                ClubSearchResponse(it.clubId.value, it.displayName.value, it.platform.value, it.currentDivision)
            }
            ClubCatalogResult.Empty -> emptyList()
            ClubCatalogResult.Unavailable -> throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "EA club search unavailable")
        }
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@RequestBody request: RegisterClubRequest): AdminClubResponse =
        present(
            monitoredClubs.register(
                ClubId(request.clubId),
                ClubName(request.displayName),
                EaPlatform(request.platform),
                request.monitoringEnabled,
            ),
        )

    @PatchMapping("/{clubId}/monitoring", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun setMonitoring(
        @PathVariable clubId: String,
        @RequestBody request: MonitoringRequest,
    ): AdminClubResponse {
        requireClub(clubId)
        return present(monitoredClubs.setMonitoring(ClubId(clubId), request.enabled))
    }

    @PutMapping("/{clubId}/discord", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun configureDiscord(
        @PathVariable clubId: String,
        @RequestBody request: DiscordWebhookRequest,
    ): AdminClubResponse {
        val club = requireClub(clubId)
        val reference = try {
            secretStore.store(club.clubId, request.webhookUrl)
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Discord webhook")
        }
        return try {
            val updated = monitoredClubs.configureWebhook(club.clubId, reference)
            club.discordWebhookSecretReference?.takeIf { it != reference }?.let(secretStore::remove)
            present(updated)
        } catch (ex: Exception) {
            secretStore.remove(reference)
            throw ex
        }
    }

    @DeleteMapping("/{clubId}/discord")
    fun removeDiscord(@PathVariable clubId: String): AdminClubResponse {
        val club = requireClub(clubId)
        val updated = monitoredClubs.removeWebhook(club.clubId)
        club.discordWebhookSecretReference?.let(secretStore::remove)
        return present(updated)
    }

    @DeleteMapping("/{clubId}")
    fun delete(@PathVariable clubId: String): ResponseEntity<Void> {
        val club = requireClub(clubId)
        if (club.clubId == defaultClubProvider.get().clubId) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "O clube principal não pode ser removido enquanto houver dependências legadas.",
            )
        }
        val webhookRef = club.discordWebhookSecretReference
        if (webhookRef != null) {
            monitoredClubs.removeWebhook(club.clubId)
            secretStore.remove(webhookRef)
        }
        monitoredClubs.remove(club.clubId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{clubId}/status")
    fun status(@PathVariable clubId: String): ClubOperationalStatusResponse {
        val club = requireClub(clubId)
        val acquisition = acquisitionState.current(club.clubId)
        val polling = pollingStatus.current(club.clubId)
        val latest = latestMatch.presentation(club.clubId)

        var latestMatchId = latest?.matchId
        var latestMatchTimestamp = latest?.timestamp
        var lastSuccessAt = acquisition.finishedAt
            ?.takeIf { acquisition.currentPhase == AcquisitionPhase.COMPLETED }
            ?.toString()

        if (latestMatchId == null && editorialRepository != null) {
            try {
                val persisted = editorialRepository.findByClub(club.clubId, limit = 1).firstOrNull()
                if (persisted != null) {
                    latestMatchId = persisted.matchId.value
                    latestMatchTimestamp = persisted.playedAt.toString()
                    if (lastSuccessAt == null) {
                        lastSuccessAt = persisted.updatedAt.toString()
                    }
                }
            } catch (_: Exception) { /* editorial fallback unavailable */ }
        }

        return ClubOperationalStatusResponse(
            clubId = club.clubId.value,
            monitoringEnabled = club.monitoringEnabled,
            acquisitionStatus = acquisition.currentPhase.name,
            pollingStatus = when {
                !club.monitoringEnabled -> "DISABLED"
                polling.running -> "RUNNING"
                else -> "IDLE"
            },
            lastPollAt = polling.lastCheck?.toString(),
            lastSuccessAt = lastSuccessAt,
            lastError = acquisition.currentStatus.takeIf { acquisition.currentPhase == AcquisitionPhase.FAILED },
            latestMatchId = latestMatchId,
            latestMatchTimestamp = latestMatchTimestamp,
            discordConfigured = club.discordWebhookSecretReference != null,
        )
    }

    private fun requireClub(rawClubId: String): MonitoredClub {
        val clubId = try {
            ClubId(rawClubId)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid clubId")
        }
        return monitoredClubs.find(clubId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Monitored club not found")
    }

    private fun present(club: MonitoredClub) = AdminClubResponse(
        clubId = club.clubId.value,
        displayName = club.displayName.value,
        platform = club.platform.value,
        monitoringEnabled = club.monitoringEnabled,
        discordConfigured = club.discordWebhookSecretReference != null,
        isDefault = club.clubId == defaultClubProvider.get().clubId,
    )
}

data class RegisterClubRequest(
    val clubId: String,
    val displayName: String,
    val platform: String,
    val monitoringEnabled: Boolean = true,
)

data class MonitoringRequest(val enabled: Boolean)
data class DiscordWebhookRequest(val webhookUrl: String)

data class AdminClubResponse(
    val clubId: String,
    val displayName: String,
    val platform: String,
    val monitoringEnabled: Boolean,
    val discordConfigured: Boolean,
    val isDefault: Boolean = false,
)

data class ClubSearchResponse(
    val clubId: String,
    val displayName: String,
    val platform: String,
    val currentDivision: Int?,
)

data class ClubOperationalStatusResponse(
    val clubId: String,
    val monitoringEnabled: Boolean,
    val acquisitionStatus: String,
    val pollingStatus: String,
    val lastPollAt: String?,
    val lastSuccessAt: String?,
    val lastError: String?,
    val latestMatchId: String?,
    val latestMatchTimestamp: String?,
    val discordConfigured: Boolean,
)
