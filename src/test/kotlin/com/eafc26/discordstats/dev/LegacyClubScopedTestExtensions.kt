package com.eafc26.discordstats.dev

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.LatestMatchHolder
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import org.mockito.ArgumentMatchers

internal val TEST_CLUB = ClubId("1104972")

internal fun clubIdEq(clubId: ClubId): ClubId {
    ArgumentMatchers.eq(clubId.value)
    return clubId
}

internal fun DevSimulatorService.simulateLatest() = simulateLatest(TEST_CLUB)
internal fun DevSimulatorService.reset() = reset(TEST_CLUB)
internal fun LatestMatchHolder.presentation() = presentation(TEST_CLUB)
internal fun LatestMatchHolder.version() = version(TEST_CLUB)
internal fun LatestMatchHolder.isSimulated() = isSimulated(TEST_CLUB)
internal fun LatestMatchHolder.hasPresentation() = hasPresentation(TEST_CLUB)
internal fun LatestMatchHolder.snapshot() = snapshot(TEST_CLUB)
internal fun LatestMatchHolder.clear() = clear(TEST_CLUB)

internal fun PublishedMatchStore.loadRecords() = loadRecords(TEST_CLUB)
internal fun PublishedMatchStore.loadIds() = loadIds(TEST_CLUB)
internal fun PublishedMatchStore.saveRecord(record: PublicationRecord) = saveRecord(TEST_CLUB, record)
internal fun PublishedMatchStore.saveIds(ids: Set<String>) = saveIds(TEST_CLUB, ids)

private val TEST_DESTINATION = DiscordDestination("https://discord.com/api/webhooks/test/token")
internal fun DiscordMatchPublicationService(
    store: PublishedMatchStore,
    webhookClient: DiscordWebhookClient,
    renderer: DiscordRenderer,
    llm: LlmEditorialService,
): DiscordMatchPublicationService {
    val resolver = DiscordDestinationResolver { TEST_DESTINATION }
    return DiscordMatchPublicationService(store, webhookClient, renderer, llm, resolver)
}
