package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Executable guardrails for the post-migration production architecture.
 */
class CanonicalProductionArchitectureTest {
    private val productionRoot = Path.of("src/main/kotlin/com/eafc26/discordstats")

    @Test
    fun `production contains no legacy football pipeline`() {
        val production = productionSources()
        val forbiddenNames = setOf(
            "LegacyMatchSummaryBuilder.kt",
            "DiscordEmbedBuilder.kt",
            "HistoryEmbedBuilder.kt",
            "MatchOutcomeResolver.kt",
            "CraqueSelector.kt",
            "BagrePerformanceEvaluator.kt",
            "XerifeSelector.kt",
            "PassePrecisaoSelector.kt",
            "CorreioExtraviadoSelector.kt",
            "RedCardEvaluator.kt",
            "OffensiveNarrativeEvaluator.kt",
            "GoalkeeperEvaluator.kt",
            "PerigoConstanteSelector.kt",
            "PlayerStatisticsEligibility.kt",
            "MatchSummaryDecisionAdapter.kt",
            "MatchSummaryDecisionProjection.kt",
        )

        assertThat(production.map { it.fileName.toString() })
            .doesNotContainAnyElementsOf(forbiddenNames)
    }

    @Test
    fun `football decisions cannot depend on EA DTOs or presentation frameworks`() {
        val guardedDirectories = listOf(
            productionRoot.resolve("domain"),
            productionRoot.resolve("application"),
            productionRoot.resolve("canonical"),
        )
        val forbiddenImports = listOf(
            "com.eafc26.discordstats.ea.model",
            "com.eafc26.discordstats.discord",
            "com.eafc26.discordstats.presentation",
            "org.springframework",
            "com.fasterxml.jackson",
        )

        guardedDirectories.flatMap(::kotlinSources).forEach { source ->
            val content = Files.readString(source)
            forbiddenImports.forEach { forbidden ->
                assertThat(content)
                    .describedAs("$source must not import $forbidden")
                    .doesNotContain("import $forbidden")
            }
        }
    }

    @Test
    fun `renderers consume canonical models and contain no legacy decision dependencies`() {
        val renderers = listOf(
            productionRoot.resolve("presentation/MatchSummaryBuilder.kt"),
            productionRoot.resolve("discord/DiscordRenderer.kt"),
        )
        val requiredModels = listOf("FootballMatch", "MatchInterpretation", "MatchStories")
        val forbidden = listOf(
            "MatchResponse",
            "PlayerEntry",
            "Selector",
            "Evaluator",
            "PlayerStatisticsEligibility",
            "MatchOutcomeResolver",
        )

        renderers.forEach { renderer ->
            val content = Files.readString(renderer)
            requiredModels.forEach { assertThat(content).contains(it) }
            forbidden.forEach {
                assertThat(content)
                    .describedAs("$renderer must not contain a football decision dependency")
                    .doesNotContain(it)
            }
        }
    }

    @Test
    fun `only the ACL translates EA match DTOs into the normalized domain`() {
        val decisionAndRenderingRoots = listOf(
            productionRoot.resolve("domain"),
            productionRoot.resolve("application"),
            productionRoot.resolve("presentation"),
            productionRoot.resolve("discord"),
        )

        decisionAndRenderingRoots.flatMap(::kotlinSources).forEach { source ->
            assertThat(Files.readString(source))
                .describedAs("$source must not consume MatchResponse")
                .doesNotContain("MatchResponse")
        }
        assertThat(Files.readString(productionRoot.resolve("ea/mapping/EaMatchMapper.kt")))
            .contains("MatchResponse")
            .contains("FootballMatch")
    }

    @Test
    fun `history reads canonical repository without EA or football engine dependencies`() {
        val source = Files.readString(productionRoot.resolve("service/MatchHistoryService.kt"))

        assertThat(source).contains("CanonicalMatchRepository")
        listOf(
            "EaClubsGateway",
            "MatchResponse",
            "EaMatchMapper",
            "MatchInterpreter",
            "MatchStoryExtractor",
        ).forEach {
            assertThat(source)
                .describedAs("History must not regenerate canonical matches")
                .doesNotContain(it)
        }
    }

    private fun productionSources(): List<Path> = kotlinSources(productionRoot)

    private fun kotlinSources(root: Path): List<Path> {
        if (!Files.exists(root)) return emptyList()
        return Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }
    }
}
