package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class EmptyMatchInterpretationTest {

    @Test
    fun `match without players remains fully interpretable and auditable`() {
        val ours = ClubId("ours")
        val match = FootballMatch(
            MatchId("empty-match"),
            Instant.EPOCH,
            null,
            listOf(
                ClubMatchPerformance(
                    ClubIdentity(ours, ClubName("Ours")),
                    Score(0),
                    null,
                    emptyList(),
                ),
                ClubMatchPerformance(
                    ClubIdentity(ClubId("opponent"), ClubName("Opponent")),
                    Score(0),
                    null,
                    emptyList(),
                ),
            ),
        )

        val interpretation = MatchInterpreter().interpret(match, ours)

        assertThat(interpretation.awards.craque.awarded).isFalse()
        assertThat(interpretation.awards.bagre.awarded).isFalse()
        assertThat(interpretation.awards.xerife.awarded).isFalse()
        assertThat(interpretation.awards.craque.evidence).isNotEmpty()
        assertThat(interpretation.features.contributions.evidence).isNotEmpty()
        assertThat(interpretation.features.highlights.evidence).isNotEmpty()
    }
}
