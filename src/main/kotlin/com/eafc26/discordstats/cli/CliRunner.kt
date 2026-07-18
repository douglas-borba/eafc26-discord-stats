package com.eafc26.discordstats.cli

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.service.NotifyLatestService
import com.eafc26.discordstats.service.NotifyResult
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

@Component
class CliRunner(
    private val client: EaClubsGateway,
    private val props: AppProperties,
    private val notifyLatestService: NotifyLatestService,
    private val out: PrintStream = PrintStream(System.out, true, StandardCharsets.UTF_8),
    private val exit: (Int) -> Unit = { code -> exitProcess(code) },
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = args.nonOptionArgs.firstOrNull() ?: return

        when (command) {
            CMD_SEARCH        -> runSearchClub()
            CMD_MATCHES       -> runLatestMatches()
            CMD_NOTIFY_LATEST -> runNotifyLatest()
            else -> {
                out.println("Unknown command: '$command'")
                out.println("Available commands: $CMD_SEARCH, $CMD_MATCHES, $CMD_NOTIFY_LATEST")
                exit(1)
            }
        }
    }

    private fun runSearchClub() {
        val name = props.ea.clubName
        if (name.isBlank()) {
            out.println("ERROR: app.ea.club-name is not set in application.yml")
            exit(1)
        }

        out.println("Searching for clubs matching: \"$name\"")

        when (val result = client.searchClubs(name)) {
            is EaApiResult.Success -> {
                out.println("Found ${result.data.size} club(s):")
                result.data.forEach { club ->
                    out.println("  club-id=${club.clubId}  name=\"${club.resolvedName()}\"")
                }
                exit(0)
            }
            EaApiResult.NoMatches -> {
                out.println("No clubs found matching \"$name\".")
                exit(0)
            }
            is EaApiResult.Unavailable -> {
                out.println("EA API unavailable (HTTP ${result.statusCode}): ${result.message}")
                out.println("The endpoint may be down. Try again later.")
                exit(1)
            }
            is EaApiResult.UnexpectedPayload -> {
                out.println("EA API returned an unexpected response that could not be parsed.")
                out.println("The response schema may have changed: ${result.cause.message}")
                exit(1)
            }
        }
    }

    private fun runLatestMatches() {
        val clubId = props.ea.clubId
        if (clubId.isBlank()) {
            out.println("ERROR: app.ea.club-id is not set in application.yml")
            exit(1)
        }

        out.println("Fetching latest ${props.ea.matchType} matches for club-id=$clubId ...")

        when (val result = client.getLatestMatches(clubId)) {
            is EaApiResult.Success -> {
                out.println("Found ${result.data.size} match(es):")
                result.data.forEach { match ->
                    val ts = Instant.ofEpochSecond(match.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

                    val clubSummary = match.clubs.entries.joinToString(" vs ") { (_, c) ->
                        "${c.resolvedName() ?: "?"} (${c.score ?: "?"})"
                    }
                    val playerCount = match.players.values.sumOf { it.size }

                    out.println("  matchId=${match.matchId}  timestamp=$ts  $clubSummary  players=$playerCount")
                }
                exit(0)
            }
            EaApiResult.NoMatches -> {
                out.println("No matches found for club-id=$clubId (matchType=${props.ea.matchType}).")
                exit(0)
            }
            is EaApiResult.Unavailable -> {
                out.println("EA API unavailable (HTTP ${result.statusCode}): ${result.message}")
                out.println("The endpoint may be down. Try again later.")
                exit(1)
            }
            is EaApiResult.UnexpectedPayload -> {
                out.println("EA API returned an unexpected response that could not be parsed.")
                out.println("The response schema may have changed: ${result.cause.message}")
                exit(1)
            }
        }
    }

    private fun runNotifyLatest() {
        val clubId = props.ea.clubId
        if (clubId.isBlank()) {
            out.println("ERROR: app.ea.club-id is not set in application.yml")
            exit(1)
            return
        }

        out.println("Fetching latest match for club-id=$clubId ...")

        when (val result = notifyLatestService.notifyLatest()) {
            is NotifyResult.Sent -> {
                out.println("SUCCESS: Match notification sent to Discord. (${result.summary})")
                exit(0)
            }
            is NotifyResult.SentPersistenceError -> {
                out.println("SUCCESS: Match notification sent to Discord, but local history could not be saved. (${result.summary})")
                exit(0)
            }
            is NotifyResult.AlreadyPublished -> {
                out.println("INFO: Match already published, skipped. (${result.summary})")
                exit(0)
            }
            is NotifyResult.ForceSent -> {
                out.println("SUCCESS: Match force-resent to Discord. (${result.summary})")
                exit(0)
            }
            NotifyResult.NoMatches -> {
                out.println("No matches found for club-id=$clubId.")
                exit(0)
            }
            NotifyResult.EaUnavailable -> {
                out.println("ERROR: EA API unavailable. The endpoint may be down. Try again later.")
                exit(1)
            }
            NotifyResult.DiscordError -> {
                out.println("ERROR: Discord delivery failed.")
                exit(1)
            }
            NotifyResult.Busy -> {
                out.println("ERROR: Another notification is already in progress.")
                exit(1)
            }
        }
    }

    companion object {
        const val CMD_SEARCH        = "search-club"
        const val CMD_MATCHES       = "latest-matches"
        const val CMD_NOTIFY_LATEST = "notify-latest"
    }
}
