package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
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
// Phrase banks
// --------------------------------------------------------------------------

private val RATING_PHRASES = listOf(
    "Nem o algoritmo conseguiu defender essa atuação.",
    "Hoje a nota chegou antes das desculpas.",
    "Foi uma atuação... memorável. Pelo motivo errado.",
    "O videogame tentou ajudar, mas desistiu.",
    "Hoje o futebol passou longe.",
    "A partida foi longa. A nota também sentiu.",
    "Não foi o pior jogo da história. Ficou perto.",
    "O técnico já está pensando em alternativas.",
    "Hoje jogou no modo difícil... para o próprio time.",
    "A torcida aplaudiu o final do jogo.",
    "Deixou tudo no vestiário. Pena que era o uniforme.",
    "Contribuiu com presença. A partida pediu mais.",
    "Estava em campo. Isso ninguém tira.",
    "Hoje fez o adversário parecer muito bom.",
    "A nota reflete com precisão o que aconteceu.",
    "Talvez a próxima partida seja diferente. Talvez.",
    "Foi uma lição. Dolorosa, mas uma lição.",
    "O campo estava difícil. A nota estava honesta.",
    "Dia de recarregar as energias.",
    "O mais importante é ter jogado. O segundo é não repetir.",
)

private val PASS_PHRASES = listOf(
    "Distribuiu o jogo... para os dois times.",
    "Hoje enxergou todo mundo com a mesma camisa.",
    "Acertou o GPS... do adversário.",
    "Treinou lançamento para o outro time.",
    "Transformou posse de bola em doação.",
    "Cada passe uma surpresa. Nem sempre boa.",
    "O adversário aproveitou cada presente.",
    "Criou chances. Para os dois lados.",
    "A bola saiu. Não pelo caminho esperado.",
    "Fez o adversário se sentir em casa.",
    "Decidiu compartilhar a bola com generosidade.",
    "O campo tinha dois times. A bola visitou os dois.",
    "Hoje o passe virou obra de arte abstrata.",
    "Contribuiu para a posse... adversária.",
    "Cada erro foi uma oportunidade. Para o outro lado.",
    "A bola encontrou o adversário antes do companheiro.",
    "Passou a bola com confiança. Para o lado errado.",
    "Ajudou a manter o jogo movimentado. Dos dois lados.",
    "O adversário ficou grato pela contribuição.",
    "Hoje o passe foi uma caixinha de surpresas.",
)

private val TACKLE_PHRASES = listOf(
    "Chegou em todas. Na bola, em quase nenhuma.",
    "Foi firme... contra o vento.",
    "Marcou presença. O adversário agradeceu.",
    "Foi para o combate sem combinar com a bola.",
    "Entrou em todos os carrinhos... atrasado.",
    "Tentou. Isso ninguém pode negar.",
    "O adversário passou. E agradeceu a passagem.",
    "Força não faltou. Precisão ficou de fora.",
    "Hoje a marcação foi mais decorativa.",
    "Mostrou disposição. A bola não correspondeu.",
    "Estava em todos os duelos. Saiu de poucos.",
    "Correu atrás da bola o jogo inteiro. Ela foi mais rápida.",
    "A intenção era boa. O resultado foi outra história.",
    "Hoje o adversário teve campo livre. Com direito a escolta.",
    "Tentou fechar os espaços. Os espaços venceram.",
    "Brigou com a bola. A bola ganhou.",
    "Deu trabalho para o adversário... entrar em campo.",
    "Hoje a marcação foi mais sugestão do que realidade.",
    "O rival passou sem maiores dificuldades. Ele viu de perto.",
    "Defendeu com o coração. A técnica foi de férias.",
)

