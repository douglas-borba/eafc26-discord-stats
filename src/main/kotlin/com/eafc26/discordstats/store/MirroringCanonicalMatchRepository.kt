package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.slf4j.LoggerFactory

class MirroringCanonicalMatchRepository(
    private val primary: CanonicalMatchRepository,
    private val secondary: CanonicalMatchRepository,
) : CanonicalMatchRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun save(match: CanonicalMatch) {
        primary.save(match)
        try {
            secondary.save(match)
        } catch (ex: Exception) {
            log.error(
                "Secondary repository failed for match {} — primary is safe",
                match.matchId.value,
                ex,
            )
        }
    }

    override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? = primary.findById(clubId, matchId)

    override fun findMatchIds(clubId: ClubId): Set<MatchId> = primary.findMatchIds(clubId)

    override fun findLatestMatchId(clubId: ClubId): MatchId? = primary.findLatestMatchId(clubId)

    override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId> =
        primary.findExistingMatchIds(clubId, candidateMatchIds)

    override fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId> =
        primary.findRecentMatchIds(clubId, limit)

    override fun findRecentOverview(clubId: ClubId, limit: Int): List<CanonicalMatchOverview> =
        primary.findRecentOverview(clubId, limit)

    override fun findAll(clubId: ClubId): List<CanonicalMatch> = primary.findAll(clubId)

    override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> = primary.findRecent(clubId, limit)

    override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata = primary.metadata(clubId)
}
