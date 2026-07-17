package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import com.fasterxml.jackson.annotation.JsonInclude
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

private const val COLOR_WIN  = 0x2ECC71
private const val COLOR_DRAW = 0x95A5A6
private const val COLOR_LOSS = 0xE74C3C

private val SEPARATOR = EmbedField("​", "──────────────────────────────")

object DiscordEmbedBuilder {

    @Volatile
    var phraseBank: PhraseBank? = null

    fun build(match: MatchResponse, ourClubId: String, zoneId: ZoneId = ZoneId.systemDefault()): DiscordPayload {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourName  = ourEntry?.resolvedName() ?: "Nós"
        val oppName  = oppEntry?.value?.resolvedName() ?: "Adversário"
        val ourScore = ourEntry?.score?.toIntOrNull() ?: 0
        val oppScore = oppEntry?.value?.score?.toIntOrNull() ?: 0

        val (resultLabel, resultEmoji, color) = when (ourEntry?.result) {
            "1"  -> Triple("Vitória", "🟢", COLOR_WIN)
            "2"  -> Triple("Empate",  "🟡", COLOR_DRAW)
            else -> Triple("Derrota", "🔴", COLOR_LOSS)
        }

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(DATE_FMT)

        val allActive = PlayerStatisticsEligibility.eligiblePlayers(
            (match.players[ourClubId] ?: emptyMap()).values
        )

        val goalkeeper = allActive
            .filter { it.position == "goalkeeper" }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        val outfield = allActive.filter { it.position != "goalkeeper" }
        val matchId  = match.matchId

        val fields = mutableListOf<EmbedField>()
        // Always prepend a separator so there is a visual break after the header description
        fun addSection(field: EmbedField?) {
            field ?: return
            fields += SEPARATOR
            fields += field
        }

        addSection(goalsField(allActive))
        addSection(assistsField(allActive))
        addSection(top3AndAvgField(outfield, allActive, matchId))
        addSection(bagreField(outfield, matchId))
        addSection(xerifeField(outfield, matchId))
        addSection(chutouField(outfield))
        addSection(correioField(outfield))
        addSection(muralhaField(goalkeeper, matchId))

        return DiscordPayload(listOf(DiscordEmbed(
            title       = "🏆 $ourName $ourScore × $oppScore $oppName",
            description = "$resultEmoji $resultLabel\n📅 $date",
            color       = color,
            fields      = fields,
            timestamp   = Instant.ofEpochSecond(match.timestamp).toString(),
        )))
    }

    // -- Section builders --------------------------------------------------

