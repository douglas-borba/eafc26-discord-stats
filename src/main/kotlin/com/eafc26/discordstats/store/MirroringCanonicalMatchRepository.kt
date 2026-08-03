package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.MatchId
import org.slf4j.LoggerFactory

class MirroringCanonicalMatchRepository(
    private val primary: CanonicalMatchRepository,
    private val mirror: CanonicalMatchRepository,
) : CanonicalMatchRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun save(match: CanonicalMatch) {
        primary.save(match)
        try {
            mirror.save(match)
        } catch (ex: Exception) {
            log.error(
                "PostgreSQL mirror failed for match {} — local JSON is safe",
                match.matchId.value,
                ex,
            )
        }
    }

    override fun findById(matchId: MatchId): CanonicalMatch? = primary.findById(matchId)

    override fun findAll(): List<CanonicalMatch> = primary.findAll()

    override fun metadata(): CanonicalRepositoryMetadata = primary.metadata()
}
