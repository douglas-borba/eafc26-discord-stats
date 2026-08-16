package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class MirroringCanonicalMatchRepositoryTest {

    private val primary = RecordingRepository()
    private val mirror = RecordingRepository()
    private val repo = MirroringCanonicalMatchRepository(primary, mirror)

    @Test
    fun `save writes to both primary and mirror`() {
        val match = testMatch("m1")
        repo.save(match)
        assertThat(primary.saved).containsExactly("m1")
        assertThat(mirror.saved).containsExactly("m1")
    }

    @Test
    fun `mirror failure does not prevent primary save`() {
        mirror.failOnSave = true
        val match = testMatch("m1")
        repo.save(match)
        assertThat(primary.saved).containsExactly("m1")
    }

    @Test
    fun `primary failure propagates without calling mirror`() {
        primary.failOnSave = true
        assertThatThrownBy { repo.save(testMatch("m1")) }
            .isInstanceOf(RuntimeException::class.java)
        assertThat(mirror.saved).isEmpty()
    }

    @Test
    fun `findById delegates to primary only`() {
        val match = testMatch("m1")
        primary.store[match.matchId] = match
        mirror.store[match.matchId] = match

        assertThat(repo.findById(CLUB_ID, MatchId("m1"))).isEqualTo(match)
        assertThat(repo.findById(CLUB_ID, MatchId("missing"))).isNull()
    }

    @Test
    fun `findAll delegates to primary only`() {
        primary.store[MatchId("m1")] = testMatch("m1")
        assertThat(repo.findAll(CLUB_ID)).hasSize(1)
    }

    @Test
    fun `findRecent delegates to primary only`() {
        primary.store[MatchId("m1")] = testMatch("m1")
        mirror.failOnRecentRead = true

        assertThat(repo.findRecent(CLUB_ID, 1)).hasSize(1)
        assertThat(primary.recentReads).isEqualTo(1)
        assertThat(mirror.recentReads).isZero()
    }

    @Test
    fun `metadata delegates to primary only`() {
        assertThat(repo.metadata(CLUB_ID).matchCount).isZero()
    }

    private fun testMatch(id: String): CanonicalMatch {
        val source = MatchResponse(
            matchId = id, timestamp = 1_718_500_000L, matchType = "leagueMatch",
            clubs = linkedMapOf(
                "club" to ClubMatchEntry(details = ClubDetails("FC", "club"), score = "1", result = "1"),
                "opp" to ClubMatchEntry(details = ClubDetails("Opp", "opp"), score = "0", result = "0"),
            ),
            players = mapOf("club" to linkedMapOf("p1" to PlayerEntry(
                playerName = "P", position = "14", rating = "7.0", goals = "0",
                assists = "0", shots = "1", manOfTheMatch = "0", passesMade = "10",
                passAttempts = "12", tacklesMade = "2", tackleAttempts = "3",
                redCards = "0", secondsPlayed = "5400",
            ))),
        )
        val fm = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interp = MatchInterpreter().interpret(fm, ClubId("club"))
        val stories = MatchStoryExtractor().extract(interp)
        return CanonicalMatch.current(fm, interp, stories, Instant.parse("2026-07-30T10:00:00Z"))
    }

    private class RecordingRepository : CanonicalMatchRepository {
        val store = linkedMapOf<MatchId, CanonicalMatch>()
        val saved = mutableListOf<String>()
        var failOnSave = false
        var failOnRecentRead = false
        var recentReads = 0

        override fun save(match: CanonicalMatch) {
            if (failOnSave) throw RuntimeException("simulated failure")
            saved += match.matchId.value
            store[match.matchId] = match
        }

        override fun findById(clubId: ClubId, matchId: MatchId) =
            store[matchId]?.takeIf { it.interpretation.perspectiveClubId == clubId }
        override fun findMatchIds(clubId: ClubId) =
            store.values.filter { it.interpretation.perspectiveClubId == clubId }.mapTo(linkedSetOf()) { it.matchId }
        override fun findAll(clubId: ClubId) =
            store.values.filter { it.interpretation.perspectiveClubId == clubId }
        override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> {
            recentReads++
            if (failOnRecentRead) throw RuntimeException("secondary recent read should not happen")
            return findAll(clubId).take(limit)
        }
        override fun metadata(clubId: ClubId) = CanonicalRepositoryMetadata(
            findAll(clubId).size, null, null, null, emptySet(), emptySet(),
        )
    }

    private companion object {
        val CLUB_ID = ClubId("club")
    }
}
