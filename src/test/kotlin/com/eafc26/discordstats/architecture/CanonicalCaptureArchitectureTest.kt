package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CanonicalCaptureArchitectureTest {

    @Test
    fun `shared canonical factory has no storage delivery or presentation dependency`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/CanonicalMatchFactory.kt"
        ).readText()

        assertThat(source)
            .contains("EaMatchMapper", "MatchInterpreter", "MatchStoryExtractor", "CanonicalMatch.current")
            .doesNotContain(
                "CanonicalMatchRepository",
                "PublishedMatchStore",
                "DiscordRenderer",
                "DiscordWebhookClient",
                "MatchSummaryBuilder",
            )
    }

    @Test
    fun `backfill has no renderer Discord or publication store dependency`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/CanonicalBackfillService.kt"
        ).readText()

        assertThat(source)
            .contains("CanonicalMatchFactory", "CanonicalMatchRepository", "EaClubsGateway")
            .doesNotContain(
                "PublishedMatchStore",
                "DiscordRenderer",
                "DiscordWebhookClient",
                "MatchSummaryBuilder",
                "LatestMatchHolder",
            )
    }

    @Test
    fun `rendering helpers do not create or persist canonical matches`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/MatchAcquisitionService.kt"
        ).readText()
        val renderingHelpers = source.substringAfter("private fun buildDashboardPresentation")

        assertThat(renderingHelpers)
            .doesNotContain("canonicalMatchFactory.create", "canonicalMatchRepository.save")
    }
}
