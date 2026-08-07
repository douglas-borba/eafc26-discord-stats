package com.eafc26.discordstats.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Tests for LLM prompt echo detection.
 * 
 * Validates that the system correctly identifies when an LLM returns
 * the prompt instructions instead of a proper narrative response.
 */
class PromptEchoDetectionTest {

    // Helper para testar a função privada usando reflexão
    private val isPromptEchoMethod: Method = LlmEditorialService::class.java.getDeclaredMethod("isPromptEcho", String::class.java).apply {
        isAccessible = true
    }
    
    private fun isPromptEcho(text: String, service: LlmEditorialService): Boolean {
        return isPromptEchoMethod.invoke(service, text) as Boolean
    }

    @Test
    fun `normal LLM response is accepted`() {
        val service = createService()
        val normalResponse = """
            A Associação BF atravessa momento excepcional com 9 vitórias em 10 partidas,
            evidenciando domínio ofensivo ao marcar 34 gols e solidez defensiva ao sofrer apenas 14.
            A sequência atual de 4 vitórias consecutivas consolida o time entre os favoritos da competição.
        """.trimIndent()
        
        val isEcho = isPromptEcho(normalResponse, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `response containing We need to produce is rejected`() {
        val service = createService()
        val promptEcho = """
            We need to produce 2-3 sentences, between 350 and 550 characters (including spaces).
            Must be prose, no lists, no markdown. Should not list results, just analyze trend.
            Use only given facts. Must compare the 10 matches.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response containing Must compare is rejected`() {
        val service = createService()
        val promptEcho = """
            We need to write something. Must compare the 10 matches and identify trends.
            Must use exact values provided. Should not invent data. Between 350 and 550 characters.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response containing Use exact values is rejected`() {
        val service = createService()
        val promptEcho = """
            Task: analyze the matches. Use exact values: 9 wins, 1 loss, 34 goals scored.
            Must mention that the latest match is part of totals. No lists, no markdown.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with Portuguese instructions is rejected`() {
        val service = createService()
        val promptEcho = """
            Escreva 2-3 frases entre 350 e 550 caracteres. Utilize apenas os fatos fornecidos.
            Compare as 10 partidas e identifique tendências. Não utilize markdown ou listas.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response mentioning character limits is rejected`() {
        val service = createService()
        val promptEcho = """
            Write between 120 and 220 characters in prose, no lists, no markdown, no emojis.
            Use only the given facts. Should not invent data or tactical details.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
        assertThat(isEcho).isTrue()
    }

    @Test
    fun `response with few instruction words is accepted`() {
        val service = createService()
        val borderlineResponse = """
            O time deve manter o ritmo para conquistar o título. A equipe precisa comparar
            este desempenho com campanhas anteriores e pode escrever uma nova história no campeonato.
        """.trimIndent()
        
        val isEcho = isPromptEcho(borderlineResponse, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `empty text is not prompt echo`() {
        val service = createService()
        val isEcho = isPromptEcho("", service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `single instruction word is not prompt echo`() {
        val service = createService()
        val response = "Escreva um texto sobre futebol profissional no Brasil."
        
        val isEcho = isPromptEcho(response, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `two instruction words is not prompt echo`() {
        val service = createService()
        val response = "Você deve escrever textos melhores para ganhar mais partidas."
        
        val isEcho = isPromptEcho(response, service)
        
        assertThat(isEcho).isFalse()
    }

    @Test
    fun `three or more instruction words triggers detection`() {
        val service = createService()
        val promptEcho = """
            Escreva 2-3 frases. Você deve comparar as tendências. Utilize apenas os dados fornecidos.
        """.trimIndent()
        
        val isEcho = isPromptEcho(promptEcho, service)
        
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

