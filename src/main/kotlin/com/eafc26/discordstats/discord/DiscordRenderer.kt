package com.eafc26.discordstats.discord

import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
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
        addSection(summary.behindThePlay?.let {
            val secondAssistLabel = if (it.secondAssists == 1) "assistência prévia" else "assistências prévias"
            val throughPassLabel = if (it.throughPasses == 1) "passe em profundidade" else "passes em profundidade"
            EmbedField(
                "🧠 POR TRÁS DA JOGADA",
                "$BLANK\n${it.name}\n$BLANK\n" +
                    "🎯 ${it.secondAssists} $secondAssistLabel\n" +
                    "📤 ${it.throughPasses} $throughPassLabel\n$BLANK\n" +
                    "💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.oneOnOne?.let {
            val beatLabel = if (it.beats == 1) "adversário superado" else "adversários superados"
            EmbedField(
                "🪄 NO UM CONTRA UM",
                "$BLANK\n${it.name}\n$BLANK\n" +
                    "⚡ ${it.beats} $beatLabel\n" +
                    "$BLANK\n" +
                    "💬 \"${it.phrase}\"",
            )
        })
        addSection(summary.xerife?.let {
            val defensiveActions = it.tacklesMade + it.interceptions
            EmbedField(
                "🚧 XERIFE DA PARTIDA",
                buildString {
                    append("$BLANK\n${it.name}\n$BLANK\n")
                    if (it.interceptions > 0) {
                        append("🛡️ $defensiveActions ações defensivas\n")
                    }
                    append("🛡️ ${it.tacklesMade}/${it.tackleAttempts} desarmes\n")
                    if (it.interceptions > 0) append("🧲 ${it.interceptions} interceptações\n")
                    if (it.interceptions == 0 || it.tackleAttempts > 0) {
                        append("📈 Aproveitamento: ${it.successRate}%\n")
                    }
                    append("$BLANK\n💬 \"${it.phrase}\"")
                },
            )
        })
        addSection(summary.bagre?.let {
            EmbedField(
                it.title,
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
            dnfNotice(footballMatch, interpretation)?.let { append("\n\n$it") }
            editorialNarrative?.takeIf { it.isNotBlank() }?.length
        }
        val title = if (interpretation.result.outcome == MatchOutcome.WIN) {
            "🏆 ${summary.ourName} ${summary.ourScore} × ${summary.oppScore} ${summary.oppName}"
        } else {
            "${summary.ourName} ${summary.ourScore} × ${summary.oppScore} ${summary.oppName}"
        }

        return DiscordPayload(
            splitIntoEmbeds(title, description, summary.outcome.color, summary.timestamp, fields)
        )
    }

    /**
     * Discord accepts at most 25 fields and 6,000 characters per embed. A rich
     * match can legitimately contain more conditional sections than fit in one
     * embed, so it is continued in a second embed instead of hiding a section.
     */
    private fun splitIntoEmbeds(
        title: String,
        description: String,
        color: Int,
        timestamp: String,
        fields: List<EmbedField>,
    ): List<DiscordEmbed> {
        val chunks = mutableListOf<List<EmbedField>>()
        var current = mutableListOf<EmbedField>()
        var currentCharacters = title.length + description.length

        fun flush() {
            val trimmed = current.dropWhile { it == SEPARATOR }.dropLastWhile { it == SEPARATOR }
            if (trimmed.isNotEmpty()) chunks += trimmed
            current = mutableListOf()
            currentCharacters = title.length + DISCORD_CONTINUATION_TITLE_RESERVE
        }

        fields.forEach { field ->
            val fieldCharacters = field.name.length + field.value.length
            if (current.isNotEmpty() &&
                (current.size == DISCORD_MAX_FIELDS_PER_EMBED ||
                    currentCharacters + fieldCharacters > DISCORD_MAX_EMBED_CHARACTERS)
            ) {
                flush()
            }
            current += field
            currentCharacters += fieldCharacters
        }
        flush()

        val nonEmptyChunks = if (chunks.isEmpty()) listOf(emptyList()) else chunks
        return nonEmptyChunks.mapIndexed { index, chunk ->
            DiscordEmbed(
                title = if (index == 0) title else "$title · continuação ${index + 1}",
                description = description.takeIf { index == 0 },
                color = color,
                fields = chunk,
                timestamp = timestamp,
            )
        }
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

    private fun dnfNotice(
        footballMatch: FootballMatch,
        interpretation: MatchInterpretation,
    ): String? {
        if (footballMatch.completion.status.name != "DNF") return null
        return when (footballMatch.completion.dnfClubId) {
            interpretation.perspectiveClubId -> "⚠️ Nosso clube saiu antes do fim"
            interpretation.result.opponentClub -> "⚠️ Adversário saiu antes do fim"
            else -> "⚠️ Partida encerrada por DNF"
        }
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
        const val DISCORD_MAX_FIELDS_PER_EMBED = 25
        const val DISCORD_MAX_EMBED_CHARACTERS = 6_000
        const val DISCORD_CONTINUATION_TITLE_RESERVE = 20
    }
}
