package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.presentation.MatchSummaryPresentation

internal val LEGACY_TEST_CLUB = ClubId("12345")

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
