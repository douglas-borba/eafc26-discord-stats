package com.eafc26.discordstats.cli

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.DefaultApplicationArguments
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CliRunnerTest {

    private lateinit var client: EaClubsGateway
    private lateinit var discord: DiscordWebhookClient
    private lateinit var output: ByteArrayOutputStream
    private lateinit var out: PrintStream
    private val exitCodes = mutableListOf<Int>()

    @BeforeEach
    fun setUp() {
        client = mock()
        discord = mock()
        output = ByteArrayOutputStream()
        out = PrintStream(output)
        exitCodes.clear()
    }

    private fun runner(clubName: String = "Test FC", clubId: String = "12345"): CliRunner {
        val props = AppProperties(
            ea = EaProperties(clubName = clubName, clubId = clubId)
        )
        return CliRunner(client, props, discord, out, exit = { exitCodes.add(it) })
    }

    private fun printed(): String = output.toString(Charsets.UTF_8)

    private fun args(vararg args: String) = DefaultApplicationArguments(*args)

    //   No command - passthrough

    @Test
    fun `no args - run returns without calling the client`() {
        runner().run(args())
        assertThat(printed()).isEmpty()
        assertThat(exitCodes).isEmpty()
    }

    //   search-club

    @Test
    fun `search-club success - prints each club id and name`() {
        whenever(client.searchClubs("Test FC")).thenReturn(
            EaApiResult.Success(
                listOf(
                    ClubSearchResult(clubId = "12345", clubName = "Test FC"),
                    ClubSearchResult(clubId = "99999", clubName = "Test FC Reserves"),
                )
            )
        )

        runner().run(args("search-club"))

        val text = printed()
        assertThat(text).contains("Found 2 club(s)")
        assertThat(text).contains("club-id=12345")
        assertThat(text).contains("club-id=99999")
        assertThat(text).contains("\"Test FC\"")
        assertThat(text).contains("\"Test FC Reserves\"")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `search-club success - prints correct output for real club with non-ASCII name`() {
        whenever(client.searchClubs("Associação BF")).thenReturn(
            EaApiResult.Success(
                listOf(ClubSearchResult(clubId = "1104972", clubName = "Associação BF"))
            )
        )

        runner(clubName = "Associação BF").run(args("search-club"))

        val text = printed()
        assertThat(text).contains("Found 1 club(s)")
        assertThat(text).contains("""club-id=1104972  name="Associação BF"""")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `search-club NoMatches - prints not-found message`() {
        whenever(client.searchClubs("Test FC")).thenReturn(EaApiResult.NoMatches)

        runner().run(args("search-club"))

        assertThat(printed()).contains("No clubs found matching")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `search-club Unavailable - prints HTTP status and retry hint`() {
        whenever(client.searchClubs("Test FC"))
            .thenReturn(EaApiResult.Unavailable(403, "Forbidden"))

        runner().run(args("search-club"))

        val text = printed()
        assertThat(text).contains("EA API unavailable")
        assertThat(text).contains("403")
        assertThat(text).contains("Try again later")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `search-club UnexpectedPayload - prints schema-change hint`() {
        whenever(client.searchClubs("Test FC"))
            .thenReturn(EaApiResult.UnexpectedPayload(RuntimeException("bad json")))

        runner().run(args("search-club"))

        val text = printed()
        assertThat(text).contains("unexpected response")
        assertThat(text).contains("schema may have changed")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `search-club with blank club-name - prints config error`() {
        runner(clubName = "").run(args("search-club"))

        assertThat(printed()).contains("app.ea.club-name is not set")
        assertThat(exitCodes).containsExactly(1)
    }

    //   latest-matches

    @Test
    fun `latest-matches success - prints matchId, timestamp, clubs, and player count`() {
        whenever(client.getLatestMatches("12345")).thenReturn(
            EaApiResult.Success(
                listOf(
                    MatchResponse(
                        matchId = "345758684140013",
                        timestamp = 1718500000L,
                        matchType = "leagueMatch",
                        clubs = mapOf(
                            "12345" to ClubMatchEntry(name = "Test FC", score = "3", goalsAgainst = "1"),
                            "99999" to ClubMatchEntry(name = "Opponent", score = "1", goalsAgainst = "3"),
                        ),
                        players = mapOf(
                            "12345" to mapOf(
                                "p1" to PlayerEntry(playerName = "Alpha"),
                                "p2" to PlayerEntry(playerName = "Beta"),
                            ),
                            "99999" to mapOf(
                                "p3" to PlayerEntry(playerName = "Gamma"),
                            )
                        )
                    )
                )
            )
        )

        runner().run(args("latest-matches"))

        val text = printed()
        assertThat(text).contains("Found 1 match(es)")
        assertThat(text).contains("matchId=345758684140013")
        assertThat(text).contains("Test FC (3)")
        assertThat(text).contains("Opponent (1)")
        assertThat(text).contains("players=3")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `latest-matches NoMatches - prints not-found message`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.NoMatches)

        runner().run(args("latest-matches"))

        assertThat(printed()).contains("No matches found")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `latest-matches Unavailable - prints HTTP status`() {
        whenever(client.getLatestMatches("12345"))
            .thenReturn(EaApiResult.Unavailable(503, "Service Unavailable"))

        runner().run(args("latest-matches"))

        val text = printed()
        assertThat(text).contains("EA API unavailable")
        assertThat(text).contains("503")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `latest-matches with blank club-id - prints config error`() {
        runner(clubId = "").run(args("latest-matches"))

        assertThat(printed()).contains("app.ea.club-id is not set")
        assertThat(exitCodes).containsExactly(1)
    }

    //   Unknown command

    @Test
    fun `unknown command - prints error and lists valid commands`() {
        runner().run(args("invalid-command"))

        val text = printed()
        assertThat(text).contains("Unknown command")
        assertThat(text).contains(CliRunner.CMD_SEARCH)
        assertThat(text).contains(CliRunner.CMD_MATCHES)
        assertThat(text).contains(CliRunner.CMD_NOTIFY_LATEST)
        assertThat(exitCodes).containsExactly(1)
    }

    //   notify-latest

    @Test
    fun `notify-latest success - sends embed and prints success message`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Success(listOf(latestMatch())))

        runner().run(args("notify-latest"))

        verify(discord).send(any())
        val text = printed()
        assertThat(text).contains("SUCCESS")
        assertThat(text).contains("match-99")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `notify-latest picks most recent match when multiple returned`() {
        val matches = listOf(latestMatch("older", ts = 1000L), latestMatch("newer", ts = 9000L))
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Success(matches))

        runner().run(args("notify-latest"))

        verify(discord).send(any())
        val text = printed()
        assertThat(text).contains("newer")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `notify-latest does not modify the store`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Success(listOf(latestMatch())))

        runner().run(args("notify-latest"))

        // No PublishedMatchStore is involved in CliRunner - this is enforced by design
        // (store is not a dependency of CliRunner). Verify discord was called but no
        // store interaction can happen.
        verify(discord).send(any())
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `notify-latest NoMatches - prints message and exits 0`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.NoMatches)

        runner().run(args("notify-latest"))

        verify(discord, never()).send(any())
        assertThat(printed()).contains("No matches found")
        assertThat(exitCodes).containsExactly(0)
    }

    @Test
    fun `notify-latest EA unavailable - prints error and exits 1`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Unavailable(503, "down"))

        runner().run(args("notify-latest"))

        verify(discord, never()).send(any())
        val text = printed()
        assertThat(text).contains("EA API unavailable")
        assertThat(text).contains("503")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `notify-latest missing webhook URL - prints config error and exits 1`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Success(listOf(latestMatch())))
        doThrow(IllegalStateException("Discord webhook URL is not configured")).whenever(discord).send(any())

        runner().run(args("notify-latest"))

        val text = printed()
        assertThat(text).contains("ERROR")
        assertThat(text).contains("webhook URL is not configured")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `notify-latest Discord delivery failure - prints error and exits 1`() {
        whenever(client.getLatestMatches("12345")).thenReturn(EaApiResult.Success(listOf(latestMatch())))
        doThrow(DiscordDeliveryException("HTTP 429: Too many requests")).whenever(discord).send(any())

        runner().run(args("notify-latest"))

        val text = printed()
        assertThat(text).contains("ERROR")
        assertThat(text).contains("Discord delivery failed")
        assertThat(exitCodes).containsExactly(1)
    }

    @Test
    fun `notify-latest blank club-id - prints config error and exits 1`() {
        runner(clubId = "").run(args("notify-latest"))

        verify(discord, never()).send(any())
        assertThat(printed()).contains("app.ea.club-id is not set")
        assertThat(exitCodes).containsExactly(1)
    }

    // -- Helpers --

    private fun latestMatch(id: String = "match-99", ts: Long = 5000L) = MatchResponse(
        matchId = id,
        timestamp = ts,
        clubs = mapOf(
            "12345" to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = "2", result = "1"),
            "99999" to ClubMatchEntry(details = ClubDetails(name = "Opp"),     score = "0", result = "0"),
        ),
        players = emptyMap(),
    )
}
