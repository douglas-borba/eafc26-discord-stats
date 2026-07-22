package com.eafc26.discordstats.ea

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EaResponseParserTest {

    private lateinit var parser: EaResponseParser

    @BeforeEach
    fun setUp() {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        parser = EaResponseParser(objectMapper)
    }

    // -- parseSearch --

    @Test
    fun `parseSearch returns Success with club from real fixture`() {
        val result = parser.parseSearch(fixture("clubs-search.json"))

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val clubs = (result as EaApiResult.Success).data
        assertThat(clubs).hasSize(1)
        assertThat(clubs[0].clubId).isEqualTo("1104972")
        assertThat(clubs[0].platform).isEqualTo("common-gen5")
        assertThat(clubs[0].currentDivision).isEqualTo("4")
    }

    @Test
    fun `parseSearch resolvedName prefers clubName over clubInfo name`() {
        val result = parser.parseSearch(fixture("clubs-search.json"))
        val club = (result as EaApiResult.Success).data[0]

        // clubName is present in the live payload - it takes priority
        assertThat(club.clubName).isEqualTo("Associação BF")
        assertThat(club.clubInfo?.name).isEqualTo("Associação BF")
        assertThat(club.resolvedName()).isEqualTo("Associação BF")
    }

    @Test
    fun `parseSearch resolvedName falls back to clubInfo name when clubName is absent`() {
        val json = """[{"clubId":"99","clubInfo":{"name":"Fallback FC"}}]"""
        val club = (parser.parseSearch(json) as EaApiResult.Success).data[0]

        assertThat(club.clubName).isNull()
        assertThat(club.resolvedName()).isEqualTo("Fallback FC")
    }

    @Test
    fun `parseSearch parses stat fields as strings`() {
        val club = (parser.parseSearch(fixture("clubs-search.json")) as EaApiResult.Success).data[0]

        assertThat(club.wins).isEqualTo("14")
        assertThat(club.losses).isEqualTo("8")
        assertThat(club.ties).isEqualTo("1")
        assertThat(club.gamesPlayed).isEqualTo("23")
        assertThat(club.goals).isEqualTo("66")
        assertThat(club.goalsAgainst).isEqualTo("58")
        assertThat(club.points).isEqualTo("26")
    }

    @Test
    fun `parseSearch parses clubInfo with integer clubId`() {
        val club = (parser.parseSearch(fixture("clubs-search.json")) as EaApiResult.Success).data[0]

        assertThat(club.clubInfo?.clubId).isEqualTo(1104972L)
        assertThat(club.clubInfo?.regionId).isEqualTo(1396788530L)
        assertThat(club.clubInfo?.teamId).isEqualTo(1629L)
    }

    @Test
    fun `parseSearch returns NoMatches for empty array`() {
        assertThat(parser.parseSearch("[]")).isEqualTo(EaApiResult.NoMatches)
    }

    @Test
    fun `parseSearch returns UnexpectedPayload for invalid JSON`() {
        val result = parser.parseSearch("{not json}")
        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    // -- parseMatches --

    @Test
    fun `parseMatches returns Success with matches from fixture`() {
        val result = parser.parseMatches(fixture("clubs-matches.json"))

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val matches = (result as EaApiResult.Success).data
        assertThat(matches).hasSize(2)
        assertThat(matches[0].matchId).isEqualTo("345758684140013")
        assertThat(matches[0].timestamp).isEqualTo(1718500000L)
        assertThat(matches[0].clubs["12345"]!!.resolvedName()).isEqualTo("Test FC")
    }

    @Test
    fun `parseMatches returns NoMatches for empty array`() {
        assertThat(parser.parseMatches("[]")).isEqualTo(EaApiResult.NoMatches)
    }

    @Test
    fun `parseMatches returns UnexpectedPayload for invalid JSON`() {
        val result = parser.parseMatches("[{\"matchId\": BROKEN")
        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test
    fun `parseMatches parses secondsplayed from fixture`() {
        val matches = (parser.parseMatches(fixture("clubs-matches.json")) as EaApiResult.Success).data
        val players = matches[0].players["12345"]!!

        assertThat(players["player_abc"]!!.secondsPlayed).isEqualTo("5400")
        assertThat(players["player_short"]!!.secondsPlayed).isEqualTo("900")
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("fixtures/$name")
            ?.bufferedReader()
            ?.readText()
            ?: error("fixture not found: $name")

    // -- parseMembersStats --

    @Test
    fun `parseMembersStats returns Success with entries from fixture (root object with object-of-objects)`() {
        val result = parser.parseMembersStats(fixture("members-stats.json"))

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val members = (result as EaApiResult.Success).data
        assertThat(members).hasSize(3)
        assertThat(members.map { it.playerName }).containsExactlyInAnyOrder("dbeng_bass", "Striker99", "GoalieKing")
        assertThat(members.first { it.playerName == "dbeng_bass" }.proName).isEqualTo("R. Nazário")
        assertThat(members.first { it.playerName == "Striker99" }.proName).isEqualTo("Ronaldinho")
    }

    @Test
    fun `parseMembersStats handles root object with array field`() {
        val json = """{"members":[{"playername":"user1","proName":"Pro One"},{"playername":"user2","proName":"Pro Two"}]}"""
        val members = (parser.parseMembersStats(json) as EaApiResult.Success).data
        assertThat(members).hasSize(2)
        assertThat(members[0].playerName).isEqualTo("user1")
        assertThat(members[0].proName).isEqualTo("Pro One")
    }

    @Test
    fun `parseMembersStats handles root object with object-of-objects field`() {
        val json = """{"members":{"id1":{"playername":"user1","proName":"Pro One"},"id2":{"playername":"user2","proName":"Pro Two"}}}"""
        val members = (parser.parseMembersStats(json) as EaApiResult.Success).data
        assertThat(members).hasSize(2)
        assertThat(members.map { it.playerName }).containsExactlyInAnyOrder("user1", "user2")
    }

    @Test
    fun `parseMembersStats returns empty list for root object with empty members object`() {
        val result = parser.parseMembersStats("""{"members":{}}""")
        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        assertThat((result as EaApiResult.Success).data).isEmpty()
    }

    @Test
    fun `parseMembersStats returns empty list for root object with empty members array`() {
        val result = parser.parseMembersStats("""{"members":[]}""")
        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        assertThat((result as EaApiResult.Success).data).isEmpty()
    }

    @Test
    fun `parseMembersStats returns UnexpectedPayload for invalid JSON`() {
        val result = parser.parseMembersStats("{broken")
        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test
    fun `parseMembersStats ignores unknown fields in member entries`() {
        val json = """{"members":{"1":{"playername":"user1","proName":"Pro One","unknownField":"ignored","proPos":"14"}}}"""
        val members = (parser.parseMembersStats(json) as EaApiResult.Success).data
        assertThat(members[0].playerName).isEqualTo("user1")
        assertThat(members[0].proName).isEqualTo("Pro One")
    }

    @Test
    fun `parseMembersStats skips member entries without a playername`() {
        val json = """{"members":{"1":{"proName":"No Name Here"},"2":{"playername":"user2","proName":"Has Name"}}}"""
        val members = (parser.parseMembersStats(json) as EaApiResult.Success).data
        assertThat(members).hasSize(1)
        assertThat(members[0].playerName).isEqualTo("user2")
    }
}