private val SHOOTING_PHRASES = listOf(
    "Levou perigo... para a arquibancada.",
    "O goleiro terminou o jogo sem sujar o uniforme.",
    "O gol entrou em modo invisível.",
    "A pontaria pediu folga.",
    "Mudou a decoração atrás da goleira.",
    "Finalizou. A bola foi visitar outros destinos.",
    "O gol estava lá. Só não onde ele chutou.",
    "Tentou achar o ângulo. O ângulo não estava em casa.",
    "Chutou com convicção. O resultado ficou em dívida.",
    "A goleira agradeceu por não ter trabalhado.",
    "Forçou o placar a não mudar.",
    "Hoje o gol estava em reforma.",
    "A bola foi longe. Bem longe.",
    "Mostrou que a trave é mesmo redonda.",
    "O goleiro rival teve uma noite tranquila.",
    "Finalizou em dose dupla. Acertou em dose zero.",
    "Hoje o gol estava com cartão cheio.",
    "Cada finalização foi uma história. Sem final feliz.",
    "O adversário agradeceu cada chute para fora.",
    "A bola e a meta tiveram opiniões diferentes.",
)

private val MVP_PHRASES = listOf(
    "Hoje jogou de terno.",
    "Parecia que sabia o roteiro da partida.",
    "Quando o time precisou, apareceu.",
    "Craque dentro e fora das estatísticas.",
    "Hoje fez parecer fácil.",
    "Colocou o time nas costas.",
    "The controller understood the assignment.",
    "Hoje o futebol sorriu para ele.",
    "Fez o que precisava ser feito, quando precisava.",
    "Diferença entre tentar e conseguir? Ele mostrou.",
    "Hoje foi o dono do campo.",
    "O adversário tentou, mas não encontrou resposta.",
    "Quando a pressão aumentou, ele cresceu junto.",
    "Cada toque tinha intenção. Cada intenção tinha resultado.",
    "Hoje a partida passou pelas suas mãos.",
    "Transformou dificuldade em oportunidade.",
    "O time jogou bem. Ele jogou melhor.",
    "Hoje a diferença tinha nome.",
    "Fez o adversário pensar duas vezes. Depois três.",
    "Jogou como se não houvesse segunda chance. Não precisou.",
    "Hoje ele foi o manual de como se joga futebol.",
    "Lembrou a todos por que é craque.",
)

private val XERIFE_PHRASES = listOf(
    "Hoje ninguém passou sem autorização.",
    "Fechou a porta e jogou a chave fora.",
    "O adversário ainda procura espaço.",
    "Cobrou pedágio em cada ataque.",
    "Hoje mandou na defesa.",
    "Cada carrinho, uma sentença.",
    "O rival tentou. Ele não deixou.",
    "Hoje foi o seguro do time.",
    "Brigou por cada centímetro com sucesso.",
    "Onde estava, o adversário não passou.",
    "Foi o controlador de tráfego da defesa.",
    "Hoje a marcação foi cirúrgica.",
    "Nenhum ataque passou sem pagar o preço.",
    "A defesa tinha um nome: ele.",
    "O adversário mudou de rota. Ele mudou junto.",
    "Hoje a bola voltou mais do que avançou.",
    "Fez a marcação parecer arte.",
    "Cada duelo era uma batalha. Ele venceu a guerra.",
    "Hoje o meio-campo adversário foi turismo.",
    "A defesa agradeceu a presença.",
    "Fechou os espaços como se soubesse o que estava vindo.",
    "Hoje ele foi o seguro do time. Sem franquia.",
)

private val GOALKEEPER_PHRASES = listOf(
    "Hoje fechou o gol.",
    "Quando precisou aparecer, apareceu.",
    "Nem GPS encontrou espaço.",
    "A última palavra quase sempre foi dele.",
    "Foi buscar até o que parecia perdido.",
    "Hoje a luva trabalhou em hora extra.",
    "O placar deveria ter sido pior. Ele não deixou.",
    "Cada defesa, uma afirmação.",
    "Hoje o gol ficou sob custódia.",
    "Os chutes vieram. Ele devolveu.",
    "Fez o adversário acreditar que era difícil marcar. Era.",
    "Hoje ele foi o plano B, C e D do time.",
    "Quando a defesa falhou, ele não falhou.",
    "O gol ficou em boas mãos. Literalmente.",
    "Hoje o goleiro foi o melhor jogador do campo.",
    "Segurou o que tinha que segurar e mais um pouco.",
    "O adversário chutou muito. Entrou pouco.",
    "Hoje foi difícil fazer gol. Por causa dele.",
    "A trave e ele foram parceiros hoje.",
    "Partida difícil. Saída fácil: defensiva e segura.",
)

