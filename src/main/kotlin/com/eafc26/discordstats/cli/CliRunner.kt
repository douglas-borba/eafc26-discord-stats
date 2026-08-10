package com.eafc26.discordstats.cli

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.CanonicalBackfillResult
import com.eafc26.discordstats.service.CanonicalBackfillService
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.PostgresSyncService
import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/**
 * CLI commands for EA FC Stats.
 *
 * The notify-latest command uses [MatchAcquisitionService] with
 * [AcquisitionTrigger.CLI] to participate in the unified acquisition pipeline.
 */
@Component
class CliRunner(
    @Qualifier("production") private val client: EaClubsGateway,
    private val props: AppProperties,
    private val acquisitionService: MatchAcquisitionService,
    private val canonicalBackfillService: CanonicalBackfillService,
    private val postgresSyncService: PostgresSyncService?,
    private val editorialBackfillService: com.eafc26.discordstats.presentation.editorial.MatchEditorialBackfillService?,
    private val out: PrintStream = PrintStream(System.out, true, StandardCharsets.UTF_8),
    private val exit: (Int) -> Unit = { code -> exitProcess(code) },
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = args.nonOptionArgs.firstOrNull() ?: return

        when (command) {
            CMD_SEARCH             -> runSearchClub()
            CMD_MATCHES            -> runLatestMatches(args)
            CMD_NOTIFY_LATEST      -> runNotifyLatest(args)
            CMD_BACKFILL           -> runCanonicalBackfill(args)
            CMD_BACKFILL_PG        -> runPostgresSync()
            CMD_SYNC_PG            -> runPostgresSync()
            CMD_BACKFILL_EDITORIAL -> runEditorialBackfill(args)
            else -> {
                out.println("Unknown command: '$command'")
                out.println("Available commands: $CMD_SEARCH, $CMD_MATCHES, $CMD_NOTIFY_LATEST, $CMD_BACKFILL, $CMD_SYNC_PG, $CMD_BACKFILL_EDITORIAL")
                exit(1)
            }
        }
    }

    private fun runCanonicalBackfill(args: ApplicationArguments) {
        val clubId = resolveClubId(args) ?: return

        out.println("Backfilling canonical ${props.ea.matchType} matches for club-id=${clubId.value} ...")
        when (val result = canonicalBackfillService.backfill(clubId)) {
            is CanonicalBackfillResult.Completed -> {
                out.println("Requested: ${result.requested}")
                out.println("Returned: ${result.returned}")
                out.println("Processed: ${result.processed}")
                out.println("Created: ${result.created}")
                out.println("Updated: ${result.updated}")
                out.println("Ignored: ${result.ignored}")
                out.println("Failures: ${result.failures.size}")
                result.failures.forEach { failure ->
                    out.println("  ${failure.matchId}: ${failure.message}")
                }
                out.println("Canonical matches: ${result.before} -> ${result.after}")
                exit(if (result.failures.isEmpty()) 0 else 1)
            }
            is CanonicalBackfillResult.Unavailable -> {
                out.println("ERROR: EA API unavailable (HTTP ${result.statusCode}): ${result.message}")
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

    private fun runLatestMatches(args: ApplicationArguments) {
        val clubId = resolveClubId(args) ?: return

        out.println("Fetching latest ${props.ea.matchType} matches for club-id=${clubId.value} ...")

        when (val result = client.getLatestMatches(clubId.value)) {
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
                out.println("No matches found for club-id=${clubId.value} (matchType=${props.ea.matchType}).")
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

    /**
     * Notify-latest command: checks for and publishes the latest match.
     *
     * Uses [MatchAcquisitionService.acquire] with [AcquisitionTrigger.CLI].
     * Maps [AcquisitionResult] to CLI-friendly output messages.
     */
    private fun runNotifyLatest(args: ApplicationArguments) {
        val clubId = resolveClubId(args) ?: return

        out.println("Fetching latest match for club-id=${clubId.value} ...")

        when (val result = acquisitionService.acquire(clubId, AcquisitionTrigger.CLI)) {
            is AcquisitionResult.Processed -> handleProcessedResult(result, clubId.value)
            is AcquisitionResult.ForceResent -> {
                // CLI doesn't use force-resend, but handle gracefully
                out.println("SUCCESS: Match force-resent to Discord. (${result.match.summary})")
                exit(0)
            }
            AcquisitionResult.NoMatches -> {
                out.println("No matches found for club-id=${clubId.value}.")
                exit(0)
            }
            is AcquisitionResult.EaUnavailable -> {
                out.println("ERROR: EA API unavailable. The endpoint may be down. Try again later.")
                exit(1)
            }
            AcquisitionResult.WebhookNotConfigured -> {
                out.println("ERROR: Discord webhook not configured.")
                exit(1)
            }
            AcquisitionResult.Busy -> {
                out.println("ERROR: Another notification is already in progress.")
                exit(1)
            }
        }
    }

    /**
     * Maps [AcquisitionResult.Processed] to CLI output.
     *
     * Output format:
     * - Published → SUCCESS
     * - Published with persistence error → SUCCESS with warning
     * - Already published → INFO
     * - Failed → ERROR
     */
    private fun handleProcessedResult(result: AcquisitionResult.Processed, clubId: String) {
        when {
            result.hasPublished() -> {
                val summary = result.latestSummary()
                val lastPublished = result.published.lastOrNull()
                if (lastPublished?.persistedSuccessfully == false) {
                    out.println("SUCCESS: Match notification sent to Discord, but local history could not be saved. ($summary)")
                } else {
                    out.println("SUCCESS: Match notification sent to Discord. ($summary)")
                }
                exit(0)
            }
            result.allSkipped() -> {
                val summary = result.latestSummary()
                out.println("INFO: Match already published, skipped. ($summary)")
                exit(0)
            }
            result.failed.isNotEmpty() -> {
                out.println("ERROR: Discord delivery failed.")
                exit(1)
            }
            result.baselineEstablished -> {
                // Baseline established is not typical for CLI, but handle it
                out.println("INFO: Baseline established. No new matches to publish.")
                exit(0)
            }
            else -> {
                // Empty processed result (no matches to publish after filtering)
                out.println("No matches found for club-id=$clubId.")
                exit(0)
            }
        }
    }

    private fun runEditorialBackfill(args: ApplicationArguments) {
        if (editorialBackfillService == null) {
            out.println("ERROR: Editorial backfill requires PostgreSQL mirror. Set EAFC_POSTGRES_MIRROR_ENABLED=true")
            exit(1)
            return
        }
        val dryRun = args.containsOption("dry-run")
        val clubId = resolveClubId(args) ?: return

        if (dryRun) {
            out.println("DRY RUN: Editorial backfill for club-id=${clubId.value}")
        } else {
            out.println("Backfilling editorial presentations for club-id=${clubId.value} ...")
        }

        val result = editorialBackfillService.backfillForClub(clubId, dryRun)

        out.println("Total canonical matches: ${result.total}")
        out.println("Processed: ${result.processed}")
        out.println("Skipped (already exists): ${result.skipped}")
        out.println("Errors: ${result.errors}")

        if (dryRun) {
            out.println("")
            out.println("DRY RUN: No changes were made")
            out.println("Run without --dry-run to actually persist presentations")
        }

        exit(if (result.errors == 0) 0 else 1)
    }

    private fun runPostgresSync() {
        if (postgresSyncService == null) {
            out.println("ERROR: PostgreSQL mirror is not enabled. Set EAFC_POSTGRES_MIRROR_ENABLED=true")
            exit(1)
            return
        }
        out.println("Syncing local JSON canonical matches to PostgreSQL...")
        val result = postgresSyncService.sync()
        out.println("Found: ${result.found}")
        out.println("Synced: ${result.synced}")
        out.println("Failures: ${result.failures.size}")
        result.failures.forEach { failure ->
            out.println("  ${failure.matchId}: ${failure.message}")
        }
        out.println("Local: ${result.localCount}  Remote: ${result.remoteCount ?: "unavailable"}")
        out.println("Duration: ${result.durationMs}ms")
        exit(if (result.failures.isEmpty()) 0 else 1)
    }

    /**
     * --club-id is the multi-club contract. Omitting it is intentionally the
     * legacy adapter to the configured default club, never a selection of all clubs.
     */
    private fun resolveClubId(args: ApplicationArguments): ClubId? {
        val configured = args.getOptionValues("club-id")?.singleOrNull()?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: props.ea.clubId.trim().takeIf(String::isNotEmpty)
        if (configured == null) {
            out.println("ERROR: specify --club-id or configure app.ea.club-id for the legacy default adapter")
            exit(1)
            return null
        }
        return ClubId(configured)
    }

    companion object {
        const val CMD_SEARCH        = "search-club"
        const val CMD_MATCHES       = "latest-matches"
        const val CMD_NOTIFY_LATEST = "notify-latest"
        const val CMD_BACKFILL      = "backfill-canonical-matches"
        const val CMD_SYNC_PG       = "sync-postgres"
        const val CMD_BACKFILL_PG   = "backfill-postgres"
        const val CMD_BACKFILL_EDITORIAL = "backfill-editorial"
    }
}
