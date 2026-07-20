package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CorreioExtraviadoSelectorTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun p(
        name: String,
        attempts: Int,
        made: Int,
        secondsPlayed: Int = 900,
    ) = PlayerEntry(
        playerName    = name,
        passAttempts  = attempts.toString(),
        passesMade    = made.toString(),
        secondsPlayed = secondsPlayed.toString(),
        rating        = "7.0",
    )

    // ── 1. Meia criativo não é penalizado ────────────────────────────────────

    @Test
    fun `meia criativo com muitos erros mas acerto proximo da media nao e selecionado`() {
        // Meia10: 28/40 = 70%; Def1: 14/20 = 70%; Def2: 15/20 = 75%
        // team = 57/80 = 71%; Meia10 delta = 1pp < MIN_DELTA_PCT → not eligible
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Meia10",    40, 28),
            p("Defensor1", 20, 14),
            p("Defensor2", 20, 15),
        ))
        assertThat(result).isNull()
    }

    @Test
    fun `meia com mais erros absolutos nao e selecionado quando outro jogador esta mais abaixo`() {
        // Meia10: 30/40 = 75% → at threshold, excluded (not strictly below)
        // Errou: 4/20 = 20%; Bom: 18/20 = 90%; team = 52/80 = 65%; Errou delta = 45pp
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Meia10", 40, 30),
            p("Errou",  20, 4),
            p("Bom",    20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Errou")
    }

    // ── 2. Jogador realmente abaixo da média é escolhido (alto volume) ────────

    @Test
    fun `seleciona jogador significativamente abaixo da media do time`() {
        // Torto 40%, Certeiro 90%, team 65%; delta = 25pp
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Torto",    20, 8),
            p("Certeiro", 20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Torto")
        assertThat(result.playerAccuracyPct).isEqualTo(40)
        assertThat(result.teamAccuracyPct).isEqualTo(65)
        assertThat(result.deltaPct).isEqualTo(25)
    }

    @Test
    fun `seleciona o pior entre varios candidatos elegiveis de alto volume`() {
        // Pior 25%, Ruim 50%, Bom 90%; team = 33/60 = 55%; Pior 30pp, Ruim 5pp below
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Pior", 20, 5),
            p("Ruim", 20, 10),
            p("Bom",  20, 18),
        ))
        assertThat(result!!.player.playerName).isEqualTo("Pior")
    }

    @Test
    fun `campos de acerto e delta estao corretos`() {
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Torto",    20, 8),
            p("Certeiro", 20, 18),
        ))!!
        assertThat(result.passesMade).isEqualTo(8)
        assertThat(result.passAttempts).isEqualTo(20)
        assertThat(result.playerAccuracyPct).isEqualTo(40)
        assertThat(result.teamAccuracyPct).isEqualTo(65)
        assertThat(result.deltaPct).isEqualTo(25)
    }

    // ── 3. Nenhum jogador elegível → null ────────────────────────────────────

    @Test
    fun `todos com acerto acima de 75 retorna null`() {
        assertThat(CorreioExtraviadoSelector.select(listOf(
            p("A", 20, 18),
            p("B", 20, 16),
        ))).isNull()
    }

    @Test
    fun `lista vazia retorna null`() {
        assertThat(CorreioExtraviadoSelector.select(emptyList())).isNull()
    }

    @Test
    fun `alto volume abaixo de 75 mas dentro de 5pp da media retorna null`() {
        // 14/20 = 70%; solo → team = 70%; delta = 0pp
        assertThat(CorreioExtraviadoSelector.select(listOf(p("X", 20, 14)))).isNull()
    }

    @Test
    fun `jogador sem dados de passe retorna null`() {
        val player = PlayerEntry(playerName = "SemDados", rating = "7.0")
        assertThat(CorreioExtraviadoSelector.select(listOf(player))).isNull()
    }

    // ── 4. Limite mínimo de tentativas (<3 → ignorado) ───────────────────────

    @Test
    fun `jogador com 2 tentativas e sempre ignorado mesmo com 0 passes certos`() {
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Minipass", 2, 0),
            p("Bom",      20, 18),
        ))
        assertThat(result).isNull()
    }

    @Test
    fun `jogador com 1 tentativa e ignorado`() {
        assertThat(CorreioExtraviadoSelector.select(listOf(p("Um", 1, 0)))).isNull()
    }

    // ── 5. Limite do alto volume (≥10 tentativas) ────────────────────────────

    @Test
    fun `jogador com exatamente 10 tentativas usa regra de alto volume`() {
        // Exato: 0/10 = 0%; Bom: 18/20 = 90%; team = 18/30 = 60%; delta = 60pp
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Exato", 10, 0),
            p("Bom",   20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Exato")
        assertThat(result.playerAccuracyPct).isEqualTo(0)
    }

    @Test
    fun `jogador com exatamente 5pp abaixo da media e elegivel`() {
        // A: 14/20 = 70%; B: 16/20 = 80%; team = 30/40 = 75%; A delta = 5pp
        val result = CorreioExtraviadoSelector.select(listOf(
            p("A", 20, 14),
            p("B", 20, 16),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("A")
        assertThat(result.deltaPct).isEqualTo(5)
    }

    @Test
    fun `jogador 4pp abaixo da media nao e elegivel`() {
        // A: 14/20 = 70%; B: 15/20 = 75%; team = 29/40 = 72%; A delta = 2pp
        assertThat(CorreioExtraviadoSelector.select(listOf(
            p("A", 20, 14),
            p("B", 20, 15),
        ))).isNull()
    }

    // ── 6. Baixo volume (3–9 tentativas): falha extrema ──────────────────────

    @Test
    fun `baixo volume com 0 passes certos e elegivel como falha extrema`() {
        // 0/5 = 0% ≤ 33%; Bom: 18/20 = 90%
        val result = CorreioExtraviadoSelector.select(listOf(
            p("ZeroPass", 5, 0),
            p("Bom",      20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("ZeroPass")
        assertThat(result.playerAccuracyPct).isEqualTo(0)
    }

    @Test
    fun `baixo volume com acerto exatamente 33 porcento e elegivel`() {
        // 1/3 = 33% ≤ LOW_VOLUME_MAX_ACCURACY_PCT=33
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Limite", 3, 1),
            p("Bom",    20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Limite")
        assertThat(result.playerAccuracyPct).isEqualTo(33)
    }

    @Test
    fun `baixo volume com acerto de 34 porcento nao e elegivel`() {
        // 3/8 = 37% > 33% → not eligible
        val result = CorreioExtraviadoSelector.select(listOf(
            p("AcimaLimite", 8, 3),
            p("Bom",         20, 18),
        ))
        assertThat(result).isNull()
    }

    @Test
    fun `baixo volume com 9 tentativas e 0 certos e elegivel`() {
        // 0/9 = 0% ≤ 33%
        val result = CorreioExtraviadoSelector.select(listOf(
            p("NoveZero", 9, 0),
            p("Bom",      20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("NoveZero")
    }

    @Test
    fun `baixo volume com 3 tentativas e minimo valido`() {
        // 3 attempts = MIN_PASS_ATTEMPTS; 0/3 = 0%
        val result = CorreioExtraviadoSelector.select(listOf(
            p("TresPasses", 3, 0),
            p("Bom",        20, 18),
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("TresPasses")
    }

    // ── 7. Confiança da amostra: alto-volume ganha sobre baixo-volume na igualdade ──

    @Test
    fun `alto volume com mesma acuracia vence sobre baixo volume`() {
        // Both at 0%; HighVol: 0/10; LowVol: 0/5
        // HighVol tier = HIGH_VOLUME → wins on tiebreak
        val result = CorreioExtraviadoSelector.select(listOf(
            p("LowVol",  5, 0),
            p("HighVol", 10, 0),
            p("Bom",     20, 18),
        ))
        assertThat(result!!.player.playerName).isEqualTo("HighVol")
    }

    @Test
    fun `baixo volume vence quando sua acuracia e genuinamente pior que o alto volume`() {
        // LowVol: 0/5 = 0%; HighVol: 4/10 = 40%
        // 0% < 40% → LowVol wins on primary accuracy sort
        val result = CorreioExtraviadoSelector.select(listOf(
            p("LowVol",  5, 0),
            p("HighVol", 20, 8),  // 40%, 12pp below team
            p("Bom",     20, 18),
        ))
        assertThat(result!!.player.playerName).isEqualTo("LowVol")
    }

    @Test
    fun `quando nao ha candidato de alto volume baixo volume com falha extrema e selecionado`() {
        // Team all good → no high-volume candidate; only low-volume extreme
        val result = CorreioExtraviadoSelector.select(listOf(
            p("BaixoExtremo", 5, 0),   // 0% — low-volume extreme
            p("BomA",         20, 18), // 90%
            p("BomB",         20, 17), // 85%
        ))
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("BaixoExtremo")
    }

    // ── 8. Desempate ─────────────────────────────────────────────────────────

    @Test
    fun `desempate por maior numero de passes errados dentro do mesmo tier`() {
        // Both high-volume at 20%; MaisErros: 4/20 (16 missed); MenosErros: 2/10 (8 missed)
        // Bom: 18/20=90%; team = (4+2+18)/50=48%; both 28pp below → same tier → MaisErros wins
        val result = CorreioExtraviadoSelector.select(listOf(
            p("MaisErros",  20, 4),
            p("MenosErros", 10, 2),
            p("Bom",        20, 18),
        ))
        assertThat(result!!.player.playerName).isEqualTo("MaisErros")
    }

    @Test
    fun `desempate final por nome alfabetico`() {
        // Abacaxi and Zebra: both 10/20=50%, same missed; ZZZBom: 18/20=90%
        // team = 38/60 = 63%; both 13pp below → Abacaxi wins alphabetically
        val result = CorreioExtraviadoSelector.select(listOf(
            p("Zebra",   20, 10),
            p("Abacaxi", 20, 10),
            p("ZZZBom",  20, 18),
        ))
        assertThat(result!!.player.playerName).isEqualTo("Abacaxi")
    }

    // ── 9. Anomalias EA ──────────────────────────────────────────────────────

    @Test
    fun `passesmade maior que tentativas e clamped corretamente`() {
        // passesMade=10 > passAttempts=5 → clamped to 5/5 = 100% → not eligible (not ≤33%)
        // Also 5 < HIGH_VOLUME_THRESHOLD, so low-volume rule applies: 100% > 33% → excluded
        val player = PlayerEntry(playerName = "Anomalo", passAttempts = "5", passesMade = "10", rating = "7.0")
        assertThat(CorreioExtraviadoSelector.select(listOf(player))).isNull()
    }

    @Test
    fun `jogador com tentativas zero e ignorado`() {
        val player = PlayerEntry(playerName = "ZeroAtt", passAttempts = "0", passesMade = "0", rating = "7.0")
        assertThat(CorreioExtraviadoSelector.select(listOf(player))).isNull()
    }

    // ── 10. Constantes ───────────────────────────────────────────────────────

    @Test
    fun `constantes tem valores corretos`() {
        assertThat(CorreioExtraviadoSelector.MIN_PASS_ATTEMPTS).isEqualTo(3)
        assertThat(CorreioExtraviadoSelector.HIGH_VOLUME_THRESHOLD).isEqualTo(10)
        assertThat(CorreioExtraviadoSelector.MAX_ACCURACY_PCT).isEqualTo(75)
        assertThat(CorreioExtraviadoSelector.MIN_DELTA_PCT).isEqualTo(5)
        assertThat(CorreioExtraviadoSelector.LOW_VOLUME_MAX_ACCURACY_PCT).isEqualTo(33)
    }
}
