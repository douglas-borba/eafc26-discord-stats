package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId

enum class ClubDashboardCapability { OVERVIEW, MATCHES, PLAYERS, OPPONENTS }

/** Single server-side policy for the public dashboard's commercial access. */
class ClubAccessPolicy(private val clubs: MonitoredClubService) {
    fun permits(clubId: ClubId, capability: ClubDashboardCapability): Boolean =
        clubs.find(clubId)?.let { capability == ClubDashboardCapability.OVERVIEW || it.accessStatus.permitsDashboardDepth() } ?: false
}
