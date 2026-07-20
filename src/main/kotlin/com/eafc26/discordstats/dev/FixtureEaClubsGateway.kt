package com.eafc26.discordstats.dev

import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Development gateway that loads match data from fixture files.
 *
 * This gateway implements [EaClubsGateway] and returns data from JSON fixtures,
 * allowing the entire acquisition pipeline to be exercised without contacting
 * the EA API.
 *
 * Fixture files are loaded from the classpath:
 * - `fixtures/dev/latest-matches.json` - Array of [MatchResponse]
 *
 * The DTOs returned are identical to those produced by the real EA gateway,
 * so [MatchAcquisitionService] cannot distinguish between fixture and real data.
 *
 * This component is always instantiated but only used when Development Mode
 * is enabled through the dashboard settings.
 *
 * ## Simulating Scenarios
 *
 * By modifying the fixture JSON files, developers can simulate:
 * - No matches (empty array)
 * - One unpublished match
 * - Multiple unpublished matches
 * - Already published match (combine with pre-populated PublishedMatchStore)
 * - Baseline creation (first run with empty store)
 */
@Component
@Qualifier("fixture")
class FixtureEaClubsGateway(
    private val objectMapper: ObjectMapper,
) : EaClubsGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DEFAULT_MATCHES_FIXTURE = "fixtures/dev/latest-matches.json"
    }

    @Volatile
    var matchesFixturePath: String = DEFAULT_MATCHES_FIXTURE

    @Volatile
    var simulateUnavailable: Boolean = false

    override fun searchClubs(clubName: String): EaApiResult<List<ClubSearchResult>> {
        // Club search is not used by the acquisition pipeline
        return EaApiResult.Success(emptyList())
    }

    override fun getLatestMatches(clubId: String): EaApiResult<List<MatchResponse>> {
        log.debug("FixtureGateway: getLatestMatches({})", clubId)

        if (simulateUnavailable) {
            log.debug("FixtureGateway: Simulating EA unavailable")
            return EaApiResult.Unavailable(503, "Simulated EA unavailable")
        }

        return try {
            val resource = ClassPathResource(matchesFixturePath)
            if (!resource.exists()) {
                log.debug("FixtureGateway: Matches fixture not found at {}", matchesFixturePath)
                return EaApiResult.NoMatches
            }
            val matches: List<MatchResponse> = objectMapper.readValue(resource.inputStream)
            log.debug("FixtureGateway: Loaded {} matches from fixture", matches.size)
            if (matches.isEmpty()) {
                EaApiResult.NoMatches
            } else {
                EaApiResult.Success(matches)
            }
        } catch (ex: Exception) {
            log.warn("FixtureGateway: Failed to load matches fixture", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }

    /**
     * Resets the gateway to default settings.
     */
    fun reset() {
        matchesFixturePath = DEFAULT_MATCHES_FIXTURE
        simulateUnavailable = false
        log.debug("FixtureGateway: Reset to defaults")
    }
}

