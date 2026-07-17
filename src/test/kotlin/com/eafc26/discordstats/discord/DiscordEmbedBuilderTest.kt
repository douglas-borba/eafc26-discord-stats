package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class DiscordEmbedBuilderTest {

    private val ourClubId = "12345"
    private val zone = ZoneOffset.UTC

    // -- Título ---------------------------------------------------------------

    @Test
    fun `titulo contem nomes dos clubes e placar com sinal de multiplicacao`() {
        val embed = buildEmbed(ourName = "Associação BF", oppName = "Rival FC", ourScore = "2", oppScore = "1").embeds[0]
        assertThat(embed.title).isEqualTo("🏆 Associação BF 2 × 1 Rival FC")
    }

    @Test
    fun `titulo contem trofeu e sinal de multiplicacao`() {
        val embed = buildEmbed().embeds[0]
        assertThat(embed.title).contains("🏆")
        assertThat(embed.title).contains("×")
    }

    // -- Descrição (resultado + data) ----------------------------------------

    @Test
    fun `descricao contem emoji verde e Vitoria`() {
        val embed = buildEmbed(ourResult = "1").embeds[0]
        assertThat(embed.description).contains("🟢")
        assertThat(embed.description).contains("Vitória")
    }

    @Test
    fun `descricao contem emoji amarelo e Empate`() {
        val embed = buildEmbed(ourResult = "2", ourScore = "1", oppScore = "1").embeds[0]
        assertThat(embed.description).contains("🟡")
        assertThat(embed.description).contains("Empate")
    }

    @Test
    fun `descricao contem emoji vermelho e Derrota`() {
        val embed = buildEmbed(ourResult = "0", ourScore = "0", oppScore = "1").embeds[0]
        assertThat(embed.description).contains("🔴")
        assertThat(embed.description).contains("Derrota")
    }

    @Test
    fun `descricao contem data com bullet e formato pt-BR`() {
        val embed = buildEmbed().embeds[0]
        // timestamp 1718500000 = 2024-06-16 01:06 UTC
        assertThat(embed.description).contains("📅")
        assertThat(embed.description).contains("•")
        assertThat(embed.description).matches("(?s).*\\d{2} [a-z]+\\.? \\d{4} • \\d{2}:\\d{2}.*")
    }

    @Test
    fun `nao ha campos separados de RESULTADO PLACAR ou DATA`() {
        val embed = buildEmbed().embeds[0]
        val names = embed.fields.map { it.name }
        assertThat(names).doesNotContain("RESULTADO")
        assertThat(names).doesNotContain("PLACAR")
        assertThat(names).doesNotContain("DATA")
    }

    // -- Cores ----------------------------------------------------------------

    @Test
    fun `cor verde para vitoria`() {
        assertThat(buildEmbed(ourResult = "1", ourScore = "2", oppScore = "1").embeds[0].color).isEqualTo(0x2ECC71)
    }

    @Test
    fun `cor cinza para empate`() {
        assertThat(buildEmbed(ourResult = "2", ourScore = "1", oppScore = "1").embeds[0].color).isEqualTo(0x95A5A6)
    }

    @Test
    fun `cor vermelha para derrota`() {
        assertThat(buildEmbed(ourResult = "0", ourScore = "0", oppScore = "2").embeds[0].color).isEqualTo(0xE74C3C)
    }

    // -- Rodapé ---------------------------------------------------------------

    @Test
    fun `rodape ausente`() {
        val embed = buildEmbed(matchId = "999888777").embeds[0]
        assertThat(embed.footer).isNull()
    }

    // -- Separadores ----------------------------------------------------------

    @Test
    fun `separadores aparecem entre secoes presentes`() {
        val embed = buildEmbedWithPlayers(
            player("Atacante", goals = "1", rating = "8.0"),
            player("Meia",     assists = "1", rating = "7.0"),
        ).embeds[0]
        val seps = embed.fields.filter { it.name == "​" }
        assertThat(seps).isNotEmpty()
        assertThat(seps[0].value).isEqualTo("──────────────────────────────")
    }

    @Test
    fun `primeiro campo do embed e um separador`() {
        val embed = buildEmbedWithPlayers(
            player("Atacante", goals = "1", rating = "8.0"),
        ).embeds[0]
        assertThat(embed.fields).isNotEmpty()
        assertThat(embed.fields[0].name).isEqualTo("​")
    }

    // -- Gols -----------------------------------------------------------------

    @Test
    fun `secao GOLS usa formato x-N e lista marcadores`() {
        val embed = buildEmbedWithPlayers(
            player("Atacante", goals = "2"),
            player("Meia",     goals = "1"),
            player("Zagueiro", goals = "0"),
        ).embeds[0]
        val gols = embed.fields.field("⚽ GOLS").value
        assertThat(gols).contains("Atacante ×2")
        assertThat(gols).contains("Meia ×1")
        assertThat(gols).doesNotContain("Zagueiro")
    }

    @Test
    fun `secao GOLS omitida quando ninguem marcou`() {
        val embed = buildEmbedWithPlayers(player("Defensor", goals = "0")).embeds[0]
        assertThat(embed.fields.none { it.name == "⚽ GOLS" }).isTrue()
    }

    @Test
    fun `secao GOLS ordenada por gols decrescente`() {
        val embed = buildEmbedWithPlayers(
            player("Um",   goals = "1"),
            player("Dois", goals = "2"),
        ).embeds[0]
        val text = embed.fields.field("⚽ GOLS").value
        assertThat(text.indexOf("Dois")).isLessThan(text.indexOf("Um"))
    }

    // -- Assistências ---------------------------------------------------------

    @Test
    fun `secao ASSISTENCIAS usa formato x-N e lista assistentes`() {
        val embed = buildEmbedWithPlayers(
            player("Passador", assists = "2"),
            player("SemPasse", assists = "0"),
        ).embeds[0]
        val field = embed.fields.field("🎯 ASSISTÊNCIAS")
        assertThat(field.value).contains("Passador ×2")
        assertThat(field.value).doesNotContain("SemPasse")
    }

    @Test
    fun `secao ASSISTENCIAS omitida quando ninguem deu assistencia`() {
        val embed = buildEmbedWithPlayers(player("Atacante", assists = "0")).embeds[0]
        assertThat(embed.fields.none { it.name == "🎯 ASSISTÊNCIAS" }).isTrue()
    }

    // -- Destaques + Média do time --------------------------------------------

    @Test
    fun `secao DESTAQUES mostra top 3 com medalhas`() {
        val embed = buildEmbedWithPlayers(
            player("Ouro",   rating = "9.5"),
            player("Prata",  rating = "8.5"),
            player("Bronze", rating = "7.5"),
            player("Quarto", rating = "6.5"),
        ).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("🥇").contains("🥈").contains("🥉")
        assertThat(text).contains("Ouro").contains("Prata").contains("Bronze")
        assertThat(text).doesNotContain("Quarto")
    }

    @Test
    fun `secao DESTAQUES formato contem palavra Nota`() {
        val embed = buildEmbedWithPlayers(player("Jogador", rating = "8.5")).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("Jogador — Nota")
    }

    @Test
    fun `craque da partida marcado na secao CRAQUE`() {
        val embed = buildEmbedWithPlayers(
            player("MVP",  rating = "9.0", mom = "1"),
            player("Dois", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("⭐ CRAQUE DA PARTIDA").value
        assertThat(text).contains("MVP")
    }

    @Test
    fun `craque da partida tem frase motivacional na secao CRAQUE`() {
        val embed = buildEmbedWithPlayers(
            player("MVP",     rating = "9.0", mom = "1"),
            player("Segundo", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("⭐ CRAQUE DA PARTIDA").value
        assertThat(text).contains("💬 \"")
    }

    @Test
    fun `jogador sem mom nao tem frase motivacional`() {
        val embed = buildEmbedWithPlayers(player("Normal", rating = "8.0")).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).doesNotContain("💬")
    }

    @Test
    fun `nota usa virgula decimal nos destaques`() {
        val embed = buildEmbedWithPlayers(player("Jogador", rating = "8.4")).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("8,40")
        assertThat(text).doesNotContain("8.40")
    }

    @Test
    fun `secao DESTAQUES omitida quando nenhum jogador tem nota`() {
        val embed = buildEmbedWithPlayers(player("SemNota", rating = null)).embeds[0]
        assertThat(embed.fields.none { it.name == "🥇 DESTAQUES" }).isTrue()
    }

    @Test
    fun `goleiro nao aparece nos destaques top 3`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("SuperGK", rating = "9.9"),
            player("Linha1",      rating = "8.0"),
            player("Linha2",      rating = "7.5"),
            player("Linha3",      rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).doesNotContain("SuperGK")
        assertThat(text).contains("Linha1")
    }

    @Test
    fun `media do time aparece dentro da secao DESTAQUES`() {
        val embed = buildEmbedWithPlayers(
            player("A", rating = "8.0"),
            player("B", rating = "6.0"),
        ).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("Média do time")
        assertThat(text).contains("7,00")
    }

    @Test
    fun `media do time exclui jogadores com zero segundos`() {
        val embed = buildEmbedWithPlayers(
            player("Ativo",   rating = "6.0", secondsPlayed = "900"),
            player("Inativo", rating = "9.0", secondsPlayed = "0"),
        ).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("Média do time")
        assertThat(text).contains("6,00")
        assertThat(text).doesNotContain("9,00")
    }

    @Test
    fun `media do time inclui goleiro ativo`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK",  rating = "8.0"),
            player("Linha",   rating = "6.0"),
        ).embeds[0]
        // avg = (8.0 + 6.0) / 2 = 7.0
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).contains("7,00")
    }

    @Test
    fun `media do time usa virgula decimal`() {
        val embed = buildEmbedWithPlayers(
            player("A", rating = "8.4"),
            player("B", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🥇 DESTAQUES").value
        assertThat(text).doesNotContain("7.70")
    }

    // -- Jogadores inativos --------------------------------------------------

    @Test
    fun `jogadores com zero segundos excluidos de todas as secoes`() {
        val embed = buildEmbedWithPlayers(
            player("Ativo",   rating = "8.0", goals = "1", secondsPlayed = "900"),
            player("Inativo", rating = "3.0", goals = "1", secondsPlayed = "0"),
        ).embeds[0]
        val allText = embed.fields.joinToString("\n") { it.value }
        assertThat(allText).contains("Ativo")
        assertThat(allText).doesNotContain("Inativo")
    }

    @Test
    fun `jogadores com secondsPlayed nulo sao incluidos`() {
        val embed = buildEmbedWithPlayers(
            player("SemCampo", rating = "7.5", secondsPlayed = null),
        ).embeds[0]
        assertThat(embed.fields.field("🥇 DESTAQUES").value).contains("SemCampo")
    }

    // -- Muralha da Partida --------------------------------------------------

    @Test
    fun `secao MURALHA mostra defesas realizadas`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("Guardião", saves = "7", rating = "8.5", goalsConceded = "2"),
            player("Linha", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🧤 MURALHA DA PARTIDA").value
        assertThat(text).contains("Defesas realizadas: 7")
    }

    @Test
    fun `secao MURALHA nao mostra nome nem nota do goleiro`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("Guardião", saves = "5", rating = "8.5"),
            player("Linha", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🧤 MURALHA DA PARTIDA").value
        assertThat(text).doesNotContain("Guardião")
        assertThat(text).doesNotContain("8,50")
    }

    @Test
    fun `secao MURALHA contem frase dinamica`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK", saves = "3"),
            player("Linha", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🧤 MURALHA DA PARTIDA").value
        assertThat(text).contains("💬 \"")
    }

    @Test
    fun `secao MURALHA omitida quando nenhum goleiro jogou`() {
        val embed = buildEmbedWithPlayers(player("Meia", rating = "7.0")).embeds[0]
        assertThat(embed.fields.none { it.name == "🧤 MURALHA DA PARTIDA" }).isTrue()
    }

    @Test
    fun `quando dois goleiros jogaram muralha usa o com mais minutos`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK_Curto", saves = "1", rating = "7.0", secondsPlayed = "600"),
            goalkeeper("GK_Longo", saves = "5", rating = "8.0", secondsPlayed = "1200"),
            player("Linha", rating = "6.5"),
        ).embeds[0]
        assertThat(embed.fields.field("🧤 MURALHA DA PARTIDA").value).contains("Defesas realizadas: 5")
    }

    @Test
    fun `secao GOLEIRO nao existe mais`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK", saves = "3"),
            player("Linha", rating = "7.0"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "🧤 GOLEIRO" }).isTrue()
    }

    // -- Bagre da Partida -----------------------------------------------------

    @Test
    fun `jogador de linha com menor nota e o Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("PiorJogador", rating = "5.0"),
            player("BomJogador",  rating = "8.0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("PiorJogador")
        assertThat(text).doesNotContain("BomJogador")
    }

    @Test
    fun `goleiro nunca e Bagre mesmo com a menor nota`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GKRuim", rating = "3.0"),
            player("Linha",      rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).doesNotContain("GKRuim")
        assertThat(text).contains("Linha")
    }

    @Test
    fun `bagre omitido quando so ha goleiro`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("SoGoleiro", rating = "6.0"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "🍍 BAGRE DA PARTIDA" }).isTrue()
    }

    @Test
    fun `nota do Bagre usa virgula decimal`() {
        val embed = buildEmbedWithPlayers(player("Bagre", rating = "6.70")).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("6,70")
        assertThat(text).doesNotContain("6.70")
    }

    @Test
    fun `bagre contem frase de rating`() {
        val embed = buildEmbedWithPlayers(player("Bagre", rating = "5.0")).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("💬 \"")
    }

    @Test
    fun `bagre subsecao Passes usa formato compacto`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", passAttempts = "21", passesMade = "15"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("📉 Passes")
        assertThat(text).contains("• 15/21 certos (71%)")
        assertThat(text).contains("• 6 passes errados")
    }

    @Test
    fun `bagre subsecao Passes contem frase`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", passAttempts = "10", passesMade = "3"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        val passIdx = text.indexOf("📉 Passes")
        assertThat(text.substring(passIdx)).contains("💬 \"")
    }

    @Test
    fun `bagre subsecao Desarmes usa formato compacto`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", tackleAttempts = "14", tacklesMade = "2"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("🛡️ Desarmes")
        assertThat(text).contains("• 2/14 certos")
        assertThat(text).contains("14% de aproveitamento")
    }

    @Test
    fun `bagre subsecao Desarmes contem frase`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", tackleAttempts = "10", tacklesMade = "2"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        val tackleIdx = text.indexOf("🛡️ Desarmes")
        assertThat(text.substring(tackleIdx)).contains("💬 \"")
    }

    @Test
    fun `bagre subsecao Finalizacoes usa chutes`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", shots = "3", goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).contains("🎯 Finalizações")
        assertThat(text).contains("3 chutes")
        assertThat(text).contains("0 gols")
    }

    @Test
    fun `bagre subsecao Finalizacoes contem frase`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", shots = "4", goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        val shotsIdx = text.indexOf("🎯 Finalizações")
        assertThat(text.substring(shotsIdx)).contains("💬 \"")
    }

    @Test
    fun `bagre subsecao Passes omitida quando zero tentativas`() {
        val embed = buildEmbedWithPlayers(player("Bagre", rating = "5.0")).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).doesNotContain("Passes")
        assertThat(text).doesNotContain("passes errados")
    }

    @Test
    fun `bagre subsecao Desarmes omitida quando zero tentativas`() {
        val embed = buildEmbedWithPlayers(player("Bagre", rating = "5.0")).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).doesNotContain("Desarmes")
        assertThat(text).doesNotContain("tentativas perdidas")
    }

    @Test
    fun `bagre subsecao Finalizacoes omitida quando zero chutes`() {
        val embed = buildEmbedWithPlayers(player("Bagre", rating = "5.0", shots = "0")).embeds[0]
        val text = embed.fields.field("🍍 BAGRE DA PARTIDA").value
        assertThat(text).doesNotContain("chutes")
    }

    @Test
    fun `bagre cartao vermelho aparece quando presente`() {
        val embed = buildEmbedWithPlayers(
            player("Expulso", rating = "4.0", redCards = "1"),
        ).embeds[0]
        assertThat(embed.fields.field("🍍 BAGRE DA PARTIDA").value).contains("🟥 Cartão vermelho: 1")
    }

    // -- Xerife da Partida ----------------------------------------------------

    @Test
    fun `secao XERIFE mostra nome e numero de desarmes`() {
        val embed = buildEmbedWithPlayers(
            player("Xerife", rating = "7.0", tackleAttempts = "10", tacklesMade = "8"),
            player("Outro",  rating = "7.5", tackleAttempts = "5",  tacklesMade = "2"),
        ).embeds[0]
        val text = embed.fields.field("🚧 XERIFE DA PARTIDA").value
        assertThat(text).contains("Xerife")
        assertThat(text).contains("8/10 desarmes")
    }

    @Test
    fun `secao XERIFE mostra aproveitamento percentual`() {
        val embed = buildEmbedWithPlayers(
            player("Xerife", rating = "7.0", tackleAttempts = "10", tacklesMade = "8"),
        ).embeds[0]
        val text = embed.fields.field("🚧 XERIFE DA PARTIDA").value
        assertThat(text).contains("Aproveitamento: 80%")
    }

    @Test
    fun `secao XERIFE contem frase dinamica`() {
        val embed = buildEmbedWithPlayers(
            player("Xerife", rating = "7.0", tackleAttempts = "5", tacklesMade = "4"),
        ).embeds[0]
        val text = embed.fields.field("🚧 XERIFE DA PARTIDA").value
        assertThat(text).contains("💬 \"")
    }

    @Test
    fun `goleiro nunca e Xerife`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GKXerife", rating = "8.0", saves = "5"),
            player("Linha", rating = "7.0", tackleAttempts = "3", tacklesMade = "2"),
        ).embeds[0]
        val text = embed.fields.field("🚧 XERIFE DA PARTIDA").value
        assertThat(text).doesNotContain("GKXerife")
        assertThat(text).contains("Linha")
    }

    @Test
    fun `secao XERIFE omitida quando nenhum outfield tem desarmes`() {
        val embed = buildEmbedWithPlayers(player("SemDesarme", rating = "7.0")).embeds[0]
        assertThat(embed.fields.none { it.name == "🚧 XERIFE DA PARTIDA" }).isTrue()
    }

    @Test
    fun `XERIFE selecionado por maior numero de desarmes`() {
        val embed = buildEmbedWithPlayers(
            player("CampeaoDesarme", rating = "6.0", tackleAttempts = "12", tacklesMade = "10"),
            player("PoucosDesarmes", rating = "9.0", tackleAttempts = "4",  tacklesMade = "3"),
        ).embeds[0]
        val text = embed.fields.field("🚧 XERIFE DA PARTIDA").value
        assertThat(text).contains("CampeaoDesarme")
        assertThat(text).doesNotContain("PoucosDesarmes")
    }

    // -- Chutou, mas não entrou -----------------------------------------------

    @Test
    fun `secao CHUTOU mostra jogador com mais chutes sem gol`() {
        val embed = buildEmbedWithPlayers(
            player("Azarado",  shots = "5", goals = "0"),
            player("MenosAzar", shots = "3", goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🎯 CHUTOU, MAS NÃO ENTROU").value
        assertThat(text).contains("Azarado")
        assertThat(text).contains("5 finalizações e nenhum gol")
    }

    @Test
    fun `secao CHUTOU exclui marcadores mesmo com mais chutes`() {
        val embed = buildEmbedWithPlayers(
            player("Marcador", shots = "10", goals = "1"),
            player("SemGol",   shots = "4",  goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🎯 CHUTOU, MAS NÃO ENTROU").value
        assertThat(text).contains("SemGol")
        assertThat(text).doesNotContain("Marcador")
    }

    @Test
    fun `secao CHUTOU omitida quando ninguem chutou sem marcar`() {
        val embed = buildEmbedWithPlayers(
            player("Goleador", shots = "3", goals = "2"),
            player("SemChute", shots = "0", goals = "0"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "🎯 CHUTOU, MAS NÃO ENTROU" }).isTrue()
    }

    @Test
    fun `secao CHUTOU omitida quando campo shots ausente`() {
        val embed = buildEmbedWithPlayers(
            player("SemChute", shots = null, goals = "0"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "🎯 CHUTOU, MAS NÃO ENTROU" }).isTrue()
    }

    @Test
    fun `secao CHUTOU excluir jogador desconectado`() {
        // Disco played only 100 s when max is 900 s -> ineligible
        val embed = buildEmbedWithPlayers(
            player("Ativo",  shots = "3", goals = "0", secondsPlayed = "900"),
            player("Disco",  shots = "9", goals = "0", secondsPlayed = "100"),
        ).embeds[0]
        val text = embed.fields.field("🎯 CHUTOU, MAS NÃO ENTROU").value
        assertThat(text).contains("Ativo")
        assertThat(text).doesNotContain("Disco")
    }

    @Test
    fun `secao CHUTOU desempate deterministico por nome`() {
        val embed = buildEmbedWithPlayers(
            player("Beta",  shots = "4", goals = "0"),
            player("Alpha", shots = "4", goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🎯 CHUTOU, MAS NÃO ENTROU").value
        // "Alpha" < "Beta" alphabetically -> Alpha wins tie-break
        assertThat(text).contains("Alpha")
        assertThat(text).doesNotContain("Beta")
    }

    // -- Correio Extraviado ---------------------------------------------------

    @Test
    fun `secao CORREIO mostra jogador com mais passes errados`() {
        val embed = buildEmbedWithPlayers(
            player("Torto",   passAttempts = "20", passesMade = "8"),
            player("Certeiro", passAttempts = "20", passesMade = "18"),
        ).embeds[0]
        val text = embed.fields.field("📮 CORREIO EXTRAVIADO").value
        assertThat(text).contains("Torto")
        assertThat(text).contains("12 passes errados")
    }

    @Test
    fun `secao CORREIO omitida quando ninguem errou passe`() {
        val embed = buildEmbedWithPlayers(
            player("Preciso", passAttempts = "10", passesMade = "10"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "📮 CORREIO EXTRAVIADO" }).isTrue()
    }

    @Test
    fun `secao CORREIO omitida quando campos de passe ausentes`() {
        val embed = buildEmbedWithPlayers(
            player("SemPasse", passAttempts = null, passesMade = null),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "📮 CORREIO EXTRAVIADO" }).isTrue()
    }

    @Test
    fun `secao CORREIO omitida quando passesMade ausente`() {
        val embed = buildEmbedWithPlayers(
            player("ParcialPass", passAttempts = "10", passesMade = null),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "📮 CORREIO EXTRAVIADO" }).isTrue()
    }

    @Test
    fun `secao CORREIO nunca produz valor negativo quando certos excedem tentativas`() {
        // EA data anomaly: passesMade > passAttempts -> clamp to 0 -> excluded
        val embed = buildEmbedWithPlayers(
            player("Anomalo", passAttempts = "5", passesMade = "10"),
        ).embeds[0]
        assertThat(embed.fields.none { it.name == "📮 CORREIO EXTRAVIADO" }).isTrue()
    }

    @Test
    fun `secao CORREIO excluir jogador desconectado`() {
        val embed = buildEmbedWithPlayers(
            player("Ativo", passAttempts = "10", passesMade = "2", secondsPlayed = "900"),
            player("Disco", passAttempts = "20", passesMade = "0", secondsPlayed = "100"),
        ).embeds[0]
        val text = embed.fields.field("📮 CORREIO EXTRAVIADO").value
        assertThat(text).contains("Ativo")
        assertThat(text).doesNotContain("Disco")
    }

    @Test
    fun `secao CORREIO desempate deterministico por nome`() {
        val embed = buildEmbedWithPlayers(
            player("Zebra",  passAttempts = "20", passesMade = "10"),
            player("Abacaxi", passAttempts = "20", passesMade = "10"),
        ).embeds[0]
        val text = embed.fields.field("📮 CORREIO EXTRAVIADO").value
        assertThat(text).contains("Abacaxi")
        assertThat(text).doesNotContain("Zebra")
    }

    // -- Sem rótulos em inglês ------------------------------------------------

    @Test
    fun `nenhum label em ingles no embed completo`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("Guardiao",  rating = "8.0", saves = "3", goalsConceded = "1"),
            player("Artilheiro",   rating = "9.0", goals = "2", assists = "1", mom = "1"),
            player("Assistente",   rating = "7.5", assists = "1"),
            player("Bagre",        rating = "5.0", shots = "3", passAttempts = "10", passesMade = "4"),
        ).embeds[0]
        val allText = embed.fields.joinToString("\n") { "${it.name}\n${it.value}" }
        val desc = embed.description ?: ""
        assertThat(allText + desc).doesNotContain("Result")
        assertThat(allText + desc).doesNotContain("Score")
        assertThat(allText + desc).doesNotContain("Goal Scorers")
        assertThat(allText + desc).doesNotContain("Assists\n")
        assertThat(allText + desc).doesNotContain("MOTM")
        assertThat(embed.footer).isNull()
    }

    // -- Helpers --------------------------------------------------------------

    private fun buildEmbed(
        matchId: String = "123",
        ourName: String = "Our Club",
        oppName: String = "Opp Club",
        ourScore: String = "1",
        oppScore: String = "0",
        ourResult: String = "1",
    ): DiscordPayload {
        val match = MatchResponse(
            matchId   = matchId,
            timestamp = 1718500000L,
            matchType = "leagueMatch",
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = ourName),
                    score   = ourScore,
                    result  = ourResult,
                ),
                "99999" to ClubMatchEntry(
                    details = ClubDetails(name = oppName),
                    score   = oppScore,
                    result  = if (ourResult == "1") "0" else "1",
                ),
            ),
            players = emptyMap(),
        )
        return DiscordEmbedBuilder.build(match, ourClubId, zone)
    }

    private fun buildEmbedWithPlayers(vararg players: PlayerEntry): DiscordPayload {
        val match = MatchResponse(
            matchId   = "42",
            timestamp = 1718500000L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
                "99999"   to ClubMatchEntry(details = ClubDetails(name = "Opp"),      score = "0", result = "0"),
            ),
            players = mapOf(ourClubId to players.mapIndexed { i, p -> "p$i" to p }.toMap()),
        )
        return DiscordEmbedBuilder.build(match, ourClubId, zone)
    }

    private fun player(
        name: String,
        goals: String?          = "0",
        assists: String?         = "0",
        rating: String?          = "7.0",
        mom: String?             = "0",
        secondsPlayed: String?   = "900",
        shots: String?           = null,
        passAttempts: String?    = null,
        passesMade: String?      = null,
        tackleAttempts: String?  = null,
        tacklesMade: String?     = null,
        redCards: String?        = null,
    ) = PlayerEntry(
        playerName     = name,
        position       = null,
        goals          = goals,
        assists        = assists,
        rating         = rating,
        manOfTheMatch  = mom,
        secondsPlayed  = secondsPlayed,
        shots          = shots,
        passAttempts   = passAttempts,
        passesMade     = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade    = tacklesMade,
        redCards       = redCards,
    )

    private fun goalkeeper(
        name: String,
        saves: String?         = "0",
        rating: String?        = "7.0",
        goalsConceded: String? = "0",
        secondsPlayed: String? = "900",
        mom: String?           = "0",
    ) = PlayerEntry(
        playerName    = name,
        position      = "goalkeeper",
        rating        = rating,
        manOfTheMatch = mom,
        secondsPlayed = secondsPlayed,
        saves         = saves,
        goalsConceded = goalsConceded,
    )

    private fun List<EmbedField>.field(name: String) =
        firstOrNull { it.name == name } ?: error("Field '$name' not found in ${map { it.name }}")
}
