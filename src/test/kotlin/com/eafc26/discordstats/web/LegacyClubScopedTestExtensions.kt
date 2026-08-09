package com.eafc26.discordstats.web

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.MatchCardService
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.support.defaultClubProvider

private val TEST_CLUB = ClubId("1104972")

internal fun MatchAcquisitionService.acquire(trigger: AcquisitionTrigger): AcquisitionResult =
    acquire(TEST_CLUB, trigger)

internal fun MatchController(acquisitionService: MatchAcquisitionService) =
    MatchController(acquisitionService, defaultClubProvider(TEST_CLUB))

internal fun MatchCardController(matchCardService: MatchCardService) =
    MatchCardController(matchCardService, defaultClubProvider(TEST_CLUB))

internal fun PollingStatusController(
    statusHolder: PollingStatusHolder,
    acquisitionStateHolder: AcquisitionStateHolder,
) = PollingStatusController(statusHolder, acquisitionStateHolder, defaultClubProvider(TEST_CLUB))
