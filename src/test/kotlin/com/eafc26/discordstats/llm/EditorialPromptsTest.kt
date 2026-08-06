package com.eafc26.discordstats.llm

import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for editorial prompt structure and content guidelines.
 *
 * These tests verify that prompts explicitly instruct the LLM to:
 * - Avoid repeating deterministic fields (scorers, MVP, ratings)
 * - Focus on complementary analysis (control, momentum, behavior)
 * - Respect character limits and format requirements
 *
 * CRITICAL SEPARATION:
 * - Discord prompt: focuses on ONE match only
 * - Panorama prompt: analyzes THREE-match trend
 */
class EditorialPromptsTest {

    // =============================================================================
    // SYSTEM PROMPT TESTS - Proving different system prompts for different products
    // =============================================================================

    @Test
    fun `discord and panorama have different system prompts`() {
        val context = sampleContext()
        val (discordSystem, _) = EditorialPrompts.matchNarrativePrompt(context)
        val (panoramaSystem, _) = EditorialPrompts.panoramaPrompt(context)

        assertThat(discordSystem).isNotEqualTo(panoramaSystem)
    }

    @Test
    fun `discord system prompt specifies one match focus`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(systemPrompt).containsIgnoringCase("UMA PARTIDA")
        assertThat(systemPrompt).containsAnyOf("acabou de acontecer", "específica")
    }

    @Test
    fun `panorama system prompt specifies moment analysis`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.panoramaPrompt(context)

        assertThat(systemPrompt).containsIgnoringCase("MOMENTO ATUAL")
        assertThat(systemPrompt).contains("últimas dez")
    }

    @Test
    fun `discord system prompt mentions short editorial`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(systemPrompt).containsAnyOf("breve", "curto")
    }

    @Test
    fun `panorama system prompt mentions article opening`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.panoramaPrompt(context)

        assertThat(systemPrompt).containsIgnoringCase("abertura de matéria")
    }

    // =============================================================================
    // DISCORD PROMPT TESTS - Proving single match focus
    // =============================================================================

    @Test
    fun `discord prompt explicitly states it focuses on this specific match`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).containsAnyOf("APENAS esta partida", "partida específica", "DESTA partida")
    }

    @Test
    fun `discord prompt labels recent form as secondary context`() {
        val context = sampleContext(recentStreak = "3 vitórias seguidas")
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("CONTEXTO SECUNDÁRIO")
        assertThat(userPrompt).containsAnyOf("comentário secundário", "secundário")
    }

    @Test
    fun `discord prompt emphasizes current match data section`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("DADOS DESTA PARTIDA")
    }

    @Test
    fun `discord prompt never asks for three-match panorama`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt.lowercase()).doesNotContain("três partidas")
        assertThat(userPrompt.lowercase()).doesNotContain("3 partidas")
        assertThat(userPrompt.lowercase()).doesNotContain("últimas partidas")
        assertThat(userPrompt.lowercase()).doesNotContain("sequência de")
    }

    @Test
    fun `discord prompt specifies this match in allowed topics`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("gols marcados nesta partida")
        assertThat(userPrompt).contains("gols sofridos nesta partida")
        assertThat(userPrompt).contains("nota média do time nesta partida")
    }

    // =============================================================================
    // PANORAMA PROMPT TESTS - Proving three-match trend focus
    // =============================================================================

    @Test
    fun `panorama prompt explicitly analyzes latest three matches`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        // Prompt now references dynamic match count from context, not hardcoded 3
        assertThat(userPrompt).containsAnyOf("ÚLTIMAS ${context.recentForm?.results?.size ?: 0} PARTIDAS", "últimas 10 partidas", "últimas dez partidas")
    }

    @Test
    fun `panorama prompt requires comparing matches`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("Comparar as 10 partidas")
        assertThat(userPrompt).containsAnyOf("tendências", "comparar")
    }

    @Test
    fun `panorama prompt explicitly states it is not a single match recap`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("NÃO é um resumo da última partida")
    }

    @Test
    fun `panorama prompt asks for consistency analysis across matches`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("consistência ou inconsistência entre as partidas")
    }

    @Test
    fun `panorama prompt asks for evolution between matches`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).containsAnyOf("evolução, deterioração ou estabilidade", "entre as partidas")
    }

    @Test
    fun `panorama prompt asks for aggregate offensive production`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        // Now expects 10 partidas
        assertThat(userPrompt).contains("gols nas 10 partidas")
    }

    @Test
    fun `panorama prompt asks for aggregate defensive production`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("gols sofridos nas 10 partidas")
    }

    @Test
    fun `panorama prompt asks for club's current form characterization`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("forma atual do clube")
    }

    @Test
    fun `panorama prompt includes sequence section with multiple matches`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        // Now uses dynamic match count from context
        assertThat(userPrompt).contains("SEQUÊNCIA DAS ÚLTIMAS")
    }

    @Test
    fun `panorama prompt specifies moment analysis not result listing`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("análise do momento do clube")
        assertThat(userPrompt).containsAnyOf("não liste resultados individuais", "últimas 10 partidas")
    }

    // =============================================================================
    // ORIGINAL DISCORD TESTS (maintained for backward compatibility)
    // =============================================================================

    @Test
    fun `match narrative prompt explicitly forbids repeating score`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("NUNCA repita")
        assertThat(userPrompt).contains("Placar exato")
    }

    @Test
    fun `match narrative prompt explicitly forbids repeating scorer names`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Nomes de goleadores")
        assertThat(userPrompt).containsIgnoringCase("assistentes")
    }

    @Test
    fun `match narrative prompt explicitly forbids repeating MVP name`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).containsAnyOf("MVP", "craque")
    }

    @Test
    fun `match narrative prompt explicitly forbids repeating player ratings`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Notas individuais de jogadores")
    }

    @Test
    fun `match narrative prompt explicitly forbids repeating exact statistics`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Estatísticas já exibidas")
    }

    @Test
    fun `match narrative prompt specifies character limit 120-220`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("120-220 caracteres")
    }

    @Test
    fun `match narrative prompt requires 1-2 sentences`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("1-2 frases")
    }

    @Test
    fun `match narrative prompt forbids emoji and markdown`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("sem emoji ou markdown")
    }

    @Test
    fun `match narrative prompt forbids inventing tactical events`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("NÃO PODE afirmar")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming match control`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Controle de jogo")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming dominance`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Domínio ou superioridade")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming reaction`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Reação ou virada durante o jogo")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming momentum changes`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Mudanças de ritmo ou momentum")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming chances created`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Quantidade de chances criadas")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming tactical superiority`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Aspectos táticos ou técnicos")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming match phases`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Divisão do jogo em tempos ou fases")
    }

    @Test
    fun `match narrative prompt explicitly forbids claiming defensive solidity from saves alone`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Qualidade defensiva só por defesas do goleiro")
    }

    @Test
    fun `match narrative prompt allows discussing result positivity`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Se o resultado foi positivo, negativo ou equilibrado")
    }

    @Test
    fun `match narrative prompt allows discussing offensive efficiency`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Eficiência ofensiva baseada nos gols marcados")
    }

    @Test
    fun `match narrative prompt allows discussing defensive vulnerability`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Vulnerabilidade defensiva baseada nos gols sofridos")
    }

    @Test
    fun `match narrative prompt allows discussing attack vs defense contrast`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Contraste entre produção ofensiva e resultado defensivo")
    }

    @Test
    fun `match narrative prompt allows discussing recent form consistency`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        // Note: uses different wording now that recent form is secondary context
        assertThat(userPrompt).doesNotContain("Consistência ou instabilidade baseada nos resultados recentes")
    }

    @Test
    fun `match narrative prompt allows discussing sequence changes`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Se o resultado DESTA partida estende, interrompe ou muda uma sequência")
    }

    @Test
    fun `match narrative prompt allows discussing performance level from team rating`() {
        val context = sampleContext()
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Nível geral de desempenho baseado na nota média do time")
    }

    @Test
    fun `match narrative prompt provides contextual data without player names`() {
        val context = sampleContext(
            mvpName = "Striker99",
            worstName = "Defender4",
            goalScorers = listOf(ScorerContext("Striker99", 2), ScorerContext("Midfielder7", 1))
        )
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        // Prompt should NOT include MVP or scorer names in the context section
        assertThat(userPrompt).doesNotContain("Striker99")
        assertThat(userPrompt).doesNotContain("Midfielder7")
        assertThat(userPrompt).doesNotContain("Defender4")
    }

    @Test
    fun `match narrative prompt includes aggregate metrics for inference`() {
        val context = sampleContext(ourScore = 4, opponentScore = 2, teamAvgRating = "7.8", goalkeperSaves = 3)
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        // Should provide aggregate data for LLM to use
        assertThat(userPrompt).contains("Nota média do time: 7.8")
        assertThat(userPrompt).contains("Gols marcados: 4")
        assertThat(userPrompt).contains("Gols sofridos: 2")
        assertThat(userPrompt).contains("Defesas do goleiro: 3")
    }

    @Test
    fun `match narrative prompt includes recent form when available`() {
        val context = sampleContext(recentStreak = "3 vitórias seguidas")
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).contains("Sequência recente: 3 vitórias seguidas")
    }

    @Test
    fun `match narrative prompt does not include recent form when unavailable`() {
        val context = sampleContext(recentStreak = null)
        val (_, userPrompt) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(userPrompt).doesNotContain("Sequência recente")
    }

    @Test
    fun `system prompt establishes Brazilian sports writer role`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(systemPrompt).containsIgnoringCase("brasileiro")
        assertThat(systemPrompt).containsIgnoringCase("EA FC")
        assertThat(systemPrompt).containsIgnoringCase("Pro Clubs")
    }

    @Test
    fun `system prompt forbids inventing data`() {
        val context = sampleContext()
        val (systemPrompt, _) = EditorialPrompts.matchNarrativePrompt(context)

        assertThat(systemPrompt).contains("nunca invente dados")
        assertThat(systemPrompt).contains("use apenas fatos fornecidos")
    }

    // =============================================================================
    // PANORAMA FORMAT TESTS
    // =============================================================================

    @Test
    fun `panorama prompt specifies 450 character limit`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        // Now expects 350-550 character range
        assertThat(userPrompt).contains("entre 350 e 550 caracteres")
    }

    @Test
    fun `panorama prompt requires 2-3 sentences`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("2-3 frases")
    }

    @Test
    fun `panorama prompt forbids lists and markdown`() {
        val context = sampleContextWithThreeMatches()
        val (_, userPrompt) = EditorialPrompts.panoramaPrompt(context)

        assertThat(userPrompt).contains("sem listas, emoji ou markdown")
    }

    // Helper function to create test context with single match focus
    private fun sampleContext(
        ourScore: Int = 3,
        opponentScore: Int = 1,
        outcome: MatchOutcome = MatchOutcome.WIN,
        mvpName: String? = null,
        worstName: String? = null,
        teamAvgRating: String? = "7.5",
        goalkeperSaves: Int? = null,
        recentStreak: String? = null,
        goalScorers: List<ScorerContext> = emptyList(),
    ): EditorialContext {
        return EditorialContext(
            match = MatchContext(
                ourClub = "Associação BF",
                opponent = "Rival FC",
                ourScore = ourScore,
                opponentScore = opponentScore,
                outcome = outcome,
                date = "05/08/2026",
                mvp = mvpName?.let { PlayerHighlight(it, "8.5", "Dominância ofensiva") },
                worstPerformer = worstName?.let { PlayerHighlight(it, "5.5", null) },
                xerife = null,
                goalkeeper = goalkeperSaves?.let { GoalkeeperHighlight("GK Bot", it, opponentScore, "Muralha") },
                goals = goalScorers,
                assists = emptyList(),
                teamAverageRating = teamAvgRating,
                notableEvents = emptyList(),
            ),
            recentForm = recentStreak?.let {
                RecentFormContext(
                    results = emptyList(),
                    wins = 3,
                    draws = 0,
                    losses = 0,
                    goalsScored = 9,
                    goalsConceded = 2,
                    streak = it,
                )
            }
        )
    }

    // Helper function to create test context with three-match sequence
    private fun sampleContextWithThreeMatches(): EditorialContext {
        return EditorialContext(
            match = MatchContext(
                ourClub = "Associação BF",
                opponent = "Rival FC",
                ourScore = 3,
                opponentScore = 1,
                outcome = MatchOutcome.WIN,
                date = "05/08/2026",
                mvp = PlayerHighlight("Striker99", "8.5", "Dominância ofensiva"),
                worstPerformer = null,
                xerife = null,
                goalkeeper = GoalkeeperHighlight("GK Bot", 4, 1, "Muralha"),
                goals = listOf(ScorerContext("Striker99", 2), ScorerContext("Midfielder7", 1)),
                assists = emptyList(),
                teamAverageRating = "7.8",
                notableEvents = emptyList(),
            ),
            recentForm = RecentFormContext(
                results = listOf(
                    RecentMatchResult("Team A", 2, 1, MatchOutcome.WIN),
                    RecentMatchResult("Team B", 1, 1, MatchOutcome.DRAW),
                    RecentMatchResult("Team C", 3, 1, MatchOutcome.WIN),
                ),
                wins = 2,
                draws = 1,
                losses = 0,
                goalsScored = 6,
                goalsConceded = 3,
                streak = "invicto há 3 jogos",
            )
        )
    }
}




