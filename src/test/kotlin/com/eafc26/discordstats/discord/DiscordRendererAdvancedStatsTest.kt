package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordRendererAdvancedStatsTest {

    private val renderer = DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))

    @Test
    fun `renders automatic low performance with factual Discord copy`() {
        val payload = render(match(players = linkedMapOf(
            "high" to player("High", rating = "8.5"),
            "low" to player("Low", rating = "7.3"),
        )))
        val field = payload.embeds.flatMap { it.fields }.single { it.name == "📉 BAIXO RENDIMENTO" }

        assertThat(field.value).contains("Low", "Nota 7,30", "Menor nota entre os jogadores elegíveis.")
    }

    @Test
    fun `renders advanced decisions from RAW decoding through canonical interpretation and summary`() {
        val payload = render(advancedMatch())
        val fields = payload.embeds.flatMap { it.fields }.associateBy { it.name }

        assertThat(fields["🧠 POR TRÁS DA JOGADA"]?.value)
            .contains("Creator", "2 assistências prévias", "9 passes em profundidade")
        assertThat(fields["🪄 NO UM CONTRA UM"]?.value)
            .contains("Dribbler", "8 adversários superados")
            .doesNotContain("drible completo", "dribles completos")
        assertThat(fields["🚧 XERIFE DA PARTIDA"]?.value)
            .contains("Creator", "6 ações defensivas", "2/2 desarmes", "4 interceptações", "Aproveitamento: 100%")

        val sectionNames = payload.embeds.flatMap { it.fields }.map { it.name }
        assertThat(sectionNames.indexOf("🧠 POR TRÁS DA JOGADA"))
            .isLessThan(sectionNames.indexOf("🪄 NO UM CONTRA UM"))
        assertThat(sectionNames.indexOf("🪄 NO UM CONTRA UM"))
            .isLessThan(sectionNames.indexOf("🚧 XERIFE DA PARTIDA"))
    }

    @Test
    fun `omits advanced sections when no canonical decision is eligible`() {
        val payload = render(match(players = mapOf(
            "ordinary" to player("Ordinary", rating = "7.0"),
            "low" to player("Low", rating = "5.0"),
        )))
        val names = payload.embeds.flatMap { it.fields }.map { it.name }

        assertThat(names)
            .doesNotContain("🧠 POR TRÁS DA JOGADA", "🪄 NO UM CONTRA UM")
    }

    @Test
    fun `omits Bagre section without leaving an empty Discord separator when nobody qualifies`() {
        val payload = render(match(players = linkedMapOf(
            "best" to player("Best", rating = "10.0"),
            "excellent" to player(
                "Excellent",
                rating = "9.4",
                tacklesMade = "1",
                tackleAttempts = "9",
            ),
        )))
        val names = payload.embeds.flatMap { it.fields }.map { it.name }

        assertThat(names).doesNotContain("🍍 BAGRE DA PARTIDA")
        names.forEachIndexed { index, name ->
            if (name == "​") {
                assertThat(index).isLessThan(names.lastIndex)
                assertThat(names[index + 1]).isNotEqualTo("​")
            }
        }
    }

    @Test
    fun `renders an interceptions-only Xerife when the domain makes the player eligible`() {
        val payload = render(match(players = linkedMapOf(
            "interceptor" to player("Interceptor", rating = "8.0", aggregate0 = "6:4"),
            "support" to player("Support", rating = "7.0"),
            "low" to player("Low", rating = "5.0"),
        )))
        val xerife = payload.embeds.flatMap { it.fields }.single { it.name == "🚧 XERIFE DA PARTIDA" }

        assertThat(xerife.value)
            .contains("Interceptor", "4 ações defensivas", "0/0 desarmes", "4 interceptações")
            .doesNotContain("Aproveitamento")
    }

    @Test
    fun `preserves every conditional section within Discord embed limits`() {
        val payload = render(richMatch())
        val names = payload.embeds.flatMap { it.fields }.map { it.name }

        assertThat(names).contains(
            "⚽ GOLS",
            "🎯 ASSISTÊNCIAS",
            "🥇 DESTAQUES",
            "⭐ CRAQUE DA PARTIDA",
            "🧠 POR TRÁS DA JOGADA",
            "🪄 NO UM CONTRA UM",
            "🚧 XERIFE DA PARTIDA",
            "🍍 BAGRE DA PARTIDA",
            "🟥 PERDEU A CABEÇA",
            "🎯 PASSE DE PRECISÃO",
            "📮 CORREIO EXTRAVIADO",
            "🧤 GOLEIRO",
        )
        assertThat(payload.embeds).hasSize(2)
        payload.embeds.forEach { embed ->
            assertThat(embed.fields).hasSizeLessThanOrEqualTo(25)
            assertThat(embed.title.length + (embed.description?.length ?: 0) + embed.fields.sumOf { it.name.length + it.value.length })
                .isLessThanOrEqualTo(6_000)
        }
    }

    private fun render(source: MatchResponse): DiscordPayload {
        val match = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(match, ClubId(OUR_CLUB))
        val stories = MatchStoryExtractor().extract(interpretation)
        return renderer.renderMatch(match, interpretation, stories)
    }

    private fun advancedMatch() = match(players = linkedMapOf(
        "creator" to player("Creator", rating = "8.5", tacklesMade = "2", tackleAttempts = "2", aggregate0 = "6:4,115:2,152:9"),
        "dribbler" to player("Dribbler", rating = "8.0", aggregate0 = "112:8,174:18"),
        "support" to player("Support", rating = "7.0"),
        "low" to player("Low", rating = "5.0"),
    ))

    private fun richMatch() = match(players = linkedMapOf(
        "mvp" to player("MVP", rating = "9.5", goals = "3", assists = "1", shots = "6", manOfTheMatch = "1", passesMade = "18", passAttempts = "20"),
        "creator" to player("Creator", rating = "8.6", assists = "2", passesMade = "15", passAttempts = "20", aggregate0 = "115:2,152:9"),
        "dribbler" to player("Dribbler", rating = "8.0", passesMade = "12", passAttempts = "18", aggregate0 = "112:8,174:18"),
        "defender" to player("Defender", rating = "7.8", tacklesMade = "2", tackleAttempts = "2", passesMade = "12", passAttempts = "18", aggregate0 = "6:4"),
        "red" to player("Sent Off", rating = "7.0", redCards = "1", passesMade = "11", passAttempts = "16"),
        "bagre" to player("Bagre", rating = "5.0", passesMade = "1", passAttempts = "10", tacklesMade = "0", tackleAttempts = "5"),
        "keeper" to goalkeeper("Keeper"),
    ))

    private fun match(players: Map<String, PlayerEntry>) = MatchResponse(
        matchId = "advanced-discord",
        timestamp = 1_767_225_600,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            OUR_CLUB to ClubMatchEntry(ClubDetails("Our FC", OUR_CLUB), score = "4", result = "1"),
            "opponent" to ClubMatchEntry(ClubDetails("Opponent FC", "opponent"), score = "1", result = "0"),
        ),
        players = mapOf(OUR_CLUB to players),
    )

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        assists: String = "0",
        shots: String = "0",
        manOfTheMatch: String = "0",
        passesMade: String = "16",
        passAttempts: String = "20",
        tacklesMade: String = "0",
        tackleAttempts: String = "0",
        redCards: String = "0",
        aggregate0: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        manOfTheMatch = manOfTheMatch,
        passesMade = passesMade,
        passAttempts = passAttempts,
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        redCards = redCards,
        secondsPlayed = "5400",
        matchEventAggregate0 = aggregate0,
    )

    private fun goalkeeper(name: String) = PlayerEntry(
        playerName = name,
        position = PlayerEntry.POSITION_GOALKEEPER,
        rating = "8.8",
        saves = "7",
        goalsConceded = "1",
        reflexSaves = "4",
        secondsPlayed = "5400",
    )

    private companion object {
        const val OUR_CLUB = "ours"
    }
}
