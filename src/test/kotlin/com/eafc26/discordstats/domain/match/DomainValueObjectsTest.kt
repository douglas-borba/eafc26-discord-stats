package com.eafc26.discordstats.domain.match

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

class DomainValueObjectsTest {

    @Nested
    inner class Identities {

        @Test
        fun `identity values preserve valid source identifiers`() {
            assertThat(MatchId("match-1").value).isEqualTo("match-1")
            assertThat(ClubId("club-1").value).isEqualTo("club-1")
            assertThat(PlayerId("player-1").value).isEqualTo("player-1")
        }

        @Test
        fun `identity values reject blank identifiers`() {
            assertThatThrownBy { MatchId(" ") }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { ClubId("") }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { PlayerId("\t") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `names reject blank values`() {
            assertThatThrownBy { ClubName(" ") }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { DisplayName("") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `player identity prefers pro name without changing canonical identity`() {
            val identity = PlayerIdentity(
                id = PlayerId("player-1"),
                platformName = DisplayName("platform_tag"),
                proName = DisplayName("Camisa 10"),
            )

            assertThat(identity.id).isEqualTo(PlayerId("player-1"))
            assertThat(identity.preferredDisplayName).isEqualTo(DisplayName("Camisa 10"))
        }

        @Test
        fun `player identity falls back to platform name and permits an unknown display name`() {
            val platformOnly = PlayerIdentity(
                id = PlayerId("player-1"),
                platformName = DisplayName("platform_tag"),
                proName = null,
            )
            val unnamed = PlayerIdentity(
                id = PlayerId("player-2"),
                platformName = null,
                proName = null,
            )

            assertThat(platformOnly.preferredDisplayName).isEqualTo(DisplayName("platform_tag"))
            assertThat(unnamed.preferredDisplayName).isNull()
        }
    }

    @Nested
    inner class TypedValues {

        @Test
        fun `score and rating preserve typed numeric values`() {
            assertThat(Score(3).goals).isEqualTo(3)
            assertThat(MatchRating(BigDecimal("8.75")).value).isEqualByComparingTo("8.75")
        }

        @Test
        fun `score and rating reject negative values`() {
            assertThatThrownBy { Score(-1) }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { MatchRating(BigDecimal("-0.1")) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `participation retains unknown duration and rejects negative duration`() {
            assertThat(Participation(null, ParticipationStatus.UNKNOWN).duration).isNull()
            assertThatThrownBy {
                Participation(Duration.ofSeconds(-1), ParticipationStatus.COMPLETED)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `nullable statistics distinguish unknown values from zero`() {
            val unknown = AttackingStats(goals = null, assists = null, shots = null)
            val zero = AttackingStats(goals = 0, assists = 0, shots = 0)

            assertThat(unknown.goals).isNull()
            assertThat(zero.goals).isZero()
            assertThat(unknown).isNotEqualTo(zero)
        }

        @Test
        fun `statistic groups reject negative counts`() {
            assertThatThrownBy { AttackingStats(goals = -1, assists = 0, shots = 0) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { DisciplineStats(redCards = -1) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy {
                SaveBreakdown(-1, 0, 0, 0, 0, 0)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `completed attempts cannot exceed total attempts`() {
            assertThatThrownBy { PassingStats(attempted = 10, completed = 11) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { DefendingStats(tacklesAttempted = 4, tacklesCompleted = 5) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
