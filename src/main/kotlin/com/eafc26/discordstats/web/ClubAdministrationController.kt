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
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.PublicationStateStore
import com.eafc26.discordstats.store.PublicationState
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
import org.slf4j.LoggerFactory

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
    private val eventRepository: OperationalEventRepository? = null,
    private val publicationStore: PublicationStateStore? = null,
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
        if (secretStore.resolve(reference) == null) {
            secretStore.remove(reference)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Discord webhook could not be persisted")
        }
        val updated = try {
            monitoredClubs.configureWebhook(club.clubId, reference)
        } catch (ex: Exception) {
            secretStore.remove(reference)
            throw ex
        }
        club.discordWebhookSecretReference?.takeIf { it != reference }?.let { previous ->
            runCatching { secretStore.remove(previous) }
                .onFailure { logger.warn("Previous Discord webhook secret could not be removed after replacement") }
        }
        return present(updated)
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
        var lastPollAt = polling.lastCheck?.toString()
        var lastError = acquisition.currentStatus.takeIf { acquisition.currentPhase == AcquisitionPhase.FAILED }

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

        // Fall back to persisted operational events when in-memory state has nothing.
        if ((lastPollAt == null || lastSuccessAt == null || lastError == null) && eventRepository != null) {
            try {
                if (lastPollAt == null) {
                    eventRepository.findLatestByClubAndType(club.clubId, "POLLING")?.let {
                        lastPollAt = it.createdAt.toString()
                    }
                }
                if (lastSuccessAt == null) {
                    eventRepository.findByClub(club.clubId, limit = 50)
                        .firstOrNull { it.eventType == "ACQUISITION" && it.status == EventStatus.SUCCESS }
                        ?.let { lastSuccessAt = it.createdAt.toString() }
                }
                if (lastError == null) {
                    eventRepository.findByClub(club.clubId, limit = 50)
                        .firstOrNull { it.status == EventStatus.FAILURE }
                        ?.let { lastError = it.message }
                }
            } catch (_: Exception) { /* event fallback unavailable */ }
        }

        var lastDiscordSuccess: String? = null
        var lastDiscordError: String? = null
        if (eventRepository != null) {
            try {
                val discordEvents = eventRepository.findByClub(club.clubId, limit = 50)
                    .filter { it.eventType == "DISCORD" }
                lastDiscordSuccess = discordEvents.firstOrNull { it.status == EventStatus.SUCCESS }?.createdAt?.toString()
                lastDiscordError = discordEvents.firstOrNull { it.status == EventStatus.FAILURE }?.message
            } catch (_: Exception) { /* discord event fallback unavailable */ }
        }

        val hasUncertainOrPermanentFailure = publicationStore?.let { pubStore ->
            try {
                pubStore.loadRecords(club.clubId).values.any {
                    it.state == PublicationState.DELIVERY_UNCERTAIN || it.state == PublicationState.FAILED_PERMANENT || it.state == PublicationState.FAILED_TRANSIENT
                }
            } catch (_: Exception) {
                false
            }
        } ?: false

        val healthIndicator = when {
            lastPollAt == null && latestMatchId == null -> "idle"
            lastError != null -> "error"
            hasUncertainOrPermanentFailure -> "warning"
            else -> "healthy"
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
            lastPollAt = lastPollAt,
            lastSuccessAt = lastSuccessAt,
            lastError = lastError,
            latestMatchId = latestMatchId,
            latestMatchTimestamp = latestMatchTimestamp,
            discordConfigured = isDiscordDestinationResolvable(club),
            lastDiscordSuccess = lastDiscordSuccess,
            lastDiscordError = lastDiscordError,
            healthIndicator = healthIndicator,
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

    private fun present(club: MonitoredClub): AdminClubResponse {
        val referencePresent = club.discordWebhookSecretReference != null
        val destinationResolvable = isDiscordDestinationResolvable(club)
        return AdminClubResponse(
        clubId = club.clubId.value,
        displayName = club.displayName.value,
        platform = club.platform.value,
        monitoringEnabled = club.monitoringEnabled,
        discordConfigured = destinationResolvable,
        discordReferencePresent = referencePresent,
        discordDestinationResolvable = destinationResolvable,
        isDefault = club.clubId == defaultClubProvider.get().clubId,
        )
    }

    private fun isDiscordDestinationResolvable(club: MonitoredClub): Boolean =
        club.discordWebhookSecretReference?.let(secretStore::resolve) != null

    companion object {
        private val logger = LoggerFactory.getLogger(ClubAdministrationController::class.java)
    }
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
    val discordReferencePresent: Boolean = false,
    val discordDestinationResolvable: Boolean = false,
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
    val lastDiscordSuccess: String? = null,
    val lastDiscordError: String? = null,
    val healthIndicator: String = "idle",
)
