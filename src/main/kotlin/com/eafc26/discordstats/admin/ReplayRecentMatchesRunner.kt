package com.eafc26.discordstats.admin

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Administrative tool to replay (re-publish) recent matches directly to Discord.
 * 
 * This tool bypasses all deduplication and PublishedMatchStore checks,
 * directly sending matches to Discord using the standard embed generation pipeline.
 * 
 * Usage:
 * ```
 * ./gradlew bootRun --args="--replay.matches=10"
 * ```
 * 
 * Or with system property:
 * ```
 * ./gradlew bootRun -Dreplay.matches=10
 * ```
 * 
 * This is intended for one-time recovery of lost publication history.
 */
@Component
@ConditionalOnProperty(name = ["replay.matches"])
class ReplayRecentMatchesRunner(
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val discordRenderer: DiscordRenderer,
    private val webhookClient: DiscordWebhookClient,
    private val llmEditorialService: LlmEditorialService,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        val limit = System.getProperty("replay.matches")?.toIntOrNull() ?: 10

        log.info("=====================================")
        log.info("REPLAY RECENT MATCHES - ADMIN TOOL")
        log.info("Limit: {} matches", limit)
        log.info("=====================================")

        val matches = canonicalMatchRepository.findAll().take(limit)

        if (matches.isEmpty()) {
            log.warn("No matches found in canonical repository")
            log.info("Replay aborted - nothing to send")
            System.exit(0)
        }

        log.info("Found {} matches to replay", matches.size)
        log.info("-------------------------------------")

        var sentCount = 0
        var failedCount = 0
        val failures = mutableListOf<String>()

        matches.forEachIndexed { index, canonical ->
            val matchId = canonical.matchId.value
            val progress = "[${index + 1}/${matches.size}]"

            try {
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

                // Small delay to avoid rate limiting
                Thread.sleep(1000)

            } catch (ex: Exception) {
                log.error("{} ❌ Failed to send match {}: {}", progress, matchId, ex.message)
                failedCount++
                failures.add("$matchId: ${ex.message}")
            }
        }

        log.info("=====================================")
        log.info("REPLAY COMPLETED")
        log.info("Total: {} matches", matches.size)
        log.info("Sent: {} ✅", sentCount)
        log.info("Failed: {} ❌", failedCount)

        if (failures.isNotEmpty()) {
            log.error("Failures:")
            failures.forEach { log.error("  - {}", it) }
        }

        log.info("=====================================")

        // Exit to prevent normal application startup
        System.exit(if (failedCount > 0) 1 else 0)
    }
}





