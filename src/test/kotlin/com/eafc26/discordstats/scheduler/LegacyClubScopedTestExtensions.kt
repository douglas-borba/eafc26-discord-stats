package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.support.defaultClubProvider

private val TEST_CLUB = ClubId("1104972")

internal fun MatchAcquisitionService.acquire(trigger: AcquisitionTrigger): AcquisitionResult =
    acquire(TEST_CLUB, trigger)

internal fun MatchPollingScheduler(
    acquisitionService: MatchAcquisitionService,
    statusHolder: PollingStatusHolder,
) = MatchPollingScheduler(acquisitionService, statusHolder, defaultClubProvider(TEST_CLUB))

internal val PollingStatusHolder.intervalSeconds get() = current(TEST_CLUB).intervalSeconds
internal val PollingStatusHolder.running get() = current(TEST_CLUB).running
internal val PollingStatusHolder.lastCheck get() = current(TEST_CLUB).lastCheck
internal val PollingStatusHolder.nextCheck get() = current(TEST_CLUB).nextCheck
internal val PollingStatusHolder.lastResult get() = current(TEST_CLUB).lastResult
internal var PollingStatusHolder.enabled: Boolean
    get() = current(TEST_CLUB).enabled
    set(value) { update(TEST_CLUB) { it.copy(enabled = value) } }
