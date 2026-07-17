package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR_HIST = Locale.forLanguageTag("pt-BR")
private val HIST_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR_HIST)

private const val HIST_COLOR_WIN  = 0x2ECC71
private const val HIST_COLOR_DRAW = 0x95A5A6
private const val HIST_COLOR_LOSS = 0xE74C3C

/**
 * Builds the compact embed sent to the optional history webhook.
 * One embed per match: date, result, MVP (if present). Nothing else.
 */
object HistoryEmbedBuilder {

    fun build(match: MatchResponse, ourClubId: String, zoneId: ZoneId = ZoneId.systemDefault()): DiscordPayload {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourScore = ourEntry?.score?.toIntOrNull() ?: 0
        val oppScore = oppEntry?.value?.score?.toIntOrNull() ?: 0

        val (resultLabel, resultEmoji, color) = when (ourEntry?.result) {
            "1"  -> Triple("Vitória", "✅", HIST_COLOR_WIN)
            "2"  -> Triple("Empate",  "🤝", HIST_COLOR_DRAW)
            else -> Triple("Derrota", "❌", HIST_COLOR_LOSS)
        }

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(HIST_DATE_FMT)

        val fields = mutableListOf<EmbedField>()
        fields += EmbedField("📅 Data", date, inline = true)
        fields += EmbedField("🏆 Resultado", "$resultEmoji $resultLabel $ourScore × $oppScore", inline = true)

        val mvp = PlayerStatisticsEligibility.eligiblePlayers(
            (match.players[ourClubId] ?: emptyMap()).values
        ).firstOrNull { it.manOfTheMatch == "1" }

        if (mvp != null) {
            val name   = mvp.playerName ?: "Desconhecido"
            val rating = mvp.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: "N/D"
            fields += EmbedField("⭐ MVP", "$name ($rating)", inline = true)
        }

        return DiscordPayload(listOf(DiscordEmbed(
            title     = "📚 Histórico de Partidas",
            color     = color,
            fields    = fields,
            timestamp = Instant.ofEpochSecond(match.timestamp).toString(),
        )))
    }
}
