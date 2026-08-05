package com.eafc26.discordstats.llm

import com.eafc26.discordstats.domain.interpretation.MatchOutcome

object EditorialPrompts {

    private const val SYSTEM = """Você é um redator esportivo brasileiro de EA FC Pro Clubs.
Regras: use apenas fatos fornecidos; nunca invente dados; escreva em prosa corrida sem listas, markdown ou formatação; responda apenas com o texto narrativo."""

    fun matchNarrativePrompt(context: EditorialContext): Pair<String, String> {
        val m = context.match
        val userPrompt = buildString {
            append("${m.ourClub} ${m.ourScore}×${m.opponentScore} ${m.opponent} — ${outcomeLabel(m.outcome)} (${m.date})\n")
            m.mvp?.let {
                append("Craque: ${it.name}")
                it.rating?.let { r -> append(" ($r)") }
                it.detail?.takeIf { d -> d.isNotBlank() }?.let { d -> append(" — $d") }
                append("\n")
            }
            m.worstPerformer?.let {
                append("Pior: ${it.name}")
                it.rating?.let { r -> append(" ($r)") }
                append("\n")
            }
            m.goalkeeper?.let { append("Goleiro: ${it.name} (${it.archetype}, ${it.saves} defesas)\n") }
            if (m.goals.isNotEmpty()) append("Gols: ${m.goals.joinToString { "${it.name}(${it.goals})" }}\n")
            context.recentForm?.let { append("Sequência: ${it.streak}\n") }
            append("\n1-2 frases, máx 250 caracteres. Tom editorial. Não repita o placar.")
        }
        return SYSTEM to userPrompt
    }

    fun panoramaPrompt(context: EditorialContext): Pair<String, String> {
        val m = context.match
        val userPrompt = buildString {
            append("Última: ${m.ourClub} ${m.ourScore}×${m.opponentScore} ${m.opponent} — ${outcomeLabel(m.outcome)} (${m.date})\n")
            m.mvp?.let { append("Craque: ${it.name}") ; it.rating?.let { r -> append(" ($r)") } ; append("\n") }
            m.teamAverageRating?.let { append("Média: $it\n") }
            context.recentForm?.let { form ->
                form.results.forEach { r ->
                    append("${outcomeLabel(r.outcome)}: ${r.ourScore}×${r.opponentScore} vs ${r.opponent}\n")
                }
                append("${form.wins}V ${form.draws}E ${form.losses}D — ${form.goalsScored} feitos, ${form.goalsConceded} sofridos\n")
                append("Sequência: ${form.streak}\n")
            }
            if (m.goals.isNotEmpty()) append("Artilheiros: ${m.goals.joinToString { "${it.name}(${it.goals})" }}\n")
            append("\n2-3 frases, máx 450 caracteres. Abertura de matéria esportiva. Narre tendência e sequência, não liste resultados.")
        }
        return SYSTEM to userPrompt
    }

    private fun outcomeLabel(outcome: MatchOutcome): String = when (outcome) {
        MatchOutcome.WIN -> "Vitória"
        MatchOutcome.DRAW -> "Empate"
        MatchOutcome.LOSS -> "Derrota"
    }
}
