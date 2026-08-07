package com.eafc26.discordstats.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Tests for semantic validation of LLM panorama responses.
 * 
 * Validates that the system correctly identifies:
 * - Valid editorial panoramas (in Portuguese, with club analysis)
 * - Invalid responses (prompts, instructions, format restrictions)
 */
class PromptEchoDetectionTest {

    private val isPromptEchoMethod: Method = LlmEditorialService::class.java.getDeclaredMethod("isPromptEcho", String::class.java).apply {
        isAccessible = true
    }
    
    private fun isPromptEcho(text: String, service: LlmEditorialService): Boolean {
        return isPromptEchoMethod.invoke(service, text) as Boolean
    }

    @Test
    fun `valid Portuguese editorial panorama is accepted`() {
        val service = createService()
        val validPanorama = """
            A Associação BF atravessa momento excepcional com 9 vitórias em 10 partidas,
            evidenciando domínio ofensivo ao marcar 34 gols e solidez defensiva ao sofrer apenas 14.
            A sequência atual de 4 vitórias consecutivas consolida o time entre os favoritos da competição.
        """.trimIndent()
        
        val isEcho = isPromptEcho(validPanorama, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `response in English with instructions is rejected`() {
        val service = createService()
        val englishInstructions = """
            We need to produce 2-3 sentences, between 350 and 550 characters (including spaces).
            Must be prose, no lists, no markdown. Should not list results, just analyze trend.
            Use only given facts. Must compare the 10 matches.
        """.trimIndent()
        
        val isEcho = isPromptEcho(englishInstructions, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with format restrictions is rejected`() {
        val service = createService()
        val formatRestrictions = """
            Write between 350 and 550 characters in prose format.
            No lists, no markdown, no emojis. Should not invent data.
        """.trimIndent()
        
        val isEcho = isPromptEcho(formatRestrictions, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with Portuguese format restrictions is rejected`() {
        val service = createService()
        val portugueseRestrictions = """
            Escreva entre 350 e 550 caracteres. Sem listas, sem markdown, sem emoji.
            Você deve comparar as 10 partidas e utilizar apenas os dados fornecidos.
        """.trimIndent()
        
        val isEcho = isPromptEcho(portugueseRestrictions, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with model instructions is rejected`() {
        val service = createService()
        val modelInstructions = """
            Task: analyze the matches. Use exact values: 9 wins, 1 loss, 34 goals scored.
            Must mention that the latest match is part of totals. Should not use markdown.
        """.trimIndent()
        
        val isEcho = isPromptEcho(modelInstructions, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `valid Portuguese analysis with natural language is accepted`() {
        val service = createService()
        val naturalAnalysis = """
            O clube apresenta desempenho irregular nas últimas partidas, com 5 vitórias e 5 derrotas.
            A equipe marcou 18 gols mas sofreu 20, evidenciando fragilidade defensiva que tem custado pontos importantes.
            O momento exige ajustes para recuperar a solidez e buscar uma sequência de resultados positivos.
        """.trimIndent()
        
        val isEcho = isPromptEcho(naturalAnalysis, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `response lacking Portuguese indicators is rejected`() {
        val service = createService()
        val noPortuguese = """
            The team shows good performance with many victories.
            Analysis of recent matches reveals strong offensive play.
        """.trimIndent()
        
        val isEcho = isPromptEcho(noPortuguese, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response lacking analysis keywords is rejected`() {
        val service = createService()
        val noAnalysis = """
            O texto está em português e tem várias palavras.
            Mas não contém análise do clube ou desempenho esportivo.
        """.trimIndent()
        
        val isEcho = isPromptEcho(noAnalysis, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with prompt patterns is rejected`() {
        val service = createService()
        val promptPatterns = """
            Tarefa: escreva um panorama. Você deve comparar as partidas.
            Analise os dados fornecidos e identifique tendências.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptPatterns, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response resembling instruction list is rejected`() {
        val service = createService()
        val instructionList = """
            Escreva texto. Compare. Analise. Use dados. Não invente. Seja breve.
            Liste tendências. Evite listas. Sem markdown. Entre 350-550 chars.
        """.trimIndent()
        
        val isEcho = isPromptEcho(instructionList, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `valid panorama with club performance analysis is accepted`() {
        val service = createService()
        val validAnalysis = """
            A campanha recente revela inconsistência, alternando vitórias expressivas com derrotas preocupantes.
            O ataque produziu 25 gols em 10 partidas, mas a defesa sofreu 18, comprometendo o aproveitamento.
            A equipe busca estabilidade para consolidar posição na tabela e retomar a confiança.
        """.trimIndent()
        
        val isEcho = isPromptEcho(validAnalysis, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `empty text is rejected as invalid`() {
        val service = createService()
        val isEcho = isPromptEcho("", service)
        
        assertThat(isEcho).isTrue()
    }

    private fun createService(): LlmEditorialService {
        val contextBuilder = org.mockito.kotlin.mock<EditorialContextBuilder>()
        val historyService = org.mockito.kotlin.mock<com.eafc26.discordstats.service.MatchHistoryService>()
        val properties = LlmProperties(enabled = true)
        
        return LlmEditorialService(
            contextBuilder = contextBuilder,
            provider = null,
            historyService = historyService,
            properties = properties,
            panoramaRepository = null,
        )
    }
}

