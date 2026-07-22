package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR_HIST = Locale.forLanguageTag("pt-BR")
private val HIST_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR_HIST)

/**
 * Builds the compact embed sent to the optional history webhook.
 * One embed per match: date, result, MVP (if present). Nothing else.
 */
object HistoryEmbedBuilder {

    fun build(match: MatchResponse, ourClubId: String, zoneId: ZoneId = ZoneId.systemDefault(), proNames: Map<String, String> = emptyMap()): DiscordPayload {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        // Use MatchOutcomeResolver for consistent result determination
        val resolved = MatchOutcomeResolver.resolve(ourEntry, oppEntry?.value)
        val outcome = resolved.outcome

        // Map outcome to history-specific emoji
        val resultEmoji = when (outcome) {
            MatchOutcomeResolver.Outcome.VICTORY -> "✅"
            MatchOutcomeResolver.Outcome.DRAW -> "🤝"
            MatchOutcomeResolver.Outcome.LOSS -> "❌"
        }

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(HIST_DATE_FMT)

        val fields = mutableListOf<EmbedField>()
        fields += EmbedField("📅 Data", date, inline = true)
        fields += EmbedField("🏆 Resultado", "$resultEmoji ${outcome.label} ${resolved.ourScore} × ${resolved.oppScore}", inline = true)

        val mvp = PlayerStatisticsEligibility.eligiblePlayers(
            (match.players[ourClubId] ?: emptyMap()).values
        ).firstOrNull { it.manOfTheMatch == "1" }

        if (mvp != null) {
            val name = mvp.displayName(proNames)
            val rating = mvp.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: "N/D"
            fields += EmbedField("⭐ MVP", "$name ($rating)", inline = true)
        }

        return DiscordPayload(listOf(DiscordEmbed(
            title = "📚 Histórico de Partidas",
            color = outcome.color,
            fields = fields,
            timestamp = Instant.ofEpochSecond(match.timestamp).toString(),
        )))
    }
}
