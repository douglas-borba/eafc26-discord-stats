package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
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

    override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? = primary.findById(clubId, matchId)

    override fun findAll(clubId: ClubId): List<CanonicalMatch> = primary.findAll(clubId)

    override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata = primary.metadata(clubId)
}
