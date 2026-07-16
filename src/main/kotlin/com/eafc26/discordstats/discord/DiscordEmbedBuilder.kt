package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --------------------------------------------------------------------------
// Discord webhook payload model
// --------------------------------------------------------------------------

data class DiscordPayload(val embeds: List<DiscordEmbed>)

data class DiscordEmbed(
    val title: String,
    val color: Int,
    val fields: List<EmbedField>,
    val footer: EmbedFooter? = null,
    val timestamp: String? = null,
)

data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = false,
)

data class EmbedFooter(val text: String)

// --------------------------------------------------------------------------
// Builder
// --------------------------------------------------------------------------

private val DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

private const val COLOR_WIN  = 0x2ECC71
private const val COLOR_DRAW = 0x95A5A6
private const val COLOR_LOSS = 0xE74C3C

object DiscordEmbedBuilder {

    fun build(match: MatchResponse, ourClubId: String, zoneId: ZoneId = ZoneId.systemDefault()): DiscordPayload {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourName  = ourEntry?.resolvedName() ?: "Us"
        val oppName  = oppEntry?.value?.resolvedName() ?: "Opponent"
        val ourScore = ourEntry?.score?.toIntOrNull() ?: 0
        val oppScore = oppEntry?.value?.score?.toIntOrNull() ?: 0

        val (resultLabel, color) = when (ourEntry?.result) {
            "1"  -> "Win"  to COLOR_WIN
            "2"  -> "Draw" to COLOR_DRAW
            else -> "Loss" to COLOR_LOSS
        }

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(DATE_FMT)

        // All players who actually played (secondsPlayed null = include, 0 = exclude)
        val allActive = (match.players[ourClubId] ?: emptyMap())
            .values
            .filter { (it.secondsPlayed?.toIntOrNull() ?: 1) > 0 }

        // Most-played goalkeeper; outfield = everyone else
        val goalkeeper = allActive
            .filter { it.position == "goalkeeper" }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        val outfield = allActive.filter { it.position != "goalkeeper" }

        val fields = mutableListOf(
            EmbedField("Result", resultLabel, inline = true),
            EmbedField("Score",  "$ourScore – $oppScore", inline = true),
            EmbedField("Date",   date, inline = true),
        )

        goalsField(allActive)?.let      { fields += it }
        assistsField(allActive)?.let    { fields += it }
        goalkeeperField(goalkeeper)?.let { fields += it }
        top3Field(allActive)?.let       { fields += it }
        teamAvgField(allActive)?.let    { fields += it }
        bagreField(outfield)?.let       { fields += it }

        return DiscordPayload(listOf(DiscordEmbed(
            title     = "$ourName vs $oppName",
            color     = color,
            fields    = fields,
            footer    = EmbedFooter("Match ID: ${match.matchId}"),
            timestamp = Instant.ofEpochSecond(match.timestamp).toString(),
        )))
    }

    // -- Section builders --------------------------------------------------