// --------------------------------------------------------------------------
// Builder
// --------------------------------------------------------------------------

private val PT_BR = Locale("pt", "BR")
private val DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy '•' HH:mm", PT_BR)

private const val COLOR_WIN  = 0x2ECC71
private const val COLOR_DRAW = 0x95A5A6
private const val COLOR_LOSS = 0xE74C3C

private val SEPARATOR = EmbedField("​", "──────────────────────────────")

object DiscordEmbedBuilder {

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

        val allActive = (match.players[ourClubId] ?: emptyMap())
            .values
            .filter { (it.secondsPlayed?.toIntOrNull() ?: 1) > 0 }

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
                parts += "${medals[i]} $name — Nota ${fmtRating(p.rating)}\n\n⭐ Craque da Partida\n\n💬 \"${pickPhrase(MVP_PHRASES, matchId, name)}\""
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

        sections += "$name — Nota ${fmtRating(bagre.rating)}\n\n💬 \"${pickPhrase(RATING_PHRASES, matchId, name)}\""

        val passAttempts = bagre.passAttempts?.toIntOrNull() ?: 0
        val passesMade   = bagre.passesMade?.toIntOrNull() ?: 0
        if (passAttempts > 0) {
            val pct = passesMade * 100 / passAttempts
            val phrase = pickPhrase(PASS_PHRASES, matchId, "${name}passes")
            sections += "📉 Passes\n\n• $passesMade/$passAttempts certos ($pct%)\n• ${passAttempts - passesMade} passes errados\n\n💬 \"$phrase\""
        }

        val tackleAttempts = bagre.tackleAttempts?.toIntOrNull() ?: 0
        val tacklesMade    = bagre.tacklesMade?.toIntOrNull() ?: 0
        if (tackleAttempts > 0) {
            val pct = tacklesMade * 100 / tackleAttempts
            val phrase = pickPhrase(TACKLE_PHRASES, matchId, "${name}desarmes")
            sections += "🛡️ Desarmes\n\n• $tacklesMade/$tackleAttempts certos ($pct%)\n• ${tackleAttempts - tacklesMade} tentativas perdidas\n\n💬 \"$phrase\""
        }

        val shots = bagre.shots?.toIntOrNull() ?: 0
        if (shots > 0) {
            val phrase = pickPhrase(SHOOTING_PHRASES, matchId, "${name}chutes")
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
        val phrase         = pickPhrase(XERIFE_PHRASES, matchId, name)

        val value = "$name\n\n🛡️ $tacklesMade/$tackleAttempts desarmes\n📈 Aproveitamento: $pct%\n\n💬 \"$phrase\""
        return EmbedField("🚧 XERIFE DA PARTIDA", value)
    }

    private fun muralhaField(gk: PlayerEntry?, matchId: String): EmbedField? {
        gk ?: return null
        val saves  = gk.saves?.toIntOrNull() ?: 0
        val phrase = pickPhrase(GOALKEEPER_PHRASES, matchId, gk.playerName ?: "goleiro")
        return EmbedField("🧤 MURALHA DA PARTIDA", "🧤 Defesas realizadas: $saves\n\n💬 \"$phrase\"")
    }

    // -- Helpers -----------------------------------------------------------

    private fun pickPhrase(phrases: List<String>, matchId: String, seed: String): String {
        val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
        return phrases[(abs(hash) % phrases.size).toInt()]
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
