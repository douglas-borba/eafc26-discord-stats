package com.eafc26.discordstats.support

import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName

fun defaultClubProvider(clubId: ClubId = ClubId("our-club")): DefaultClubProvider =
    object : DefaultClubProvider {
        override fun get() = DefaultClubConfiguration(
            clubId = clubId,
            displayName = ClubName("Our FC"),
            platform = EaPlatform("common-gen5"),
            webhookSecretReference = null,
        )
    }
