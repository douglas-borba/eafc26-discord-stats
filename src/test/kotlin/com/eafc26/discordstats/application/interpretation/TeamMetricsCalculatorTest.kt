package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.MatchRating
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TeamMetricsCalculatorTest {

    private val calculator = TeamMetricsCalculator()

    @Test
    fun `calculates average rating and aggregate attacking totals`() {
        val metrics = calculator.calculate(
            listOf(
                player("one", rating = "8.0", goals = 2, assists = 1),
                player("two", rating = "7.0", goals = 1, assists = 2),
            )
        )

        assertThat(metrics.averageRating).isEqualByComparingTo("7.500000")
        assertThat(metrics.totalGoals).isEqualTo(3)
        assertThat(metrics.totalAssists).isEqualTo(3)
    }

    @Test
    fun `team passing uses weighted totals rather than average player percentages`() {
        val metrics = calculator.calculate(
            listOf(
                player("high-volume", passAttempts = 90, passesMade = 72),
                player("low-volume", passAttempts = 10, passesMade = 10),
            )
        )

        assertThat(metrics.passing.attempted).isEqualTo(100)
        assertThat(metrics.passing.completed).isEqualTo(82)
        assertThat(metrics.passing.accuracy!!.decimal).isEqualByComparingTo("0.820000")
    }

    @Test
    fun `missing ratings are excluded from average`() {
        val metrics = calculator.calculate(
            listOf(
                player("rated", rating = "8.0"),
                player("unrated", rating = null),
            )
        )

        assertThat(metrics.averageRating).isEqualByComparingTo("8.000000")
    }

    @Test
    fun `players without a complete positive passing sample are excluded from passing totals`() {
        val metrics = calculator.calculate(
            listOf(
                player("valid", passAttempts = 20, passesMade = 15),
                player("unknown-made", passAttempts = 10, passesMade = null),
                player("zero", passAttempts = 0, passesMade = 0),
            )
        )

        assertThat(metrics.passing.attempted).isEqualTo(20)
        assertThat(metrics.passing.completed).isEqualTo(15)
    }

    @Test
    fun `entirely unknown metrics remain unknown instead of becoming zero`() {
        val metrics = calculator.calculate(
            listOf(
                player(
                    "unknown",
                    rating = null,
                    goals = null,
                    assists = null,
                    passAttempts = null,
                    passesMade = null,
                )
            )
        )

        assertThat(metrics.averageRating).isNull()
        assertThat(metrics.totalGoals).isNull()
        assertThat(metrics.totalAssists).isNull()
        assertThat(metrics.passing.attempted).isNull()
        assertThat(metrics.passing.completed).isNull()
    }

    @Test
    fun `known zero attacking totals remain distinguishable from unknown totals`() {
        val metrics = calculator.calculate(
            listOf(player("known-zero", goals = 0, assists = 0))
        )

        assertThat(metrics.totalGoals).isZero()
        assertThat(metrics.totalAssists).isZero()
    }

    @Test
    fun `empty team produces absent aggregate metrics`() {
        val metrics = calculator.calculate(emptyList())

        assertThat(metrics.averageRating).isNull()
        assertThat(metrics.totalGoals).isNull()
        assertThat(metrics.totalAssists).isNull()
        assertThat(metrics.passing.attempted).isNull()
    }

    private fun player(
        id: String,
        rating: String? = "7.0",
        goals: Int? = 0,
        assists: Int? = 0,
        passAttempts: Int? = 20,
        passesMade: Int? = 15,
    ): PlayerMatchPerformance = PlayerMatchPerformance(
        player = PlayerIdentity(PlayerId(id), platformName = null, proName = null),
        role = PlayerRole.Outfield(position = null),
        participation = Participation(duration = null, status = null),
        rating = rating?.let { MatchRating(BigDecimal(it)) },
        attacking = AttackingStats(goals, assists, shots = null),
        passing = PassingStats(passAttempts, passesMade),
        defending = DefendingStats(null, null),
        discipline = DisciplineStats(null),
        goalkeeping = null,
        eaRecognition = EaRecognition(null),
    )
}
