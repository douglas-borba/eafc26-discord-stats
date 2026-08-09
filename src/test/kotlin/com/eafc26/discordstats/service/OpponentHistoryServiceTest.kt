package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.opponent.OpponentLeaderType
import com.eafc26.discordstats.opponent.OpponentRunType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class OpponentHistoryServiceTest {
    private lateinit var history: MatchHistoryService
    private lateinit var service: OpponentHistoryService

    @BeforeEach fun setUp() { history = mock(); service = OpponentHistoryService(history) }

    @Test fun `empty history has no opponents`() {
        whenever(history.list(OUR)).thenReturn(emptyList())
        assertThat(service.listOpponents(OUR)).isEmpty()
    }

    @Test fun `groups by ClubId uses latest name and keeps same names with different ids separate`() {
        whenever(history.list(OUR)).thenReturn(listOf(
            match("new-a", 400, "a", "Nome Atual", 2, 0),
            match("old-a", 100, "a", "Nome Antigo", 1, 1),
            match("club-b", 300, "b", "Nome Atual", 0, 1),
        ))
        val result = service.listOpponents(OUR)
        assertThat(result.map { it.clubId.value }).containsExactly("a", "b")
        assertThat(result.first().displayName).isEqualTo("Nome Atual")
        assertThat(result.first().meetings).isEqualTo(2)
        assertThat(service.findByClubId(OUR, ClubId("a"))!!.previousNames).containsExactly("Nome Antigo")
    }

    @Test fun `aggregates record goals balance and orders by latest meeting`() {
        whenever(history.list(OUR)).thenReturn(listOf(
            match("loss", 300, "a", "A", 1, 3), match("draw", 200, "a", "A", 2, 2),
            match("win", 100, "a", "A", 4, 0), match("other", 250, "b", "B", 1, 0),
        ))
        val index = service.listOpponents(OUR)
        assertThat(index.map { it.clubId.value }).containsExactly("a", "b")
        val record = service.findByClubId(OUR, ClubId("a"))!!.record
        assertThat(record.meetings).isEqualTo(3)
        assertThat(listOf(record.wins, record.draws, record.losses)).containsExactly(1, 1, 1)
        assertThat(record.goalsFor).isEqualTo(7); assertThat(record.goalsAgainst).isEqualTo(5)
        assertThat(record.goalDifference).isEqualTo(2)
    }

    @Test fun `preserves tied biggest wins and losses and omits missing outcome category`() {
        whenever(history.list(OUR)).thenReturn(listOf(
            match("w1", 100, "a", "A", 3, 0), match("w2", 200, "a", "A", 4, 1),
            match("l1", 300, "a", "A", 0, 2), match("l2", 400, "a", "A", 1, 3),
        ))
        val result = service.findByClubId(OUR, ClubId("a"))!!
        assertThat(result.biggestWins.map { it.matchId.value }).containsExactly("w2", "w1")
        assertThat(result.biggestLosses.map { it.matchId.value }).containsExactly("l2", "l1")
        whenever(history.list(OUR)).thenReturn(listOf(match("only-win", 100, "b", "B", 1, 0)))
        assertThat(service.findByClubId(OUR, ClubId("b"))!!.biggestLosses).isEmpty()
    }

    @Test fun `requires two matches for runs and calculates current and historical records chronologically`() {
        whenever(history.list(OUR)).thenReturn(listOf(match("one",100,"a","A",1,0)))
        val one = service.findByClubId(OUR, ClubId("a"))!!
        assertThat(one.currentRun).isNull(); assertThat(one.runRecords).isEmpty()

        whenever(history.list(OUR)).thenReturn(listOf(
            match("m5",500,"a","A",2,0), match("m4",400,"a","A",1,0), match("m3",300,"a","A",0,1),
            match("m2",200,"a","A",1,1), match("m1",100,"a","A",1,0),
        ))
        val many = service.findByClubId(OUR, ClubId("a"))!!
        assertThat(many.currentRun!!.type).isEqualTo(OpponentRunType.WINNING)
        assertThat(many.currentRun!!.matchIds.map { it.value }).containsExactly("m4", "m5")
        assertThat(many.runRecords.single { it.type == OpponentRunType.UNBEATEN }.count).isEqualTo(2)
    }

    @Test fun `aggregates player leaders by PlayerId preserves ties and ignores unavailable production`() {
        whenever(history.list(OUR)).thenReturn(listOf(
            match("m2",200,"a","A",2,0, players=mapOf("p1" to player("Ana",1,1,true),"p2" to player("Bia",1,0,false))),
            match("m1",100,"a","A",2,0, players=mapOf("p1" to player("Ana",0,0,false),"p2" to player("Bia",0,1,true))),
        ))
        val leaders = service.findByClubId(OUR, ClubId("a"))!!.playerLeaders
        assertThat(leaders.single { it.type == OpponentLeaderType.GOALS }.players.map { it.playerId.value }).containsExactly("p1", "p2")
        assertThat(leaders.single { it.type == OpponentLeaderType.ASSISTS }.players.map { it.playerId.value }).containsExactly("p1", "p2")
        assertThat(leaders.single { it.type == OpponentLeaderType.CRAQUES }.players).hasSize(2)
        assertThat(leaders.none { it.type == OpponentLeaderType.XERIFES }).isTrue()
    }

    @Test fun `missing opponent name remains a separate valid ClubId history`() {
        whenever(history.list(OUR)).thenReturn(listOf(match("unnamed",100,"unknown",null,1,1)))
        val result = service.listOpponents(OUR).single()
        assertThat(result.clubId.value).isEqualTo("unknown")
        assertThat(result.displayName).isEqualTo("Adversário sem nome")
    }

    @Test fun `evidence identifies opponent sources criteria and tie policy`() {
        whenever(history.list(OUR)).thenReturn(listOf(match("m2",200,"a","A",2,0),match("m1",100,"a","A",2,0)))
        val evidence = service.findByClubId(OUR, ClubId("a"))!!.evidence
        assertThat(evidence.opponentClubId.value).isEqualTo("a")
        assertThat(evidence.sourceMatchIds.map { it.value }).containsExactly("m1","m2")
        assertThat(evidence.criteria.map { it.criterion }).contains("retrospecto","maior_vitoria")
        assertThat(evidence.criteria.single { it.criterion == "maior_vitoria" }.tiePolicy).contains("todas")
    }

    @Test fun `same opponent remains isolated for two monitored clubs`() {
        val otherMonitoredClub = ClubId("other-monitored")
        whenever(history.list(OUR)).thenReturn(listOf(match("ours-win", 200, "shared-opponent", "Rival", 3, 0)))
        whenever(history.list(otherMonitoredClub)).thenReturn(listOf(match("their-loss", 100, "shared-opponent", "Rival", 0, 2, perspectiveClubId = otherMonitoredClub)))

        val ours = service.findByClubId(OUR, ClubId("shared-opponent"))!!
        val theirs = service.findByClubId(otherMonitoredClub, ClubId("shared-opponent"))!!

        assertThat(ours.record.wins).isEqualTo(1)
        assertThat(ours.record.losses).isZero()
        assertThat(theirs.record.wins).isZero()
        assertThat(theirs.record.losses).isEqualTo(1)
    }

    private fun player(name:String, goals:Int, assists:Int, motm:Boolean)=PlayerEntry(playerName=name,position="14",rating=if(motm)"9.0" else "6.0",goals="$goals",assists="$assists",shots="2",manOfTheMatch=if(motm)"1" else "0",passesMade="10",passAttempts="12",tacklesMade="1",tackleAttempts="2",secondsPlayed="5400")
    private fun match(id:String,time:Long,opponentId:String,opponentName:String?,ours:Int,theirs:Int,players:Map<String,PlayerEntry> = emptyMap(), perspectiveClubId: ClubId = OUR):CanonicalMatch {
        val source=MatchResponse(id,time,"leagueMatch",mapOf(
            perspectiveClubId.value to ClubMatchEntry(details=ClubDetails("Our",perspectiveClubId.value),name="Our",score=ours.toString()),
            opponentId to ClubMatchEntry(details=ClubDetails(opponentName,opponentId),name=opponentName,score=theirs.toString()),
        ),if(players.isEmpty()) emptyMap() else mapOf(perspectiveClubId.value to players))
        val football=(EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation=MatchInterpreter().interpret(football,perspectiveClubId)
        return CanonicalMatch.current(football,interpretation,MatchStoryExtractor().extract(interpretation),Instant.EPOCH)
    }
    companion object { val OUR=ClubId("ours") }
}
