package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationStateStore
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate

/**
 * Persists a new canonical match and its initial Discord publication intent in the
 * same PostgreSQL transaction. The JSON mirror remains best-effort, matching the
 * established primary-first mirroring behaviour.
 */
interface CanonicalPublicationPersistence {
    /** Returns true only when [initialPublication] was newly inserted. */
    fun persist(canonical: CanonicalMatch, initialPublication: PublicationRecord?): Boolean
}

class PostgresCanonicalPublicationPersistence(
    private val canonicalRepository: PostgresCanonicalMatchRepository,
    private val publicationStore: PublicationStateStore,
    private val jsonMirror: JsonCanonicalMatchRepository,
    private val transactions: TransactionTemplate,
) : CanonicalPublicationPersistence {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun persist(canonical: CanonicalMatch, initialPublication: PublicationRecord?): Boolean {
        val publicationCreated = transactions.execute {
            canonicalRepository.save(canonical)
            initialPublication?.let {
                publicationStore.createRecordIfAbsent(canonical.interpretation.perspectiveClubId, it)
            } ?: false
        } ?: false

        try {
            jsonMirror.save(canonical)
        } catch (ex: Exception) {
            log.error(
                "Secondary repository failed for match {} after authoritative publication transaction — primary is safe",
                canonical.matchId.value,
                ex,
            )
        }
        return publicationCreated
    }
}
