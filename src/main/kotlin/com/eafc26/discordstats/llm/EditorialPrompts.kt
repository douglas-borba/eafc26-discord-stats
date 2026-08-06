package com.eafc26.discordstats.llm

import com.eafc26.discordstats.domain.interpretation.MatchOutcome

object EditorialPrompts {

    private const val DISCORD_SYSTEM = """Você é um redator esportivo brasileiro de EA FC Pro Clubs.
Seu trabalho é escrever uma breve introdução editorial sobre UMA PARTIDA específica que acabou de acontecer.
Regras: use apenas fatos fornecidos; nunca invente dados; escreva em prosa corrida sem listas, markdown ou formatação; responda apenas com o texto narrativo curto."""

    private const val PANORAMA_SYSTEM = """Você é um redator esportivo brasileiro de EA FC Pro Clubs.
Seu trabalho é escrever uma abertura de matéria analisando o MOMENTO ATUAL do clube com base nas últimas dez partidas.
Regras: use apenas fatos fornecidos; nunca invente dados; escreva em prosa corrida sem listas, markdown ou formatação; responda apenas com o texto narrativo."""

    fun matchNarrativePrompt(context: EditorialContext): Pair<String, String> {
        val m = context.match
        val userPrompt = buildString {
            append("CONTEXTO: Introdução editorial para publicação no Discord sobre esta partida específica.\n\n")
            append("PARTIDA: ${outcomeLabel(m.outcome)} — ${m.ourClub} ${m.ourScore}×${m.opponentScore} ${m.opponent}\n\n")
            append("DADOS DESTA PARTIDA:\n")
            append("- Nota média do time: ${m.teamAverageRating ?: "N/A"}\n")
            append("- Gols marcados: ${m.ourScore}\n")
            append("- Gols sofridos: ${m.opponentScore}\n")
            
            m.goalkeeper?.let { append("- Defesas do goleiro: ${it.saves}\n") }
            
            context.recentForm?.let { 
                append("\nCONTEXTO SECUNDÁRIO (últimas partidas):\n")
                append("- Sequência recente: ${it.streak}\n")
                append("- Balanço recente: ${it.wins}V ${it.draws}E ${it.losses}D\n")
            }
            
            append("\nTAREFA:\n")
            append("Escreva 1-2 frases (120-220 caracteres) descrevendo APENAS esta partida.\n")
            append("Texto corrido em português brasileiro, sem emoji ou markdown.\n")
            append("O foco DEVE ser o desempenho nesta partida específica.\n")
            append("O contexto recente pode ser mencionado apenas como comentário secundário.\n\n")
            
            append("VOCÊ PODE discutir sobre ESTA partida:\n")
            append("- Se o resultado foi positivo, negativo ou equilibrado\n")
            append("- Eficiência ofensiva baseada nos gols marcados nesta partida\n")
            append("- Vulnerabilidade defensiva baseada nos gols sofridos nesta partida\n")
            append("- Contraste entre produção ofensiva e resultado defensivo desta partida\n")
            append("- Nível geral de desempenho baseado na nota média do time nesta partida\n")
            append("- Se o resultado DESTA partida estende, interrompe ou muda uma sequência recente\n\n")
            
            append("VOCÊ NÃO PODE afirmar:\n")
            append("- Controle de jogo ou posse de bola\n")
            append("- Domínio ou superioridade\n")
            append("- Reação ou virada durante o jogo\n")
            append("- Mudanças de ritmo ou momentum\n")
            append("- Quantidade de chances criadas\n")
            append("- Aspectos táticos ou técnicos\n")
            append("- Divisão do jogo em tempos ou fases\n")
            append("- Qualidade defensiva só por defesas do goleiro\n\n")
            
            append("NUNCA repita informações já exibidas:\n")
            append("- Placar exato\n")
            append("- Nomes de goleadores ou assistentes\n")
            append("- Nome do MVP ou craque\n")
            append("- Notas individuais de jogadores\n")
            append("- Estatísticas já exibidas abaixo")
        }
        return DISCORD_SYSTEM to userPrompt
    }

    fun panoramaPrompt(context: EditorialContext): Pair<String, String> {
        val m = context.match
        val userPrompt = buildString {
            append("CONTEXTO: Abertura de matéria sobre o MOMENTO ATUAL do clube baseado nas últimas 10 partidas.\n\n")
            append("PARTIDA MAIS RECENTE:\n")
            append("${m.ourClub} ${m.ourScore}×${m.opponentScore} ${m.opponent} — ${outcomeLabel(m.outcome)} (${m.date})\n")
            m.mvp?.let { 
                append("Craque: ${it.name}")
                it.rating?.let { r -> append(" ($r)") }
                append("\n")
            }
            m.teamAverageRating?.let { append("Média do time: $it\n") }
            
            context.recentForm?.let { form ->
                append("\nSEQUÊNCIA DAS ÚLTIMAS ${form.results.size} PARTIDAS:\n")
                form.results.forEach { r ->
                    append("${outcomeLabel(r.outcome)}: ${r.ourScore}×${r.opponentScore} vs ${r.opponent}\n")
                }
                append("\nBALANÇO GERAL:\n")
                append("Resultados: ${form.wins}V ${form.draws}E ${form.losses}D\n")
                append("Gols: ${form.goalsScored} marcados, ${form.goalsConceded} sofridos\n")
                append("Sequência: ${form.streak}\n")
            }
            
            if (m.goals.isNotEmpty()) {
                append("\nARTILHEIROS RECENTES:\n")
                append(m.goals.joinToString(", ") { "${it.name} (${it.goals})" })
                append("\n")
            }
            
            append("\nTAREFA:\n")
            append("Escreva 2-3 frases (entre 350 e 550 caracteres) analisando o MOMENTO ATUAL do clube.\n")
            append("Texto corrido em português brasileiro, sem listas, emoji ou markdown.\n\n")
            
            append("VOCÊ DEVE:\n")
            append("- Comparar as 10 partidas e identificar tendências evidentes\n")
            append("- Discutir consistência ou inconsistência entre as partidas\n")
            append("- Avaliar produção ofensiva considerando os gols nas 10 partidas\n")
            append("- Avaliar solidez defensiva considerando os gols sofridos nas 10 partidas\n")
            append("- Comentar se há evolução, deterioração ou estabilidade entre as partidas\n")
            append("- Caracterizar a forma atual do clube com base em fatos observáveis\n\n")
            
            append("FOCO:\n")
            append("Este NÃO é um resumo da última partida.\n")
            append("Este é uma análise do momento do clube através das últimas 10 partidas.\n")
            append("A partida mais recente pode ter ênfase, mas o texto deve comparar e analisar a sequência.\n")
            append("Narre a tendência geral baseada em evidências, não liste resultados individuais.")
        }
        return PANORAMA_SYSTEM to userPrompt
    }

    private fun outcomeLabel(outcome: MatchOutcome): String = when (outcome) {
        MatchOutcome.WIN -> "Vitória"
        MatchOutcome.DRAW -> "Empate"
        MatchOutcome.LOSS -> "Derrota"
    }
}
