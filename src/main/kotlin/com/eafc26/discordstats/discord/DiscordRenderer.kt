package com.eafc26.discordstats.discord

import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.presentation.MatchPresentationTimeZone
import org.springframework.stereotype.Component
import java.time.ZoneId

/**
 * Discord-only rendering of canonical match facts and decisions.
 *
 * Football rules are evaluated before this boundary. This class only applies
 * Discord structure, copy, emoji, ordering and channel-size constraints.
 */
@Component
class DiscordRenderer(
    private val summaryBuilder: MatchSummaryBuilder,
) {
    fun renderMatch(
        footballMatch: FootballMatch,
        interpretation: MatchInterpretation,
        stories: MatchStories,
        zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL,
        editorialNarrative: String? = null,
    ): DiscordPayload {
        validateInputs(footballMatch, interpretation, stories)
        val summary = summaryBuilder.build(footballMatch, interpretation, stories, zoneId)
        val players = perspectivePlayers(footballMatch, interpretation)
        val fields = mutableListOf<EmbedField>()

        fun addSection(field: EmbedField?) {
            field ?: return
            fields += SEPARATOR
            fields += field
        }

        addSection(summary.goals?.let {
            EmbedField("⚽ GOLS", "\n" + it.scorers.joinToString("\n") { scorer ->
                "• ${scorer.name} ×${scorer.count}"
            })
        })
        addSection(summary.assists?.let {
            EmbedField("🎯 ASSISTÊNCIAS", "\n" + it.assisters.joinToString("\n") { assister ->
                "• ${assister.name} ×${assister.count}"
            })
        })
        addSection(highlights(stories, players))
        addSection(summary.craque?.let {
            EmbedField(
                "⭐ CRAQUE DA PARTIDA",
                "$BLANK\n${it.name}\n$BLANK\n${it.reason}\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })
        summary.offensiveNarratives.take(DISCORD_OFFENSIVE_STORY_LIMIT).forEach {
            val goalsLabel = if (it.goals == 1) "gol" else "gols"
            addSection(
                EmbedField(
                    "${it.emoji} ${it.title}",
                    "$BLANK\n${it.name}\n$BLANK\n${it.shots} chutes • ${it.goals} $goalsLabel\n" +
                        "$BLANK\n💬 \"${it.message}\"",
                )
            )
        }
        addSection(summary.bagre?.let {
            EmbedField(
                "🍍 BAGRE DA PARTIDA",
                buildString {
                    append("$BLANK\n${it.name}\n$BLANK\n")
                    append("📊 Nota ${it.rating}\n")
                    append("📝 ${it.reason}\n$BLANK\n")
                    it.tackleStats?.let { stats -> append("🛡️ Desarmes: $stats\n") }
                    it.passStats?.let { stats -> append("📉 Passes: $stats\n") }
                    append("$BLANK\n💬 \"${it.phrase}\"")
                },
            )
        })
        addSection(summary.redCard?.let {
            EmbedField(
                "🟥 PERDEU A CABEÇA",
                "$BLANK\n${it.name}\n$BLANK\nCartão vermelho\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.xerife?.let {
            EmbedField(
                "🚧 XERIFE DA PARTIDA",
                "$BLANK\n${it.name}\n$BLANK\n" +
                    "🛡️ ${it.tacklesMade}/${it.tackleAttempts} desarmes\n" +
                    "📈 Aproveitamento: ${it.successRate}%\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.passePrecisao?.let {
            EmbedField(
                "🎯 PASSE DE PRECISÃO",
                "$BLANK\n${it.name}\n$BLANK\n" +
                    "📊 ${it.passesMade}/${it.passAttempts} passes certos\n" +
                    "📈 Aproveitamento: ${it.accuracy}%\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.correioExtraviado?.let {
            EmbedField(
                "📮 CORREIO EXTRAVIADO",
                "$BLANK\n${it.name}\n$BLANK\n" +
                    "📬 ${it.playerAccuracyPct}% de acerto\n" +
                    "📊 Média do time: ${it.teamAccuracyPct}%\n" +
                    "📉 -${it.deltaPct}% abaixo da média\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.muralha?.let {
            val saves = if (it.saves == 1) "1 defesa" else "${it.saves} defesas"
            val conceded = if (it.goalsConceded == 1) {
                "1 gol sofrido"
            } else {
                "${it.goalsConceded} gols sofridos"
            }
            EmbedField(
                "🧤 GOLEIRO",
                "$BLANK\n${it.name}\n$BLANK\n${it.archetypeTitle}\n$BLANK\n" +
                    "🧤 $saves\n⚽ $conceded\n$BLANK\n💬 \"${it.phrase}\"",
            )
        })

        val description = buildString {
            append("${summary.outcome.emoji} ${summary.outcome.label}\n📅 ${summary.date}")
            // Temporarily hidden until LLM issue is resolved
            // if (!editorialNarrative.isNullOrBlank()) {
            //     append("\n\n")
            //     append(editorialNarrative)
            // }
        }

        return DiscordPayload(
            listOf(
                DiscordEmbed(
                    title = "🏆 ${summary.ourName} ${summary.ourScore} × ${summary.oppScore} ${summary.oppName}",
                    description = description,
                    color = summary.outcome.color,
                    fields = fields,
                    timestamp = summary.timestamp,
                )
            )
        )
    }

    private fun highlights(
        stories: MatchStories,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): EmbedField? {
        val content = stories.stories
            .singleOrNull { it.type == StoryType.HIGHLIGHTS }
            ?.content as? StoryContent.Highlights ?: return null
        val parts = content.unfilteredPlayers.mapIndexed { index, highlight ->
            "${MEDALS[index]} ${displayName(players.getValue(highlight.playerId))} — Nota ${fmtRating(highlight.rating)}"
        }.toMutableList()
        content.teamAverageRating?.let { parts += "⭐ Média do time: ${fmtRating(it)}" }
        return EmbedField("🥇 DESTAQUES", "\n" + parts.joinToString("\n\n"))
    }

    private fun perspectivePlayers(
        footballMatch: FootballMatch,
        interpretation: MatchInterpretation,
    ): Map<PlayerId, PlayerMatchPerformance> = footballMatch.participants
        .first { it.club.id == interpretation.perspectiveClubId }
        .players
        .associateBy { it.player.id }

    private fun validateInputs(
        footballMatch: FootballMatch,
        interpretation: MatchInterpretation,
        stories: MatchStories,
    ) {
        require(footballMatch.id == interpretation.matchId && stories.matchId == footballMatch.id) {
            "Match facts, interpretation and stories must belong to the same match"
        }
    }

    private fun displayName(player: PlayerMatchPerformance): String =
        player.player.preferredDisplayName?.value
            ?: if (player.role == PlayerRole.Goalkeeper) "Goleiro BOT" else "Desconhecido"

    private fun fmtRating(value: java.math.BigDecimal): String =
        "%.2f".format(value).replace('.', ',')

    private companion object {
        val SEPARATOR = EmbedField("​", "──────────────────────────────")
        val MEDALS = listOf("🥇", "🥈", "🥉")
        const val BLANK = "\u200B"
        const val DISCORD_OFFENSIVE_STORY_LIMIT = 2
    }
}
