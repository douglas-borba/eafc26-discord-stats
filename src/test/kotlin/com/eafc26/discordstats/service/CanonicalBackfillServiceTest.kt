package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.eafc26.discordstats.support.defaultClubProvider

class CanonicalBackfillServiceTest {
    private val clubId = "12345"
    private lateinit var gateway: EaClubsGateway
    private lateinit var repository: InMemoryCanonicalRepository
    private lateinit var factory: CanonicalMatchFactory
    private lateinit var service: CanonicalBackfillService

    @BeforeEach
    fun setUp() {
        gateway = mock()
        repository = InMemoryCanonicalRepository()
        factory = CanonicalMatchFactory()
        service = CanonicalBackfillService(
            gateway = gateway,
            props = AppProperties(ea = EaProperties(clubId = clubId, maxResultCount = 20)),
            canonicalMatchFactory = factory,
            canonicalMatchRepository = repository,
            defaultClubProvider = defaultClubProvider(ClubId(clubId)),
        )
        whenever(gateway.getMembersStats(clubId)).thenReturn(EaApiResult.Success(emptyList()))
    }

    @Test
    fun `creates missing records whether or not publication happened elsewhere`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(
            EaApiResult.Success(listOf(match("published-but-missing"), match("unpublished")))
        )

        val result = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(result.created).isEqualTo(2)
        assertThat(result.updated).isZero()
        assertThat(repository.ids()).containsExactlyInAnyOrder("published-but-missing", "unpublished")
    }

    @Test
    fun `mixed window creates missing records and atomically replaces existing records`() {
        val existingSource = match("existing", timestamp = 1)
        repository.save(factory.create(existingSource, clubId))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(
            EaApiResult.Success(listOf(match("new-2", 3), existingSource, match("new-1", 2)))
        )

        val result = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(result.requested).isEqualTo(20)
        assertThat(result.returned).isEqualTo(3)
        assertThat(result.processed).isEqualTo(3)
        assertThat(result.created).isEqualTo(2)
        assertThat(result.updated).isEqualTo(1)
        assertThat(result.before).isEqualTo(1)
        assertThat(result.after).isEqualTo(3)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `repeated execution is idempotent by MatchId`() {
        val window = listOf(match("m1", 1), match("m2", 2))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(window))

        val first = service.backfill() as CanonicalBackfillResult.Completed
        val second = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(first.created).isEqualTo(2)
        assertThat(second.created).isZero()
        assertThat(second.updated).isEqualTo(2)
        assertThat(second.after).isEqualTo(2)
        assertThat(repository.ids()).containsExactlyInAnyOrder("m1", "m2")
        verify(gateway, times(2)).getLatestMatches(clubId)
    }

    @Test
    fun `duplicate MatchId in the EA window is reported as ignored`() {
        val duplicate = match("m1", 1)
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(duplicate, duplicate)))

        val result = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(result.returned).isEqualTo(2)
        assertThat(result.processed).isEqualTo(1)
        assertThat(result.ignored).isEqualTo(1)
        assertThat(result.after).isEqualTo(1)
    }

    @Test
    fun `normalization failure is reported without blocking valid matches`() {
        val invalid = MatchResponse(matchId = "invalid", timestamp = 1, clubs = emptyMap(), players = emptyMap())
        whenever(gateway.getLatestMatches(clubId)).thenReturn(
            EaApiResult.Success(listOf(invalid, match("valid", 2)))
        )

        val result = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(result.created).isEqualTo(1)
        assertThat(result.failures).extracting<String> { it.matchId }.containsExactly("invalid")
        assertThat(repository.ids()).containsExactly("valid")
    }

    @Test
    fun `empty EA window produces a complete zero-change report`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)

        val result = service.backfill() as CanonicalBackfillResult.Completed

        assertThat(result.returned).isZero()
        assertThat(result.processed).isZero()
        assertThat(result.before).isZero()
        assertThat(result.after).isZero()
    }

    @Test
    fun `backfill isolates the same match id by club`() {
        val otherClub = ClubId("8874106")
        val sameIdForOtherClub = MatchResponse(
            matchId = "same-match",
            timestamp = 1,
            clubs = mapOf(
                otherClub.value to ClubMatchEntry(details = ClubDetails(name = "Other FC"), score = "1", result = "1"),
                "opponent" to ClubMatchEntry(details = ClubDetails(name = "Opponent"), score = "0", result = "0"),
            ),
            players = emptyMap(),
        )
        whenever(gateway.getLatestMatches(otherClub.value)).thenReturn(EaApiResult.Success(listOf(sameIdForOtherClub)))
        whenever(gateway.getMembersStats(otherClub.value)).thenReturn(EaApiResult.Success(emptyList()))

        service.backfill(otherClub)

        assertThat(repository.findAll(ClubId(clubId))).isEmpty()
        assertThat(repository.findAll(otherClub)).hasSize(1)
    }

    private fun match(id: String, timestamp: Long = 1): MatchResponse = MatchResponse(
        matchId = id,
        timestamp = timestamp,
        clubs = mapOf(
            clubId to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = "2", result = "1"),
            "opponent" to ClubMatchEntry(details = ClubDetails(name = "Opponent"), score = "1", result = "0"),
        ),
        players = emptyMap(),
    )

    private class InMemoryCanonicalRepository : CanonicalMatchRepository {
        private val records = linkedMapOf<Pair<ClubId, MatchId>, CanonicalMatch>()

        override fun save(match: CanonicalMatch) {
            records[match.interpretation.perspectiveClubId to match.matchId] = match
        }

        override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? =
            records[clubId to matchId]

        override fun findMatchIds(clubId: ClubId): Set<MatchId> =
            records.values.filter { it.interpretation.perspectiveClubId == clubId }.mapTo(linkedSetOf()) { it.matchId }

        override fun findLatestMatchId(clubId: ClubId): MatchId? =
            findAll(clubId).maxByOrNull { it.footballMatch.playedAt }?.matchId

        override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId> =
            candidateMatchIds.filterTo(linkedSetOf()) { records.containsKey(clubId to it) }

        override fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId> =
            findAll(clubId)
                .sortedWith(compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }.thenBy { it.matchId.value })
                .take(limit)
                .map { it.matchId }

        override fun findAll(clubId: ClubId): List<CanonicalMatch> =
            records.values.filter { it.interpretation.perspectiveClubId == clubId }

        override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata =
            findAll(clubId).let { scoped ->
                CanonicalRepositoryMetadata(
                    matchCount = scoped.size,
                    oldestMatchAt = scoped.minOfOrNull { it.footballMatch.playedAt },
                    newestMatchAt = scoped.maxOfOrNull { it.footballMatch.playedAt },
                    lastGeneratedAt = scoped.maxOfOrNull { it.generatedAt },
                    schemaVersions = scoped.mapTo(linkedSetOf()) { it.schemaVersion },
                    engineVersions = scoped.mapTo(linkedSetOf()) { it.engineVersion },
                )
            }

        fun ids(): List<String> = records.keys.map { it.second.value }
    }
}
