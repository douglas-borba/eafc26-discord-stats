package com.eafc26.discordstats.admin

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.llm.LlmEditorialService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
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
 * ./gradlew bootRun -Dreplay.matches=10
 * 
 * # Dry-run mode (inspect without sending)
 * ./gradlew bootRun -Dreplay.matches=10 -Dreplay.dryRun=true
 * 
 * # Replay a specific match by ID
 * ./gradlew bootRun -Dreplay.matchId=944922107030449
 * ```
 * 
 * This is intended for one-time recovery of lost publication history.
 */
@Component
class ReplayRecentMatchesRunner(
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val discordRenderer: DiscordRenderer,
    private val webhookClient: DiscordWebhookClient,
    private val llmEditorialService: LlmEditorialService,
    private val webhookConfigService: WebhookConfigService,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val zoneId = ZoneId.of("America/Sao_Paulo")

    override fun run(vararg args: String) {
        val specificMatchId = System.getProperty("replay.matchId")?.trim()
        val limit = System.getProperty("replay.matches")?.toIntOrNull()
        val dryRun = System.getProperty("replay.dryRun")?.toBoolean() ?: false

        // Only run if replay parameters are specified
        if (specificMatchId == null && limit == null) {
            return
        }

        log.info("=====================================")
        log.info("REPLAY RECENT MATCHES - ADMIN TOOL")
        if (specificMatchId != null) {
            log.info("Mode: SINGLE MATCH")
            log.info("Match ID: {}", specificMatchId)
        } else {
            log.info("Mode: RECENT MATCHES")
            log.info("Limit: {} matches", limit ?: 10)
        }
        if (dryRun) {
            log.info("DRY RUN: Enabled (no messages will be sent)")
        }
        log.info("=====================================")

        val matches = if (specificMatchId != null) {
            val match = canonicalMatchRepository.findById(MatchId(specificMatchId))
            if (match != null) listOf(match) else emptyList()
        } else {
            canonicalMatchRepository.findAll().take(limit ?: 10)
        }

        if (matches.isEmpty()) {
            if (specificMatchId != null) {
                log.warn("Match {} not found in canonical repository", specificMatchId)
            } else {
                log.warn("No matches found in canonical repository")
            }
            log.info("Replay aborted - nothing to process")
            System.exit(0)
        }

        log.info("Found {} match(es) to process", matches.size)
        log.info("-------------------------------------")

        var sentCount = 0
        var failedCount = 0
        val failures = mutableListOf<String>()
        val webhookUrl = webhookConfigService.getWebhookUrl()

        matches.forEachIndexed { index, canonical ->
            val matchId = canonical.matchId.value
            val progress = "[${index + 1}/${matches.size}]"

            try {
                if (dryRun) {
                    inspectMatch(canonical, index + 1, matches.size, webhookUrl)
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
                    webhookClient.send(payload)

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
            log.info("DRY RUN COMPLETED")
        } else {
            log.info("REPLAY COMPLETED")
        }
        log.info("Total: {} match(es)", matches.size)
        if (dryRun) {
            log.info("Analyzed: {} ✅", sentCount)
            log.info("Render failures: {} ❌", failedCount)
            log.info("")
            log.info("No messages were sent to Discord.")
        } else {
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
        total: Int,
        webhookUrl: String,
    ) {
        val matchId = canonical.matchId.value
        val footballMatch = canonical.footballMatch
        val interpretation = canonical.interpretation
        val stories = canonical.stories

        // Extract match info
        val date = footballMatch.playedAt.atZone(zoneId).format(dateFormatter)
        val competitionType = footballMatch.competition?.name ?: "Friendly"
        
        val ourClub = footballMatch.participants.first { it.club.id == interpretation.perspectiveClubId }
        val opponent = footballMatch.participants.first { it.club.id == interpretation.result.opponentClub }
        
        val ourName = ourClub.club.name?.value ?: "Nós"
        val ourScore = interpretation.result.ourScore.goals
        val oppName = opponent.club.name?.value ?: "Adversário"
        val oppScore = interpretation.result.opponentScore.goals

        // Extract craque
        val craqueStory = stories.stories
            .filter { it.type == StoryType.AWARD }
            .mapNotNull { it.content as? StoryContent.Award }
            .firstOrNull { it.awardType == AwardType.CRAQUE }
        
        val craqueName = craqueStory?.let { story ->
            val player = ourClub.players.find { it.player.id == story.winnerId }
            player?.player?.preferredDisplayName?.value ?: "—"
        } ?: "—"

        // Extract bagre
        val bagreStory = stories.stories
            .firstOrNull { it.type == StoryType.BAGRE_PERFORMANCE }
        
        val bagreName = (bagreStory?.content as? StoryContent.BagrePerformance)?.let { story ->
            val player = ourClub.players.find { it.player.id == story.playerId }
            player?.player?.preferredDisplayName?.value ?: "—"
        } ?: "—"

        // Generate narrative (best effort)
        val hasNarrative = try {
            val narrative = llmEditorialService.generateMatchNarrative(canonical)
            narrative != null
        } catch (ex: Exception) {
            false
        }

        // Build Discord payload to inspect
        val payload = discordRenderer.renderMatch(
            footballMatch,
            interpretation,
            stories,
            editorialNarrative = null, // Don't include for inspection
        )

        val embedCount = payload.embeds.size
        val embedTitle = payload.embeds.firstOrNull()?.title ?: "—"

        // Print inspection report
        log.info("--------------------------------------------------------")
        log.info("[{}/{}]", index, total)
        log.info("")
        log.info("MatchId:")
        log.info("{}", matchId)
        log.info("")
        log.info("Data:")
        log.info("{}", date)
        log.info("")
        log.info("Tipo:")
        log.info("{}", competitionType)
        log.info("")
        log.info("{} {} x {} {}", ourName, ourScore, oppScore, oppName)
        log.info("")
        log.info("Craque:")
        log.info("{}", craqueName)
        log.info("")
        log.info("Bagre:")
        log.info("{}", bagreName)
        log.info("")
        log.info("Narrativa LLM:")
        log.info("{}", if (hasNarrative) "SIM" else "NÃO")
        log.info("")
        log.info("Título do Embed:")
        log.info("{}", embedTitle)
        log.info("")
        log.info("Embeds:")
        log.info("{}", embedCount)
        log.info("")
        log.info("Imagem:")
        log.info("NÃO") // Feature não implementada ainda
        log.info("")
        log.info("Webhook:")
        log.info("{}", if (webhookUrl.isBlank()) "NÃO CONFIGURADO" else webhookUrl.take(50) + "...")
        log.info("")
        log.info("WOULD SEND")
        log.info("--------------------------------------------------------")
    }
}





