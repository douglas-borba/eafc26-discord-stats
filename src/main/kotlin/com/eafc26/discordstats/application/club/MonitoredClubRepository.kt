package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.GameVersion
import java.time.Instant

interface MonitoredClubRepository {
    fun save(club: MonitoredClub): MonitoredClub
    /** Updates only the operational EA generation and its audit timestamp. */
    fun updateGameVersion(clubId: ClubId, gameVersion: GameVersion, updatedAt: Instant): MonitoredClub? =
        findById(clubId)?.copy(gameVersion = gameVersion, updatedAt = updatedAt)?.also(::save)
    fun findById(clubId: ClubId): MonitoredClub?
    fun findAll(): List<MonitoredClub>
    fun existsById(clubId: ClubId): Boolean
    fun deleteById(clubId: ClubId): Boolean
}
