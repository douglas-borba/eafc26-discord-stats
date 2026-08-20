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
     * Returns canonical identifiers without loading or deserializing full match payloads.
     *
     * This remains available for consumers that explicitly need the complete
     * identifier archive. Bounded polling must use [findExistingMatchIds].
     */
    fun findMatchIds(clubId: ClubId): Set<MatchId>

    /**
     * Returns the newest canonical identifier for [clubId], or null when no
     * canonical history exists. This is a lightweight bootstrap check for
     * bounded acquisition. Database-backed implementations must not load
     * canonical payloads for this lookup.
     */
    fun findLatestMatchId(clubId: ClubId): MatchId?

    /**
     * Returns the subset of [candidateMatchIds] that already exists for
     * [clubId], without loading canonical payloads.
     *
     * The candidate collection comes from a bounded EA window. Implementations
     * must return an empty set safely when it is empty.
     */
    fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId>

    /**
     * Returns only recent canonical identifiers, ordered by match time
     * descending and then ID. Database-backed implementations must apply
     * [limit] without reading canonical payloads.
     */
    fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId>

    /**
     * Returns all records ordered by match time descending, then ID.
     */
    fun findAll(clubId: ClubId): List<CanonicalMatch>

    /**
     * Returns the most recent records ordered by match time descending, then ID.
     *
     * Implementations backed by a database must apply [limit] before loading full
     * canonical payloads. The default preserves compatibility for repositories that
     * cannot select a subset without reading their local documents.
     */
    fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> {
        require(limit >= 0) { "limit must be non-negative" }
        return findAll(clubId).take(limit)
    }

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
