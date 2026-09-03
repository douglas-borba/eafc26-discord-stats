package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class DiscordShadowModeTest {
    private val clubId = "club-us"
    private lateinit var shadow: DiscordShadowMode

    @BeforeEach
    fun setUp() {
        val phrases = PhraseBank(jacksonObjectMapper())
        DiscordEmbedBuilder.phraseBank = phrases
        shadow = DiscordShadowMode(DiscordRenderer(MatchSummaryBuilder(phrases)))
    }

    @Test
    fun `rich match preserves legacy section content when no advanced story exists`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "mvp" to player("MVP", "9.2", goals = "3", assists = "1", shots = "6", mom = "1"),
                    "def" to player("Defender", "8.0", tackles = "6", tackleAttempts = "7"),
                    "red" to player("SentOff", "6.0", redCards = "1"),
                    "bagre" to player("Bagre", "5.5", passes = "5", passAttempts = "20"),
                    "gk" to goalkeeper("Keeper"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertNonBagreSectionsPreserved(result)
    }

    @Test
    fun `minimal payload preserves absent sections and exact ordering`() {
        val result = shadow.compare(match(emptyMap()), clubId, ZoneOffset.UTC)

        assertThat(result.divergences).isEmpty()
        assertThat(result.hasParity).isTrue()
    }

    @Test
    fun `virtual pro names retain legacy section content`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "line" to player("platform-line", "8.8"),
                    "bagre" to player("platform-bagre", "5.5"),
                    "gk" to goalkeeper("platform-gk").copy(manOfTheMatch = "1"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
            mapOf("platform-line" to "Pro Line", "platform-gk" to "Pro Keeper"),
        )

        assertNonBagreSectionsPreserved(result)
    }

    @Test
    fun `known Bagre highlight defect is preserved without renderer recalculation`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "best" to player("Best", "9.0"),
                    "bagre" to player("Bagre", "8.0"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertNonBagreSectionsPreserved(result)
        assertThat(result.canonicalMatch.embeds.single().fields.map { it.name })
            .doesNotContain("🍍 BAGRE DA PARTIDA")
        assertThat(result.canonicalMatch.embeds.single().fields.single {
            it.name == "🥇 DESTAQUES"
        }.value).contains("Bagre")
    }

    @Test
    fun `Discord presentation limit retains only the first two ordered offensive stories`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "decisive" to player("Decisive", "9.5", goals = "3", shots = "6"),
                    "fell-short" to player("FellShort", "9.0", goals = "1", shots = "6"),
                    "wasteful" to player("Wasteful", "8.5", goals = "0", shots = "6"),
                    "bagre" to player("Bagre", "5.0"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertNonBagreSectionsPreserved(result)
        assertThat(result.canonicalMatch.embeds.single().fields.map { it.name })
            .contains("⚡ DECISIVO", "😬 FICOU NO QUASE")
            .doesNotContain("🎯 PODERIA TER DECIDIDO", "😵 FALTOU CAPRICHO")
    }

    @Test
    fun `Discord renders match instants in Brasilia time by default`() {
        val source = match(emptyMap()).copy(timestamp = 1_786_572_420L) // 2026-08-12T22:07:00Z
        val normalized = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(normalized, ClubId(clubId))
        val stories = MatchStoryExtractor().extract(interpretation)

        val payload = DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))
            .renderMatch(normalized, interpretation, stories)

        assertThat(payload.embeds.single().description).contains("12 ago. 2026 • 19:07")
    }

    @Test
    fun `Discord converts a UTC date boundary to the prior Brasilia date`() {
        val source = match(emptyMap()).copy(timestamp = 1_786_584_600L) // 2026-08-13T01:30:00Z
        val normalized = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(normalized, ClubId(clubId))
        val stories = MatchStoryExtractor().extract(interpretation)

        val payload = DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))
            .renderMatch(normalized, interpretation, stories)

        assertThat(payload.embeds.single().description).contains("12 ago. 2026 • 22:30")
    }

    private fun match(players: Map<String, PlayerEntry>) = MatchResponse(
        matchId = "discord-shadow",
        timestamp = 1_718_500_000L,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            clubId to ClubMatchEntry(ClubDetails("Our FC", clubId), score = "4", result = "1"),
            "opponent" to ClubMatchEntry(ClubDetails("Opponent FC", "opponent"), score = "1", result = "0"),
        ),
        players = mapOf(clubId to players),
    )

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        assists: String = "0",
        shots: String = "2",
        mom: String = "0",
        passes: String = "16",
        passAttempts: String = "20",
        tackles: String = "2",
        tackleAttempts: String = "4",
        redCards: String = "0",
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        manOfTheMatch = mom,
        passesMade = passes,
        passAttempts = passAttempts,
        tacklesMade = tackles,
        tackleAttempts = tackleAttempts,
        redCards = redCards,
        secondsPlayed = "5400",
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

    private fun assertNonBagreSectionsPreserved(result: DiscordShadowResult) {
        assertThat(semanticFields(result.canonicalMatch)).isEqualTo(semanticFields(result.legacyMatch))
    }

    private fun semanticFields(payload: DiscordPayload): Map<String, String> = payload.embeds
        .flatMap { it.fields }
        // The canonical renderer intentionally adds this factual-only victory
        // section. Shadow parity still protects every legacy section below it.
        .filterNot {
            it.name == "​" ||
                it.name == "🍍 BAGRE DA PARTIDA" ||
                it.name == "📊 DESTAQUES DA VITÓRIA"
        }
        .associate { it.name to it.value }
}
