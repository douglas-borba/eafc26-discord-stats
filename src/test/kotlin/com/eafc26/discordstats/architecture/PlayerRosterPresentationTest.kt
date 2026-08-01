package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class PlayerRosterPresentationTest {
    private val page = Path.of("src/main/resources/players.html").readText()

    @Test
    fun `roster list reuses existing profile presentation data`() {
        assertThat(page)
            .contains("/api/player-profiles", "/api/player-profiles/detail?playerId=")
            .contains("profile?.averageRating", "player.matchCount", "player.latestMatchLabel")
    }

    @Test
    fun `performance bar uses the approved four to ten presentation scale`() {
        assertThat(page)
            .contains("((rating-4)/6)*100")
            .contains("Math.max(0,Math.min(100")
            .contains("performance-track", "performance-fill")
            .doesNotContain("transition:", "animation:", "sparkline")
    }

    @Test
    fun `performance colors follow the approved rating bands`() {
        assertThat(page)
            .contains(
                "if(rating>=8.5) return 'elite'",
                "if(rating>=8) return 'strong'",
                "if(rating>=7) return 'good'",
                "if(rating>=6) return 'regular'",
                "return 'low'",
            )
            .contains(
                ".performance-fill.elite",
                ".performance-fill.strong",
                ".performance-fill.good",
                ".performance-fill.regular",
                ".performance-fill.low",
            )
    }

    @Test
    fun `roster remains compact responsive and keeps the existing profile detail`() {
        assertThat(page)
            .contains("overflow:hidden", "text-overflow:ellipsis", "@media(max-width:420px)")
            .contains("function renderProfile(profile)", "Últimas partidas", "profile.recentMatches")
    }
}
