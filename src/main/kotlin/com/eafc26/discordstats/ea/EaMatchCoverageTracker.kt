package com.eafc26.discordstats.ea

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local observation of the EA match sources returned by the
 * internal gateway. It is diagnostic only: an empty playoff source is not
 * treated as a polling failure because EA legitimately returns an empty window
 * for clubs without a recent match in one competition type.
 */
@Component
class EaMatchCoverageTracker {
    private val latestByClub = ConcurrentHashMap<String, ClubMatchCoverage>()

    fun record(clubId: String, maxResultCount: Int, leagueCount: Int, playoffCount: Int, friendlyCount: Int) {
        latestByClub[clubId] = ClubMatchCoverage(
            clubId = clubId,
            maxResultCount = maxResultCount,
            leagueCount = leagueCount,
            playoffCount = playoffCount,
            friendlyCount = friendlyCount,
            status = if (playoffCount == 0 || friendlyCount == 0) "PARTIAL" else "UP",
            observedAt = Instant.now().toString(),
        )
    }

    fun snapshot(): EaMatchCoverageSnapshot {
        val clubs = latestByClub.values.sortedBy { it.clubId }
        return when {
            clubs.isEmpty() -> EaMatchCoverageSnapshot(
                status = "NOT_OBSERVED",
                observedClubCount = 0,
                clubs = emptyList(),
                message = "Nenhuma aquisição de partidas foi concluída nesta instância.",
            )
            clubs.any { it.status == "PARTIAL" } -> EaMatchCoverageSnapshot(
                status = "PARTIAL",
                observedClubCount = clubs.size,
                clubs = clubs,
                message = "Ao menos uma fonte playoffMatch ou friendlyMatch retornou zero; a EA não confirma a cobertura histórica dessa janela.",
            )
            else -> EaMatchCoverageSnapshot(
                status = "UP",
                observedClubCount = clubs.size,
                clubs = clubs,
                message = "leagueMatch, playoffMatch e friendlyMatch retornaram partidas nas últimas aquisições observadas.",
            )
        }
    }
}

data class EaMatchCoverageSnapshot(
    val status: String,
    val observedClubCount: Int,
    val clubs: List<ClubMatchCoverage>,
    val message: String,
)

data class ClubMatchCoverage(
    val clubId: String,
    val maxResultCount: Int,
    val leagueCount: Int,
    val playoffCount: Int,
    val friendlyCount: Int,
    val status: String,
    val observedAt: String,
)
