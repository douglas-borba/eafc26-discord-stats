package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// --------------------------------------------------------------------------
// Discord webhook payload model
// --------------------------------------------------------------------------

data class DiscordPayload(val embeds: List<DiscordEmbed>)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DiscordEmbed(
    val title: String,
    val description: String? = null,
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

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy '•' HH:mm", PT_BR)

private val SEPARATOR = EmbedField("​", "──────────────────────────────")

// Zero-width space for forcing blank lines in Discord
private const val BLANK = "\u200B"

object DiscordEmbedBuilder {

    private val log = LoggerFactory.getLogger(DiscordEmbedBuilder::class.java)

    @Volatile
    var phraseBank: PhraseBank? = null

    fun build(match: MatchResponse, ourClubId: String, zoneId: ZoneId = ZoneId.systemDefault()): DiscordPayload {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourName = ourEntry?.resolvedName() ?: "Nós"
        val oppName = oppEntry?.value?.resolvedName() ?: "Adversário"

        // Use MatchOutcomeResolver for correct result determination
        val resolved = MatchOutcomeResolver.resolve(ourEntry, oppEntry?.value)
        val outcome = resolved.outcome

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(DATE_FMT)

        // Get all players from the match (including BOT goalkeeper)
        val allPlayers = (match.players[ourClubId] ?: emptyMap()).values

        // Eligible players for stats (excludes substitutes with low playtime)
        val allActive = PlayerStatisticsEligibility.eligiblePlayers(allPlayers)

        // Find goalkeeper from ALL players (not just eligible) since BOT GK may not pass eligibility
        val goalkeeper = allPlayers
            .filter { it.isGoalkeeper() }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        // Log goalkeeper status for debugging
        if (goalkeeper == null) {
            log.debug("No goalkeeper found in players list for match {}. Players: {}", 
                match.matchId, 
                allPlayers.map { "pos=${it.position}, name=${it.playerName}" })
        } else {
            log.debug("Goalkeeper found for match {}: pos={}, name={}, saves={}, goalsConceded={}",
                match.matchId, goalkeeper.position, goalkeeper.playerName, goalkeeper.saves, goalkeeper.goalsConceded)
        }

        // Outfield players for awards (from eligible players, excluding goalkeeper)
        val outfield = allActive.filter { !it.isGoalkeeper() }
        val matchId = match.matchId

        // First, determine the Bagre (lowest-rated player) - this player cannot receive positive awards
        val bagrePlayer = BagrePerformanceEvaluator.selectBagrePlayer(outfield)
        val excludeFromPositive: Set<String> = bagrePlayer?.let { setOf(it.playerName ?: "") } ?: emptySet()

        val fields = mutableListOf<EmbedField>()
        // Always prepend a separator so there is a visual break after the header description
        fun addSection(field: EmbedField?) {
            field ?: return
            fields += SEPARATOR
            fields += field
        }

        // Section order as requested:
        // 🥇 DESTAQUES, ⭐ CRAQUE, 🔥 PERIGO CONSTANTE, 🍍 BAGRE, 🚧 XERIFE, 🎯 PASSE DE PRECISÃO, 📮 CORREIO
        addSection(goalsField(allActive))
        addSection(assistsField(allActive))
        addSection(top3AndAvgField(outfield, allActive))
        addSection(craqueField(outfield, matchId, excludeFromPositive))
        addSection(perigoConstanteField(outfield, matchId, excludeFromPositive))
        addSection(bagreField(outfield, matchId))
        addSection(xerifeField(outfield, matchId, excludeFromPositive))
        addSection(passePrecisaoField(outfield, matchId, excludeFromPositive))
        addSection(correioField(outfield, matchId))
        addSection(muralhaField(goalkeeper, matchId))

        return DiscordPayload(listOf(DiscordEmbed(
            title = "🏆 $ourName ${resolved.ourScore} × ${resolved.oppScore} $oppName",
            description = "${outcome.emoji} ${outcome.label}\n📅 $date",
            color = outcome.color,
            fields = fields,
            timestamp = Instant.ofEpochSecond(match.timestamp).toString(),
        )))
    }

    // -- Section builders --------------------------------------------------

    private fun goalsField(players: Collection<PlayerEntry>): EmbedField? {
        val scorers = players
            .filter { (it.goals?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.goals?.toIntOrNull() ?: 0 }
        if (scorers.isEmpty()) return null
        // Goals: ONE blank line after title, then players
        val lines = scorers.joinToString("\n") {
            "• ${it.displayName()} ×${it.goals?.toIntOrNull() ?: 0}"
        }
        return EmbedField("⚽ GOLS", "\n$lines")
    }

    private fun assistsField(players: Collection<PlayerEntry>): EmbedField? {
        val assisters = players
            .filter { (it.assists?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.assists?.toIntOrNull() ?: 0 }
        if (assisters.isEmpty()) return null
        // Assists: ONE blank line after title, then players
        val lines = assisters.joinToString("\n") {
            "• ${it.displayName()} ×${it.assists?.toIntOrNull() ?: 0}"
        }
        return EmbedField("🎯 ASSISTÊNCIAS", "\n$lines")
    }

    private fun top3AndAvgField(
        outfield: Collection<PlayerEntry>,
        allActive: Collection<PlayerEntry>,
    ): EmbedField? {
        val top = outfield
            .filter { it.rating != null }
            .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            .take(3)
        val allRatings = allActive.mapNotNull { it.rating?.toDoubleOrNull() }
        if (top.isEmpty() && allRatings.isEmpty()) return null

        val medals = listOf("🥇", "🥈", "🥉")
        val parts = mutableListOf<String>()

        top.forEachIndexed { i, p ->
            val name = p.displayName()
            parts += "${medals[i]} $name — Nota ${fmtRating(p.rating)}"
        }

        if (allRatings.isNotEmpty()) {
            parts += "⭐ Média do time: ${fmtRating("%.2f".format(allRatings.average()))}"
        }

        return EmbedField("🥇 DESTAQUES", "\n" + parts.joinToString("\n\n"))
    }

    private fun craqueField(outfield: Collection<PlayerEntry>, matchId: String, excludeFromPositive: Set<String>): EmbedField? {
        val eligible = outfield.filter { it.playerName !in excludeFromPositive }
        val selection = CraqueSelector.select(eligible) ?: return null
        val name = selection.player.displayName()
        val phrase = pickFromCategory(PhraseCategory.MVP, matchId, name)

        // Format: blank line, name, blank line, stats, blank line, quote
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("${selection.reason}\n$BLANK\n")
            append("💬 \"$phrase\"")
        }

        return EmbedField("⭐ CRAQUE DA PARTIDA", value)
    }

    private fun bagreField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val evaluation = BagrePerformanceEvaluator.evaluate(outfield, matchId, phraseBank)
            ?: return null

        val name = evaluation.player.displayName()
        
        // Build the field value with structured content
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("📊 Nota ${evaluation.rating}\n")
            append("📝 ${evaluation.reason}\n$BLANK\n")
            
            // Add tackle stats if present
            evaluation.tackleStats?.let {
                append("🛡️ Desarmes: $it\n")
            }
            
            // Add pass stats if present
            evaluation.passStats?.let {
                append("📉 Passes: $it\n")
            }
            
            append("$BLANK\n💬 \"${evaluation.phrase}\"")
        }

        return EmbedField("🍍 BAGRE DA PARTIDA", value)
    }

    private fun xerifeField(outfield: Collection<PlayerEntry>, matchId: String, excludeFromPositive: Set<String>): EmbedField? {
        val eligible = outfield.filter { it.playerName !in excludeFromPositive }
        val selection = XerifeSelector.select(eligible) ?: return null
        val name = selection.player.displayName()
        val phrase = pickFromCategory(PhraseCategory.XERIFE, matchId, name)

        // Format: blank line, name, blank line, stats, blank line, quote
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("🛡️ ${selection.tacklesMade}/${selection.tackleAttempts} desarmes\n")
            append("📈 Aproveitamento: ${selection.successRate}%\n$BLANK\n")
            append("💬 \"$phrase\"")
        }
        return EmbedField("🚧 XERIFE DA PARTIDA", value)
    }

    private fun passePrecisaoField(outfield: Collection<PlayerEntry>, matchId: String, excludeFromPositive: Set<String>): EmbedField? {
        val eligible = outfield.filter { it.playerName !in excludeFromPositive }
        val selection = PassePrecisaoSelector.select(eligible) ?: return null
        val name = selection.player.displayName()
        val phrase = pickFromCategory(PhraseCategory.PASSE_PRECISAO, matchId, name)

        // Format: blank line, name, blank line, stats, blank line, quote
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("📊 ${selection.passesMade}/${selection.passAttempts} passes certos\n")
            append("📈 Aproveitamento: ${selection.accuracy}%\n$BLANK\n")
            append("💬 \"$phrase\"")
        }
        return EmbedField("🎯 PASSE DE PRECISÃO", value)
    }

    private fun perigoConstanteField(outfield: Collection<PlayerEntry>, matchId: String, excludeFromPositive: Set<String>): EmbedField? {
        val eligible = outfield.filter { it.playerName !in excludeFromPositive }
        val selection = PerigoConstanteSelector.select(eligible) ?: return null
        val name = selection.player.displayName()

        // Choose phrase category based on efficiency
        val category = if (selection.efficient) PhraseCategory.PERIGO_EFICIENTE else PhraseCategory.PERIGO_VOLUME
        val phrase = pickFromCategory(category, matchId, name)

        // Format: blank line, name, blank line, stats, blank line, quote
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("🥅 ${selection.shots} finalizações\n")
            append("⚽ ${selection.goals} ${if (selection.goals == 1) "gol" else "gols"}\n$BLANK\n")
            append("💬 \"$phrase\"")
        }
        return EmbedField("🔥 PERIGO CONSTANTE", value)
    }

    private fun correioField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val candidates = outfield.mapNotNull { p ->
            val attempted = p.passAttempts?.toIntOrNull() ?: return@mapNotNull null
            val made = p.passesMade?.toIntOrNull() ?: return@mapNotNull null
            val failed = maxOf(attempted - made, 0)
            if (failed == 0) null else Pair(p, failed)
        }
        val best = candidates.maxWithOrNull(
            compareBy<Pair<PlayerEntry, Int>> { it.second }
                .thenByDescending { it.first.playerName ?: "" }
        ) ?: return null
        val name = best.first.displayName()
        val phrase = pickFromCategory(PhraseCategory.CORREIO, matchId, name)
        
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("📬 ${best.second} passes errados\n$BLANK\n")
            append("💬 \"$phrase\"")
        }
        return EmbedField("📮 CORREIO EXTRAVIADO", value)
    }

    private fun muralhaField(gk: PlayerEntry?, matchId: String): EmbedField? {
        gk ?: return null
        val saves = gk.saves?.toIntOrNull() ?: 0
        val goalsConceded = gk.goalsConceded?.toIntOrNull() ?: 0
        val name = gk.displayName()
        val phrase = pickFromCategory(PhraseCategory.GOALKEEPER, matchId, name)
        
        // Format: name, stats (saves and goals conceded), quote
        val savesText = if (saves == 1) "1 defesa" else "$saves defesas"
        val goalsText = if (goalsConceded == 1) "1 gol sofrido" else "$goalsConceded gols sofridos"
        
        val value = buildString {
            append("$BLANK\n$name\n$BLANK\n")
            append("🧤 $savesText\n")
            append("⚽ $goalsText\n$BLANK\n")
            append("💬 \"$phrase\"")
        }
        return EmbedField("🧤 GOLEIRO", value)
    }

    // -- Helpers -----------------------------------------------------------

    private fun pickFromCategory(cat: PhraseCategory, matchId: String, seed: String): String {
        val list = phraseBank?.get(cat) ?: cat.defaults
        val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
        return list[(abs(hash) % list.size).toInt()]
    }

    private fun fmtRating(raw: String?): String {
        val d = raw?.toDoubleOrNull() ?: return "N/D"
        return "%.2f".format(d).replace('.', ',')
    }
}
