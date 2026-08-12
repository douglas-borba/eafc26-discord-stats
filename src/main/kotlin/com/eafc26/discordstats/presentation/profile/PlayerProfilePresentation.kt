package com.eafc26.discordstats.presentation.profile

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import com.eafc26.discordstats.profile.PlayerProfileMatch
import com.eafc26.discordstats.presentation.MatchPresentationTimeZone
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlayerProfileListResponse(
    val status: String,
    val players: List<PlayerProfileListItem>,
)

data class PlayerProfileResponse(
    val status: String,
    val profile: PlayerProfileView? = null,
    val message: String? = null,
)

data class PlayerProfileListItem(
    val playerId: String,
    val name: String,
    val matchCount: Int,
    val latestMatchAt: Instant,
    val latestMatchLabel: String,
)

data class PlayerProfileView(
    val playerId: String,
    val name: String,
    val matchCount: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val averageRating: BigDecimal?,
    val ratedMatchCount: Int,
    val goals: Int,
    val assists: Int,
    val craques: Int,
    val bagres: Int,
    val xerifes: Int,
    val redCards: Int,
    val shots: Int,
    val passesCompleted: Int,
    val passesAttempted: Int,
    val tacklesCompleted: Int,
    val tacklesAttempted: Int,
    val recentMatches: List<PlayerProfileMatchView>,
)

data class PlayerProfileMatchView(
    val matchId: String,
    val playedAt: Instant,
    val dateLabel: String,
    val competition: String?,
    val ourClubName: String,
    val opponentClubName: String,
    val ourScore: Int,
    val opponentScore: Int,
    val outcomeCode: String,
    val outcomeLabel: String,
    val outcomeIcon: String,
    val rating: BigDecimal?,
    val goals: Int?,
    val assists: Int?,
    val awards: List<String>,
)

object PlayerProfilePresenter {
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy 'às' HH:mm",
        Locale.forLanguageTag("pt-BR"),
    )

    fun listItem(
        entry: PlayerProfileIndexEntry,
        zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL,
    ) = PlayerProfileListItem(
        playerId = entry.playerId.value,
        name = entry.displayName,
        matchCount = entry.matchCount,
        latestMatchAt = entry.latestMatchAt,
        latestMatchLabel = dateFormatter.withZone(zoneId).format(entry.latestMatchAt),
    )

    fun profile(
        profile: PlayerProfile,
        zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL,
    ) = PlayerProfileView(
        playerId = profile.playerId.value,
        name = profile.displayName,
        matchCount = profile.matchCount,
        wins = profile.wins,
        draws = profile.draws,
        losses = profile.losses,
        averageRating = profile.averageRating,
        ratedMatchCount = profile.ratedMatchCount,
        goals = profile.goals,
        assists = profile.assists,
        craques = profile.craques,
        bagres = profile.bagres,
        xerifes = profile.xerifes,
        redCards = profile.redCards,
        shots = profile.shots,
        passesCompleted = profile.passesCompleted,
        passesAttempted = profile.passesAttempted,
        tacklesCompleted = profile.tacklesCompleted,
        tacklesAttempted = profile.tacklesAttempted,
        recentMatches = profile.recentMatches.map { it.presentation(zoneId) },
    )

    private fun PlayerProfileMatch.presentation(zoneId: ZoneId): PlayerProfileMatchView {
        val outcomePresentation = outcome.presentation()
        return PlayerProfileMatchView(
            matchId = matchId.value,
            playedAt = playedAt,
            dateLabel = dateFormatter.withZone(zoneId).format(playedAt),
            competition = competition?.label(),
            ourClubName = ourClubName ?: "Nosso clube",
            opponentClubName = opponentClubName ?: "Adversário",
            ourScore = ourScore,
            opponentScore = opponentScore,
            outcomeCode = outcome.name,
            outcomeLabel = outcomePresentation.first,
            outcomeIcon = outcomePresentation.second,
            rating = rating,
            goals = goals,
            assists = assists,
            awards = awards.sortedBy(AwardType::ordinal).map { it.label() },
        )
    }

    private fun MatchOutcome.presentation() = when (this) {
        MatchOutcome.WIN -> "Vitória" to "🏆"
        MatchOutcome.DRAW -> "Empate" to "🤝"
        MatchOutcome.LOSS -> "Derrota" to "📉"
    }

    private fun CompetitionType.label() = when (this) {
        CompetitionType.FRIENDLY -> "Amistoso"
        CompetitionType.LEAGUE -> "Liga"
        CompetitionType.PLAYOFF -> "Playoff"
    }

    private fun AwardType.label() = when (this) {
        AwardType.CRAQUE -> "⭐ Craque"
        AwardType.BAGRE -> "🐟 Bagre"
        AwardType.XERIFE -> "🛡️ Xerife"
    }
}
