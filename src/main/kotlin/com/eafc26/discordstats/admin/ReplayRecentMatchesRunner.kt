package com.eafc26.discordstats.admin

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.llm.LlmEditorialService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Administrative tool to replay (re-publish) recent matches directly to Discord.
 * 
 * This tool bypasses all deduplication and PublishedMatchStore checks,
 * directly sending matches to Discord using the standard embed generation pipeline.
 * 
 * Usage:
 * ```
 * # Replay 10 most recent matches
 * ./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10"
 * 
 * # Dry-run mode (inspect without sending)
 * ./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.dryRun=true"
 * 
 * # Replay a specific match by ID
 * ./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matchId=944922107030449"
 * 
 * # Exclude specific match IDs (filters before applying limit)
 * ./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=944922107030449"
 * 
 * # Exclude multiple matches (comma-separated)
 * ./gradlew bootRun --args="--app.replay.enabled=true --app.replay.matches=10 --app.replay.excludeMatchIds=id1,id2,id3"
 * ```
 * 
 * This is intended for one-time recovery of lost publication history.
 */
@Component
@ConditionalOnProperty(name = ["app.replay.enabled"], havingValue = "true")
class ReplayRecentMatchesRunner(
    private val properties: AppProperties,
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val discordRenderer: DiscordRenderer,
    private val webhookClient: DiscordWebhookClient,
    private val llmEditorialService: LlmEditorialService,
    private val destinationResolver: DiscordDestinationResolver,
    private val defaultClubProvider: DefaultClubProvider,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val zoneId = ZoneId.of("America/Sao_Paulo")

    override fun run(vararg args: String) {
        val replayConfig = properties.replay
        val specificMatchId = replayConfig.matchId?.trim()
        val limit = replayConfig.matches
        val dryRun = replayConfig.dryRun
        val clubId = defaultClubProvider.get().clubId
        
        // Parse excluded matchIds
        val excludedIds = replayConfig.excludeMatchIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        log.info("=====================================")
        log.info("Club ID: {}", clubId.value)
        if (dryRun) {
            log.info("REPLAY DRY RUN")
        } else {
            log.info("REPLAY RECENT MATCHES - ADMIN TOOL")
        }
        
        if (specificMatchId != null) {
            log.info("Mode: SINGLE MATCH")
            log.info("Match ID: {}", specificMatchId)
        } else {
            log.info("Mode: RECENT MATCHES")
            log.info("Limit: {} matches", limit ?: 10)
        }
        
        if (excludedIds.isNotEmpty()) {
            log.info("Excluding {} matchId(s): {}", excludedIds.size, excludedIds.joinToString(", "))
        }
        
        if (dryRun) {
            log.info("DRY RUN: No messages will be sent")
        }
        log.info("=====================================")
        if (dryRun) {
            log.info("")
        }

        val matches = selectReplayMatches(
            repository = canonicalMatchRepository,
            clubId = clubId,
            specificMatchId = specificMatchId,
            excludedIds = excludedIds,
            limit = limit ?: 10,
        )

        if (matches.isEmpty()) {
            if (specificMatchId != null) {
                log.warn("Match {} not found in canonical repository", specificMatchId)
            } else {
                log.warn("No matches found in canonical repository")
            }
            log.info("Replay aborted - nothing to process")
            System.exit(0)
        }

        if (!dryRun) {
            log.info("Found {} match(es) to process", matches.size)
            log.info("-------------------------------------")
        }

        var sentCount = 0
        var failedCount = 0
        val failures = mutableListOf<String>()
        val destination = destinationResolver.resolve(clubId)
        if (!dryRun && destination == null) {
            log.info("Replay skipped: clubId={}, destinationConfigured=false", clubId.value)
            System.exit(0)
        }

        matches.forEachIndexed { index, canonical ->
            val matchId = canonical.matchId.value
            val progress = "[${index + 1}/${matches.size}]"

            try {
                if (dryRun) {
                    inspectMatch(canonical, index + 1)
                    sentCount++
                } else {
                    log.info("{} Replaying match: {}", progress, matchId)

                    // Generate narrative (best effort)
                    val narrative = try {
                        llmEditorialService.generateMatchNarrative(canonical)
                    } catch (ex: Exception) {
                        log.warn("{} Failed to generate narrative for match {}: {}", progress, matchId, ex.message)
                        null
                    }

                    // Build Discord payload using standard pipeline
                    val payload = discordRenderer.renderMatch(
                        canonical.footballMatch,
                        canonical.interpretation,
                        canonical.stories,
                        editorialNarrative = narrative,
                    )

                    // Send directly to Discord
                    webhookClient.send(destination!!, payload)

                    log.info("{} ✅ Match {} sent successfully", progress, matchId)
                    sentCount++

                    // Delay to avoid rate limiting (2 seconds for safety)
                    Thread.sleep(2000)
                }

            } catch (ex: Exception) {
                log.error("{} ❌ Failed to process match {}: {}", progress, matchId, ex.message)
                failedCount++
                failures.add("$matchId: ${ex.message}")
            }
        }

        log.info("=====================================")
        if (dryRun) {
            log.info("Total: {} partida{}", matches.size, if (matches.size == 1) "" else "s")
            log.info("Nenhuma mensagem enviada.")
        } else {
            log.info("REPLAY COMPLETED")
            log.info("Total: {} match(es)", matches.size)
            log.info("Sent: {} ✅", sentCount)
            log.info("Failed: {} ❌", failedCount)
        }

        if (failures.isNotEmpty()) {
            log.error("Failures:")
            failures.forEach { log.error("  - {}", it) }
        }

        log.info("=====================================")

        // Exit to prevent normal application startup
        System.exit(if (failedCount > 0) 1 else 0)
    }

    private fun inspectMatch(
        canonical: com.eafc26.discordstats.canonical.CanonicalMatch,
        index: Int,
    ) {
        val matchId = canonical.matchId.value
        val footballMatch = canonical.footballMatch
        val interpretation = canonical.interpretation

        // Extract match info
        val date = footballMatch.playedAt.atZone(zoneId).format(dateFormatter)
        
        val ourClub = footballMatch.participants.first { it.club.id == interpretation.perspectiveClubId }
        val opponent = footballMatch.participants.first { it.club.id == interpretation.result.opponentClub }
        
        val ourName = ourClub.club.name?.value ?: "Nós"
        val ourScore = interpretation.result.ourScore.goals
        val oppName = opponent.club.name?.value ?: "Adversário"
        val oppScore = interpretation.result.opponentScore.goals

        // Simplified dry-run output
        log.info("[{}] {}", index, matchId)
        log.info("{} {} x {} {}", ourName, ourScore, oppScore, oppName)
        log.info("{}", date)
        log.info("")
    }
}

internal fun selectReplayMatches(
    repository: CanonicalMatchRepository,
    clubId: ClubId,
    specificMatchId: String?,
    excludedIds: Set<String>,
    limit: Int,
) = if (specificMatchId != null) {
    repository.findById(clubId, MatchId(specificMatchId))?.let(::listOf).orEmpty()
} else {
    repository.findAll(clubId)
        .filter { it.matchId.value !in excludedIds }
        .take(limit)
}
