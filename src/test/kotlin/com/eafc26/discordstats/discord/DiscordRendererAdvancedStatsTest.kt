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
        assertThat(names).doesNotContain("📊 DESTAQUES DA VITÓRIA")
        assertThat(payload.embeds).hasSize(2)
        payload.embeds.forEach { embed ->
            assertThat(embed.fields).hasSizeLessThanOrEqualTo(25)
            assertThat(embed.title.length + (embed.description?.length ?: 0) + embed.fields.sumOf { it.name.length + it.value.length })
                .isLessThanOrEqualTo(6_000)
            assertThat(embed.fields).allMatch { it.value.length <= 1_024 }
        }
    }

    @Test
    fun `opponent DNF victory keeps the normal presentation and factual contributions`() {
        val payload = render(
            match(
                ourScore = "3",
                opponentScore = "0",
                ourWinnerByDnf = "1",
                opponentWinnerByDnf = "0",
                players = linkedMapOf(
                    "scorer" to player(
                        "Scorer",
                        rating = "8.8",
                        goals = "2",
                        assists = "1",
                        passesMade = "24",
                        passAttempts = "28",
                        aggregate0 = "112:6,115:1,152:3",
                    ),
                    "other" to player("Other", rating = "7.5", goals = "1", passesMade = "10", passAttempts = "12"),
                ),
            ),
        )
        val fields = payload.embeds.flatMap { it.fields }.associateBy { it.name }

        assertThat(payload.embeds.first().title).startsWith("🏆 Our FC 3 × 0 Opponent FC")
        assertThat(payload.embeds.first().description).contains("Vitória", "Adversário saiu antes do fim")
        assertThat(fields["⚽ GOLS"]?.value).contains("Scorer ×2", "Other ×1")
        assertThat(fields["🎯 ASSISTÊNCIAS"]?.value).contains("Scorer ×1")
        assertThat(fields).doesNotContainKey("📊 DESTAQUES DA VITÓRIA")
    }

    @Test
    fun `our DNF loss keeps factual contributions without rich victory details`() {
        val payload = render(
            match(
                ourScore = "2",
                opponentScore = "4",
                ourWinnerByDnf = "0",
                opponentWinnerByDnf = "1",
                players = linkedMapOf(
                    "scorer" to player("Scorer", rating = "8.0", goals = "1", assists = "1"),
                    "other" to player("Other", rating = "7.0", goals = "1"),
                    "quiet" to player("Quiet", rating = "6.5"),
                ),
            ),
        )
        val fields = payload.embeds.flatMap { it.fields }.associateBy { it.name }

        assertThat(payload.embeds.first().title).isEqualTo("Our FC 2 × 4 Opponent FC")
        assertThat(payload.embeds.first().description).contains("Derrota", "Nosso clube saiu antes do fim")
        assertThat(fields["⚽ GOLS"]?.value).contains("Scorer ×1", "Other ×1").doesNotContain("Quiet")
        assertThat(fields["🎯 ASSISTÊNCIAS"]?.value).contains("Scorer ×1")
        assertThat(fields).doesNotContainKey("📊 DESTAQUES DA VITÓRIA")
        assertThat(fields.keys).doesNotContain("⭐ CRAQUE DA PARTIDA", "🚧 XERIFE DA PARTIDA", "🍍 BAGRE DA PARTIDA")
    }

    @Test
    fun `loss and draw do not add rich victory block`() {
        val loss = render(match(ourScore = "0", opponentScore = "1", players = mapOf("p" to player("P", rating = "8.0"))))
        val draw = render(match(ourScore = "1", opponentScore = "1", players = mapOf("p" to player("P", rating = "8.0"))))

        assertThat(loss.embeds.first().title).isEqualTo("Our FC 0 × 1 Opponent FC")
        assertThat(draw.embeds.first().title).isEqualTo("Our FC 1 × 1 Opponent FC")
        assertThat(loss.embeds.flatMap { it.fields }.map { it.name }).doesNotContain("📊 DESTAQUES DA VITÓRIA")
        assertThat(draw.embeds.flatMap { it.fields }.map { it.name }).doesNotContain("📊 DESTAQUES DA VITÓRIA")
    }

    @Test
    fun `trophy remains relative to the monitored club perspective`() {
        val victoryFromOpponentPerspective = render(
            match(ourScore = "1", opponentScore = "4", players = mapOf("scorer" to player("Scorer", rating = "8.5", goals = "2"))),
            perspectiveClubId = "opponent",
        )
        val lossFromOpponentPerspective = render(
            match(ourScore = "4", opponentScore = "1", players = mapOf("scorer" to player("Scorer", rating = "8.5", goals = "2"))),
            perspectiveClubId = "opponent",
        )

        assertThat(victoryFromOpponentPerspective.embeds.first().title).isEqualTo("🏆 Opponent FC 4 × 1 Our FC")
        assertThat(victoryFromOpponentPerspective.embeds.first().description).contains("Vitória")
        assertThat(lossFromOpponentPerspective.embeds.first().title).isEqualTo("Opponent FC 1 × 4 Our FC")
        assertThat(lossFromOpponentPerspective.embeds.first().description).contains("Derrota")
    }

    private fun render(source: MatchResponse, perspectiveClubId: String = OUR_CLUB): DiscordPayload {
        val match = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(match, ClubId(perspectiveClubId))
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
        "keeper" to goalkeeper(),
    ))

    private fun match(
        players: Map<String, PlayerEntry>,
        ourScore: String = "4",
        opponentScore: String = "1",
        ourWinnerByDnf: String = "0",
        opponentWinnerByDnf: String = "0",
        matchType: String = "leagueMatch",
    ) = MatchResponse(
        matchId = "advanced-discord",
        timestamp = 1_767_225_600,
        matchType = matchType,
        clubs = linkedMapOf(
            OUR_CLUB to ClubMatchEntry(
                ClubDetails("Our FC", OUR_CLUB), score = ourScore,
                result = when {
                    ourScore.toInt() > opponentScore.toInt() -> "1"
                    ourScore.toInt() < opponentScore.toInt() -> "0"
                    else -> "2"
                },
                winnerByDnf = ourWinnerByDnf,
            ),
            "opponent" to ClubMatchEntry(
                ClubDetails("Opponent FC", "opponent"), score = opponentScore,
                result = when {
                    opponentScore.toInt() > ourScore.toInt() -> "1"
                    opponentScore.toInt() < ourScore.toInt() -> "0"
                    else -> "2"
                },
                winnerByDnf = opponentWinnerByDnf,
            ),
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

    private fun goalkeeper() = PlayerEntry(
        playerName = "Keeper",
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