    private fun goalsField(players: Collection<PlayerEntry>): EmbedField? {
        val scorers = players
            .filter { (it.goals?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.goals?.toIntOrNull() ?: 0 }
        if (scorers.isEmpty()) return null
        val lines = scorers.joinToString("\n") {
            "• ${it.playerName ?: "Desconhecido"} ×${it.goals?.toIntOrNull() ?: 0}"
        }
        return EmbedField("⚽ GOLS", lines)
    }

    private fun assistsField(players: Collection<PlayerEntry>): EmbedField? {
        val assisters = players
            .filter { (it.assists?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.assists?.toIntOrNull() ?: 0 }
        if (assisters.isEmpty()) return null
        val lines = assisters.joinToString("\n") {
            "• ${it.playerName ?: "Desconhecido"} ×${it.assists?.toIntOrNull() ?: 0}"
        }
        return EmbedField("🎯 ASSISTÊNCIAS", lines)
    }

    private fun top3AndAvgField(
        outfield: Collection<PlayerEntry>,
        allActive: Collection<PlayerEntry>,
        matchId: String,
    ): EmbedField? {
        val top = outfield
            .filter { it.rating != null }
            .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            .take(3)
        val allRatings = allActive.mapNotNull { it.rating?.toDoubleOrNull() }
        if (top.isEmpty() && allRatings.isEmpty()) return null

        val medals = listOf("🥇", "🥈", "🥉")
        val parts  = mutableListOf<String>()

        top.forEachIndexed { i, p ->
            val name   = p.playerName ?: "Desconhecido"
            if (p.manOfTheMatch == "1") {
                parts += "${medals[i]} $name — Nota ${fmtRating(p.rating)}\n\n⭐ Craque da Partida\n\n💬 \"${pickFromCategory(PhraseCategory.MVP, matchId, name)}\""
            } else {
                parts += "${medals[i]} $name — Nota ${fmtRating(p.rating)}"
            }
        }

        if (allRatings.isNotEmpty()) {
            parts += "⭐ Média do time: ${fmtRating("%.2f".format(allRatings.average()))}"
        }

        return EmbedField("🥇 DESTAQUES", parts.joinToString("\n\n"))
    }

    private fun bagreField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val rated = outfield.filter { it.rating != null }
        if (rated.isEmpty()) return null

        val bagre = rated.minWithOrNull(
            compareBy<PlayerEntry> { it.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy          { passCompletionPct(it) ?: 100.0 }
                .thenBy          { tackleSuccessPct(it)  ?: 100.0 }
                .thenByDescending { shotsNoGoal(it) }
                .thenByDescending { missedPasses(it) }
        ) ?: return null

        val name     = bagre.playerName ?: "Desconhecido"
        val sections = mutableListOf<String>()

        sections += "$name — Nota ${fmtRating(bagre.rating)}\n\n💬 \"${pickFromCategory(PhraseCategory.RATING, matchId, name)}\""

        val passAttempts = bagre.passAttempts?.toIntOrNull() ?: 0
        val passesMade   = bagre.passesMade?.toIntOrNull() ?: 0
        if (passAttempts > 0) {
            val pct = passesMade * 100 / passAttempts
            val phrase = pickFromCategory(PhraseCategory.PASS, matchId, "${name}passes")
            sections += "📉 Passes\n\n• $passesMade/$passAttempts certos ($pct%)\n• ${passAttempts - passesMade} passes errados\n\n💬 \"$phrase\""
        }

        val tackleAttempts = bagre.tackleAttempts?.toIntOrNull() ?: 0
        val tacklesMade    = bagre.tacklesMade?.toIntOrNull() ?: 0
        if (tackleAttempts > 0) {
            val pct = tacklesMade * 100 / tackleAttempts
            val phrase = pickFromCategory(PhraseCategory.TACKLE, matchId, "${name}desarmes")
            sections += "🛡️ Desarmes\n\n• $tacklesMade/$tackleAttempts certos ($pct%)\n• ${tackleAttempts - tacklesMade} tentativas perdidas\n\n💬 \"$phrase\""
        }

        val shots = bagre.shots?.toIntOrNull() ?: 0
        if (shots > 0) {
            val phrase = pickFromCategory(PhraseCategory.SHOOTING, matchId, "${name}chutes")
            sections += "🎯 Finalizações\n\n• $shots chutes\n• ${bagre.goals?.toIntOrNull() ?: 0} gols\n• ${bagre.assists?.toIntOrNull() ?: 0} assistências\n\n💬 \"$phrase\""
        }

        val redCards = bagre.redCards?.toIntOrNull() ?: 0
        if (redCards > 0) sections += "🟥 Cartão vermelho: $redCards"

        return EmbedField("🍍 BAGRE DA PARTIDA", sections.joinToString("\n\n"))
    }

    private fun xerifeField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val xerife = outfield
            .filter { (it.tackleAttempts?.toIntOrNull() ?: 0) > 0 }
            .maxWithOrNull(
                compareBy<PlayerEntry> { it.tacklesMade?.toIntOrNull() ?: 0 }
                    .thenBy { tackleSuccessPct(it) ?: 0.0 }
            ) ?: return null

        val name           = xerife.playerName ?: "Desconhecido"
        val tacklesMade    = xerife.tacklesMade?.toIntOrNull() ?: 0
        val tackleAttempts = xerife.tackleAttempts?.toIntOrNull() ?: 0
        val pct            = if (tackleAttempts > 0) tacklesMade * 100 / tackleAttempts else 0
        val phrase         = pickFromCategory(PhraseCategory.XERIFE, matchId, name)

        val value = "$name\n\n🛡️ $tacklesMade/$tackleAttempts desarmes\n📈 Aproveitamento: $pct%\n\n💬 \"$phrase\""
        return EmbedField("🚧 XERIFE DA PARTIDA", value)
    }

    private fun chutouField(outfield: Collection<PlayerEntry>): EmbedField? {
        // maxWithOrNull + compareBy (ascending) -> highest shots wins
        // thenByDescending (name) -> alphabetically-first name wins on tie
        val winner = outfield
            .filter { (it.goals?.toIntOrNull() ?: 0) == 0 && (it.shots?.toIntOrNull() ?: 0) > 0 }
            .maxWithOrNull(
                compareBy<PlayerEntry> { it.shots?.toIntOrNull() ?: 0 }
                    .thenByDescending { it.playerName ?: "" }
            ) ?: return null
        val shots = winner.shots?.toIntOrNull() ?: return null
        val name  = winner.playerName ?: "Desconhecido"
        return EmbedField("🎯 CHUTOU, MAS NÃO ENTROU", "$name — $shots finalizações e nenhum gol")
    }

    private fun correioField(outfield: Collection<PlayerEntry>): EmbedField? {
        val candidates = outfield.mapNotNull { p ->
            val attempted = p.passAttempts?.toIntOrNull() ?: return@mapNotNull null
            val made      = p.passesMade?.toIntOrNull()  ?: return@mapNotNull null
            val failed    = maxOf(attempted - made, 0)
            if (failed == 0) null else Pair(p, failed)
        }
        val best = candidates.maxWithOrNull(
            compareBy<Pair<PlayerEntry, Int>> { it.second }
                .thenByDescending { it.first.playerName ?: "" }
        ) ?: return null
        val name = best.first.playerName ?: "Desconhecido"
        return EmbedField("📮 CORREIO EXTRAVIADO", "$name — ${best.second} passes errados")
    }

    private fun muralhaField(gk: PlayerEntry?, matchId: String): EmbedField? {
        gk ?: return null
        val saves  = gk.saves?.toIntOrNull() ?: 0
        val phrase = pickFromCategory(PhraseCategory.GOALKEEPER, matchId, gk.playerName ?: "goleiro")
        return EmbedField("🧤 MURALHA DA PARTIDA", "🧤 Defesas realizadas: $saves\n\n💬 \"$phrase\"")
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
