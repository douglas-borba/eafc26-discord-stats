package com.eafc26.discordstats.dev

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.LatestMatchHolder

private val TEST_CLUB = ClubId("1104972")

internal fun DevSimulatorService.simulateLatest() = simulateLatest(TEST_CLUB)
internal fun DevSimulatorService.reset() = reset(TEST_CLUB)
internal fun LatestMatchHolder.presentation() = presentation(TEST_CLUB)
internal fun LatestMatchHolder.version() = version(TEST_CLUB)
internal fun LatestMatchHolder.isSimulated() = isSimulated(TEST_CLUB)
internal fun LatestMatchHolder.hasPresentation() = hasPresentation(TEST_CLUB)
internal fun LatestMatchHolder.snapshot() = snapshot(TEST_CLUB)
internal fun LatestMatchHolder.clear() = clear(TEST_CLUB)
