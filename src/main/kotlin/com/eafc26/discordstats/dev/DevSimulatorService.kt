package com.eafc26.discordstats.dev

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.LatestMatchHolder
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Development simulator service for testing match acquisition (web-only).
 *
 * This service provides methods to:
 * - Trigger the acquisition pipeline using fixture data
 * - Reset the simulator state for repeated testing
 *
 * All operations flow through [MatchAcquisitionService], ensuring the
 * simulator exercises the exact same fetch, processing, and caching code 
 * paths as production.
 *
 * ## Web-Only Behavior
 *
 * The simulator NEVER sends to Discord:
 * - Match cards are generated and cached locally
 * - Cards can be viewed and downloaded from the dashboard
 * - No Discord webhook is called
 * - No production delivery state is modified
 *
 * ## Usage
 *
 * 1. Enable Development Mode in the dashboard settings
 * 2. Use the dashboard's "Simulador" section or call the REST endpoints
 * 3. Modify fixture files to simulate different scenarios
 * 4. Use "Reset" to clear state between tests
 *
 * ## Important
 *
 * This service is always instantiated but only operates when Development Mode
 * is enabled through the application settings. When disabled, all operations
 * will throw [DevelopmentModeDisabledException].
 */
@Service
class DevSimulatorService(
    private val acquisitionService: MatchAcquisitionService,
    @Qualifier("fixture") private val fixtureGateway: FixtureEaClubsGateway,
    private val latestMatchHolder: LatestMatchHolder,
    private val webhookConfigService: WebhookConfigService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns whether development mode is currently enabled.
     */
    fun isEnabled(): Boolean = webhookConfigService.isDevelopmentModeEnabled()

    /**
     * Simulates a match acquisition using fixture data (web-only).
     *
     * The acquisition flows through the pipeline:
     * FETCHING → PROCESSING → CACHING
     *
     * No Discord delivery or production persistence occurs.
     * The generated card is available in the dashboard for viewing/download.
     *
     * @return The result of the acquisition
     * @throws DevelopmentModeDisabledException if development mode is disabled
     */
    fun simulateLatest(): AcquisitionResult {
        requireEnabled()
        log.info("DevSimulator: Starting simulated acquisition (web-only)")
        return acquisitionService.acquire(
            trigger = AcquisitionTrigger.DEV_SIMULATOR,
            gateway = fixtureGateway,
        )
    }

    /**
     * Resets the simulator state.
     *
     * This clears:
     * - Latest match holder cache (presentation becomes null)
     * - Fixture gateway settings
     *
     * Note: Does NOT clear the production published match store since
     * simulations don't affect it anyway.
     *
     * @throws DevelopmentModeDisabledException if development mode is disabled
     */
    fun reset() {
        requireEnabled()
        log.info("DevSimulator: Resetting state")

        // Clear latest match holder
        latestMatchHolder.clear()
        log.debug("DevSimulator: Cleared latest match holder")

        // Reset fixture gateway
        fixtureGateway.reset()
        log.debug("DevSimulator: Reset fixture gateway")

        log.info("DevSimulator: Reset complete")
    }

    private fun requireEnabled() {
        if (!isEnabled()) {
            throw DevelopmentModeDisabledException()
        }
    }
}

/**
 * Exception thrown when attempting simulator operations while development mode is disabled.
 */
class DevelopmentModeDisabledException : RuntimeException("Development mode is disabled")

