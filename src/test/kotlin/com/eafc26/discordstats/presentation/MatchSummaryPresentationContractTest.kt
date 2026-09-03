package com.eafc26.discordstats.presentation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MatchSummaryPresentationContractTest {
    @Test
    fun `missing negative recognition title keeps historical Bagre presentation compatible`() {
        val json = """{
          "name":"Jogador","rating":"6,80","reason":"Nota abaixo","tackleStats":null,
          "passStats":null,"phrase":"Frase"
        }"""

        val section = jacksonObjectMapper().readValue(json, BagreSection::class.java)

        assertThat(section.title).isEqualTo("🍍 BAGRE DA PARTIDA")
    }

    @Test
    fun `missing allPlayers remains null for payloads created before the field existed`() {
        val json = """{
          "ourName":"A","oppName":"B","ourScore":0,"oppScore":0,
          "outcome":{"emoji":"","label":"Empate","color":0,"type":"DRAW"},
          "date":"2026-01-01","timestamp":"2026-01-01T00:00:00Z","matchId":"old",
          "goals":null,"assists":null,"highlights":null,"craque":null,
          "offensiveNarratives":[],"bagre":null,"redCard":null,"xerife":null,
          "passePrecisao":null,"correioExtraviado":null,"muralha":null
        }"""

        val presentation = jacksonObjectMapper().readValue(json, MatchSummaryPresentation::class.java)

        assertThat(presentation.allPlayers).isNull()
    }
}
