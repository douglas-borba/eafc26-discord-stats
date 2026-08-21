package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId

/**
 * Abstraction over publication state persistence, implemented by both the
 * file-based [PublishedMatchStore] and the Postgres-backed [PostgresPublishedMatchStore].
 *
 * Spring resolves the `@Primary` implementation (Postgres, when enabled) for any
 * consumer that depends on this interface type instead of a concrete store class.
 */
interface PublicationStateStore {
    fun loadRecords(clubId: ClubId): Map<String, PublicationRecord>
    fun find(clubId: ClubId, matchId: String): PublicationRecord?
    fun saveRecord(clubId: ClubId, record: PublicationRecord)

    /**
     * Persists an initial publication intent without replacing an existing delivery
     * history. Returns true only when this call created the record.
     */
    fun createRecordIfAbsent(clubId: ClubId, record: PublicationRecord): Boolean

    /**
     * Atomically claims one safe automatic-delivery record. Only the caller receiving
     * the resulting DELIVERING record owns the right to execute the Discord HTTP call.
     */
    fun claimForAutomaticDelivery(
        clubId: ClubId,
        expected: PublicationRecord,
        attemptedAt: java.time.Instant,
    ): PublicationRecord?

    fun removeRecord(clubId: ClubId, matchId: String)
    fun resolveAsDelivered(clubId: ClubId, matchId: String)
    fun resolveAsUndelivered(clubId: ClubId, matchId: String)
    fun loadIds(clubId: ClubId): Set<String>
    fun saveIds(clubId: ClubId, ids: Set<String>)
    fun metadata(clubId: ClubId): PublicationStoreMetadata
}
