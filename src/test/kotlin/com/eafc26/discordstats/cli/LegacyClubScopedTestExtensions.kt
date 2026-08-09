package com.eafc26.discordstats.cli

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService

private val TEST_CLUB = ClubId("12345")

internal fun MatchAcquisitionService.acquire(trigger: AcquisitionTrigger): AcquisitionResult =
    acquire(TEST_CLUB, trigger)
