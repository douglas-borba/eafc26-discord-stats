package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import org.mockito.ArgumentMatchers

internal val LEGACY_TEST_CLUB = ClubId("12345")

/** Mockito sees Kotlin value classes as their unboxed JVM representation. */
internal fun clubIdEq(clubId: ClubId): ClubId {
    ArgumentMatchers.eq(clubId.value)
    return clubId
}

internal fun MatchAcquisitionService.acquire(trigger: AcquisitionTrigger): AcquisitionResult =
    acquire(LEGACY_TEST_CLUB, trigger)

internal fun MatchAcquisitionService.acquire(
    trigger: AcquisitionTrigger,
    gateway: EaClubsGateway,
): AcquisitionResult = acquire(LEGACY_TEST_CLUB, trigger, gateway)

internal fun LatestMatchHolder.presentation() = presentation(LEGACY_TEST_CLUB)
internal fun LatestMatchHolder.version() = version(LEGACY_TEST_CLUB)
internal fun LatestMatchHolder.isSimulated() = isSimulated(LEGACY_TEST_CLUB)
internal fun LatestMatchHolder.hasPresentation() = hasPresentation(LEGACY_TEST_CLUB)
internal fun LatestMatchHolder.snapshot() = snapshot(LEGACY_TEST_CLUB)
internal fun LatestMatchHolder.update(presentation: MatchSummaryPresentation, simulated: Boolean = false) =
    update(LEGACY_TEST_CLUB, presentation, simulated)
internal fun LatestMatchHolder.clear() = clear(LEGACY_TEST_CLUB)

internal fun AcquisitionStateHolder.current() = current(LEGACY_TEST_CLUB)
internal fun AcquisitionStateHolder.start(trigger: AcquisitionTrigger) = start(LEGACY_TEST_CLUB, trigger)
internal fun AcquisitionStateHolder.enterPhase(phase: AcquisitionPhase, status: String) =
    enterPhase(LEGACY_TEST_CLUB, phase, status)
internal fun AcquisitionStateHolder.complete(status: String) = complete(LEGACY_TEST_CLUB, status)
internal fun AcquisitionStateHolder.fail(error: String, status: String) = fail(LEGACY_TEST_CLUB, error, status)
internal fun AcquisitionStateHolder.recordBusy(trigger: AcquisitionTrigger) = recordBusy(LEGACY_TEST_CLUB, trigger)

internal fun <T> AcquisitionLock.tryRun(action: () -> T): T? = tryRun(LEGACY_TEST_CLUB, action)
internal fun AcquisitionLock.isBusy() = isBusy(LEGACY_TEST_CLUB)

internal fun MatchCardService.getLatestMatchCard() = getLatestMatchCard(LEGACY_TEST_CLUB)
internal fun MatchCardService.version() = version(LEGACY_TEST_CLUB)
internal fun MatchCardService.isSimulated() = isSimulated(LEGACY_TEST_CLUB)

internal fun PublishedMatchStore.loadRecords() = loadRecords(LEGACY_TEST_CLUB)
internal fun PublishedMatchStore.loadIds() = loadIds(LEGACY_TEST_CLUB)
internal fun PublishedMatchStore.saveRecord(record: PublicationRecord) = saveRecord(LEGACY_TEST_CLUB, record)
internal fun PublishedMatchStore.saveIds(ids: Set<String>) = saveIds(LEGACY_TEST_CLUB, ids)
internal fun PublishedMatchStore.removeRecord(matchId: String) = removeRecord(LEGACY_TEST_CLUB, matchId)
internal fun PublishedMatchStore.resolveAsDelivered(matchId: String) = resolveAsDelivered(LEGACY_TEST_CLUB, matchId)
internal fun PublishedMatchStore.resolveAsUndelivered(matchId: String) = resolveAsUndelivered(LEGACY_TEST_CLUB, matchId)

private val LEGACY_TEST_DESTINATION = DiscordDestination("https://discord.com/api/webhooks/test/token")
internal fun DiscordMatchPublicationService(
    store: PublishedMatchStore,
    webhookClient: DiscordWebhookClient,
    renderer: DiscordRenderer,
    llm: LlmEditorialService,
): DiscordMatchPublicationService {
    val resolver = DiscordDestinationResolver { LEGACY_TEST_DESTINATION }
    return DiscordMatchPublicationService(store, webhookClient, renderer, llm, resolver)
}