    private fun goalsField(players: Collection<PlayerEntry>): EmbedField? {
        val scorers = players
            .filter { (it.goals?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.goals?.toIntOrNull() ?: 0 }
        if (scorers.isEmpty()) return null
        val lines = scorers.joinToString("\n") {
            "• ${it.playerName ?: "Unknown"} (${it.goals?.toIntOrNull() ?: 0})"
        }
        return EmbedField("Goal Scorers", lines)
    }

    private fun assistsField(players: Collection<PlayerEntry>): EmbedField? {
        val assisters = players
            .filter { (it.assists?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.assists?.toIntOrNull() ?: 0 }
        if (assisters.isEmpty()) return null
        val lines = assisters.joinToString("\n") {
            "• ${it.playerName ?: "Unknown"} (${it.assists?.toIntOrNull() ?: 0})"
        }
        return EmbedField("Assists", lines)
    }

    private fun goalkeeperField(gk: PlayerEntry?): EmbedField? {
        gk ?: return null
        val lines = mutableListOf(
            "🧤 ${gk.playerName ?: "Unknown"} — ${gk.saves ?: "0"} defesas",
            "⭐ Nota: ${gk.rating ?: "N/A"}",
        )
        val conceded = gk.goalsConceded?.toIntOrNull() ?: 0
        if (conceded > 0) lines += "🥅 Gols sofridos: $conceded"
        return EmbedField("🥅 Goleiro", lines.joinToString("\n"))
    }

    private fun top3Field(players: Collection<PlayerEntry>): EmbedField? {
        val top = players
            .filter { it.rating != null }
            .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            .take(3)
        if (top.isEmpty()) return null
        val medals = listOf("🥇", "🥈", "🥉")
        val lines = top.mapIndexed { i, p ->
            val motmTag = if (p.manOfTheMatch == "1") " ⭐ MOTM" else ""
            "${medals[i]} ${p.playerName ?: "Unknown"} — ${p.rating}$motmTag"
        }.joinToString("\n")
        return EmbedField("🏅 Destaques", lines)
    }

    private fun teamAvgField(players: Collection<PlayerEntry>): EmbedField? {
        val ratings = players.mapNotNull { it.rating?.toDoubleOrNull() }
        if (ratings.isEmpty()) return null
        return EmbedField("⭐ Média do time", "%.2f".format(ratings.average()))
    }

    private fun bagreField(outfield: Collection<PlayerEntry>): EmbedField? {
        val rated = outfield.filter { it.rating != null }
        if (rated.isEmpty()) return null

        val bagre = rated.minWithOrNull(
            compareBy<PlayerEntry> { it.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy    { passCompletionPct(it) ?: 100.0 }
                .thenBy    { tackleSuccessPct(it)  ?: 100.0 }
                .thenByDescending { shotsNoGoal(it) }
                .thenByDescending { missedPasses(it) }
        ) ?: return null

        val sections = mutableListOf<String>()
        sections += "**${bagre.playerName ?: "Unknown"} — ${bagre.rating}**"

        val passAttempts = bagre.passAttempts?.toIntOrNull() ?: 0
        val passesMade   = bagre.passesMade?.toIntOrNull() ?: 0
        if (passAttempts > 0) {
            val pct = passesMade * 100 / passAttempts
            sections += "📉 Passes: $passesMade/$passAttempts — $pct%\n❌ Passes errados: ${passAttempts - passesMade}"
        }

        val tackleAttempts = bagre.tackleAttempts?.toIntOrNull() ?: 0
        val tacklesMade    = bagre.tacklesMade?.toIntOrNull() ?: 0
        if (tackleAttempts > 0) {
            val pct = tacklesMade * 100 / tackleAttempts
            sections += "🛡️ Desarmes: $tacklesMade/$tackleAttempts — $pct%\n❌ Desarmes perdidos: ${tackleAttempts - tacklesMade}"
        }

        val shots = bagre.shots?.toIntOrNull() ?: 0
        if (shots > 0) {
            sections += "🎯 Finalizações: $shots\n⚽ Gols: ${bagre.goals?.toIntOrNull() ?: 0}\n🎯 Assistências: ${bagre.assists?.toIntOrNull() ?: 0}"
        }

        val redCards = bagre.redCards?.toIntOrNull() ?: 0
        if (redCards > 0) sections += "🟥 Cartão vermelho: $redCards"

        return EmbedField("🍍 Bagre da Partida", sections.joinToString("\n\n"))
    }

    // -- Derived stat helpers ----------------------------------------------

    private fun passCompletionPct(p: PlayerEntry): Double? {
        val attempts = p.passAttempts?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return (p.passesMade?.toIntOrNull() ?: 0).toDouble() * 100.0 / attempts
    }

    private fun tackleSuccessPct(p: PlayerEntry): Double? {
        val attempts = p.tackleAttempts?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return (p.tacklesMade?.toIntOrNull() ?: 0).toDouble() * 100.0 / attempts
    }

    private fun missedPasses(p: PlayerEntry): Int {
        val attempts = p.passAttempts?.toIntOrNull() ?: 0
        val made     = p.passesMade?.toIntOrNull() ?: 0
        return maxOf(attempts - made, 0)
    }

    private fun shotsNoGoal(p: PlayerEntry): Int {
        if ((p.goals?.toIntOrNull() ?: 0) > 0) return 0
        return p.shots?.toIntOrNull() ?: 0
    }
}
