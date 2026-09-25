package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.GameVersion
import com.eafc26.discordstats.domain.match.MatchCompletionStatus
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult.Success
import com.eafc26.discordstats.service.CanonicalMatchFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** Regression contract built only from the captured FC27 Worshipers match fields. */
class Fc27ContractTest {
    private val mapper = EaMatchMapper()
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `normalizes the real Worshipers FC27 league fixture without FC26 semantic leakage`() {
        val source = fixture()

        val normalized = mapper.map(source).success().match
        val canonical = CanonicalMatchFactory().create(source, "486460")
        val worshipers = normalized.participants.single { it.club.id == ClubId("486460") }
        val opponent = normalized.participants.single { it.club.id == ClubId("208428") }
        val dbeng = worshipers.players.single { it.player.id.value == "1003722789595" }

        assertThat(normalized.id.value).isEqualTo("16540167170290")
        assertThat(normalized.competition).isEqualTo(CompetitionType.LEAGUE)
        assertThat(normalized.completion.status).isEqualTo(MatchCompletionStatus.COMPLETED)
        assertThat(worshipers.club.name?.value).isEqualTo("Worshipers FC")
        assertThat(worshipers.score.goals).isEqualTo(1)
        assertThat(opponent.score.goals).isEqualTo(4)
        assertThat(dbeng.player.platformName?.value).isEqualTo("dbeng_bass")
        assertThat(dbeng.eaPositionCode).isEqualTo("forward")
        assertThat(dbeng.rating?.value).isEqualByComparingTo(BigDecimal("6.90"))
        assertThat(dbeng.attacking.goals).isZero()
        assertThat(dbeng.attacking.assists).isZero()
        assertThat(dbeng.passing.completed).isEqualTo(30)
        assertThat(dbeng.passing.attempted).isEqualTo(35)
        assertThat(dbeng.defending.tacklesCompleted).isEqualTo(1)
        assertThat(dbeng.defending.tacklesAttempted).isEqualTo(8)
        assertThat(dbeng.discipline.redCards).isZero()
        assertThat(dbeng.participation.duration?.seconds).isEqualTo(5602)
        assertThat(dbeng.participation.status).isNull()
        assertThat(dbeng.rawEventAggregates?.aggregate0).contains("112:3")
        assertThat(dbeng.rawEventAggregates?.aggregate1).isEqualTo("34:8,35:1,6:2,8:3,97:14")
        assertThat(dbeng.rawEventAggregates?.aggregate2).isEqualTo("")
        assertThat(dbeng.rawEventAggregates?.aggregate3).isEqualTo("")
        assertThat(dbeng.advancedCoverage.name).isEqualTo("UNAVAILABLE")
        assertThat(dbeng.advanced.secondAssists).isZero()
        assertThat(canonical.gameVersion).isEqualTo(GameVersion.FC27)
    }

    @Test
    fun `gateway provenance wins over a conflicting legacy root match type`() {
        val source = fixture().copy(matchType = "playoffMatch", sourceMatchType = "leagueMatch")
        assertThat(mapper.map(source).success().match.competition).isEqualTo(CompetitionType.LEAGUE)
    }

    @Test
    fun `all factual gateway competition sources normalize without reading club level match type`() {
        assertThat(mapper.map(fixture().copy(matchType = null, sourceMatchType = "leagueMatch")).success().match.competition)
            .isEqualTo(CompetitionType.LEAGUE)
        assertThat(mapper.map(fixture().copy(matchType = null, sourceMatchType = "playoffMatch")).success().match.competition)
            .isEqualTo(CompetitionType.PLAYOFF)
        assertThat(mapper.map(fixture().copy(matchType = null, sourceMatchType = "friendlyMatch")).success().match.competition)
            .isEqualTo(CompetitionType.FRIENDLY)
    }

    @Test
    fun `legacy FC26 root match type remains supported when provenance is absent`() {
        val source = fixture().copy(sourceMatchType = null, sourceGameVersion = null, matchType = "playoffMatch")
        assertThat(mapper.map(source).success().match.competition).isEqualTo(CompetitionType.PLAYOFF)
    }

    @Test
    fun `missing provenance and root match type remains unknown rather than reading club matchType`() {
        val source = fixture().copy(sourceMatchType = null, sourceGameVersion = null, matchType = null)
        assertThat(mapper.map(source).success().match.competition).isNull()
    }

    private fun fixture(): MatchResponse = objectMapper.readValue<List<MatchResponse>>(
        requireNotNull(javaClass.getResourceAsStream("/fixtures/fc27-worshipers-league-match.json")),
    ).single()

    private fun MatchNormalizationResult.success(): Success = this as? Success
        ?: error("Expected successful FC27 normalization, got $this")
}
