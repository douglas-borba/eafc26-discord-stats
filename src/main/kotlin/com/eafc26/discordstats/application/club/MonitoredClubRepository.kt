package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId

interface MonitoredClubRepository {
    fun save(club: MonitoredClub): MonitoredClub
    fun findById(clubId: ClubId): MonitoredClub?
    fun findAll(): List<MonitoredClub>
    fun existsById(clubId: ClubId): Boolean
}
