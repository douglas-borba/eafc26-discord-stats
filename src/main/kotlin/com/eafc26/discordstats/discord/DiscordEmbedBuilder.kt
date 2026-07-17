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

private val SEPARATOR = EmbedField("​", "──────────────────────────────")

object DiscordEmbedBuilder {

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

        val allActive = PlayerStatisticsEligibility.eligiblePlayers(
            (match.players[ourClubId] ?: emptyMap()).values
        )

        val goalkeeper = allActive
            .filter { it.position == "goalkeeper" }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        val outfield = allActive.filter { it.position != "goalkeeper" }
        val matchId = match.matchId

        val fields = mutableListOf<EmbedField>()
        // Always prepend a separator so there is a visual break after the header description
        fun addSection(field: EmbedField?) {
            field ?: return
            fields += SEPARATOR
            fields += field
        }

        addSection(goalsField(allActive))
        addSection(assistsField(allActive))
        addSection(top3AndAvgField(outfield, allActive))
        addSection(craqueField(outfield, matchId))
        addSection(bagreField(outfield, matchId))
        addSection(xerifeField(outfield, matchId))
        addSection(passePrecisaoField(outfield))
        addSection(chutouField(outfield))
        addSection(correioField(outfield))
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
            val name = p.playerName ?: "Desconhecido"
            parts += "${medals[i]} $name — Nota ${fmtRating(p.rating)}"
        }

        if (allRatings.isNotEmpty()) {
            parts += "⭐ Média do time: ${fmtRating("%.2f".format(allRatings.average()))}"
        }

        return EmbedField("🥇 DESTAQUES", parts.joinToString("\n\n"))
    }

    private fun craqueField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val selection = CraqueSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.MVP, matchId, name)

        val value = buildString {
            append("$name\n\n")
            append("${selection.reason}\n\n")
            append("💬 \"$phrase\"")
        }

        return EmbedField("⭐ CRAQUE DA PARTIDA", value)
    }

    private fun bagreField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val evaluation = BagrePerformanceEvaluator.evaluate(outfield, matchId, phraseBank)
            ?: return null

        return EmbedField("🍍 BAGRE DA PARTIDA", evaluation.sections.joinToString("\n\n"))
    }

    private fun xerifeField(outfield: Collection<PlayerEntry>, matchId: String): EmbedField? {
        val selection = XerifeSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.XERIFE, matchId, name)

        val value = "$name\n\n🛡️ ${selection.tacklesMade}/${selection.tackleAttempts} desarmes\n📈 Aproveitamento: ${selection.successRate}%\n\n💬 \"$phrase\""
        return EmbedField("🚧 XERIFE DA PARTIDA", value)
    }

    private fun passePrecisaoField(outfield: Collection<PlayerEntry>): EmbedField? {
        val selection = PassePrecisaoSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"

        val value = "$name\n\n📊 ${selection.passesMade}/${selection.passAttempts} passes certos\n📈 Aproveitamento: ${selection.accuracy}%"
        return EmbedField("🎯 PASSE DE PRECISÃO", value)
    }

    private fun chutouField(outfield: Collection<PlayerEntry>): EmbedField? {
        val winner = outfield
            .filter { (it.goals?.toIntOrNull() ?: 0) == 0 && (it.shots?.toIntOrNull() ?: 0) > 0 }
            .maxWithOrNull(
                compareBy<PlayerEntry> { it.shots?.toIntOrNull() ?: 0 }
                    .thenByDescending { it.playerName ?: "" }
            ) ?: return null
        val shots = winner.shots?.toIntOrNull() ?: return null
        val name = winner.playerName ?: "Desconhecido"
        return EmbedField("🎯 CHUTOU, MAS NÃO ENTROU", "$name — $shots finalizações e nenhum gol")
    }

    private fun correioField(outfield: Collection<PlayerEntry>): EmbedField? {
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
        val name = best.first.playerName ?: "Desconhecido"
        return EmbedField("📮 CORREIO EXTRAVIADO", "$name — ${best.second} passes errados")
    }

    private fun muralhaField(gk: PlayerEntry?, matchId: String): EmbedField? {
        gk ?: return null
        val saves = gk.saves?.toIntOrNull() ?: 0
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
}
