package com.eafc26.discordstats.application.repository

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.canonical.CanonicalSchemaVersion
import com.eafc26.discordstats.canonical.EngineVersion
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import java.time.Instant

/**
 * Persistence port for complete canonical match records.
 */
interface CanonicalMatchRepository {
    fun save(match: CanonicalMatch)

    fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch?

    /**
     * Returns all records ordered by match time descending, then ID.
     */
    fun findAll(clubId: ClubId): List<CanonicalMatch>

    fun metadata(clubId: ClubId): CanonicalRepositoryMetadata
}

data class CanonicalRepositoryMetadata(
    val matchCount: Int,
    val oldestMatchAt: Instant?,
    val newestMatchAt: Instant?,
    val lastGeneratedAt: Instant?,
    val schemaVersions: Set<CanonicalSchemaVersion>,
    val engineVersions: Set<EngineVersion>,
)
