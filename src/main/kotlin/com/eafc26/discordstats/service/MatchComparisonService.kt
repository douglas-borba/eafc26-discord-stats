package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.comparison.AwardMatchDifference
import com.eafc26.discordstats.comparison.ComparedAward
import com.eafc26.discordstats.comparison.ComparedMatch
import com.eafc26.discordstats.comparison.ComparedStatistics
import com.eafc26.discordstats.comparison.ComparedStory
import com.eafc26.discordstats.comparison.ComparisonMetric
import com.eafc26.discordstats.comparison.ComparisonUnit
import com.eafc26.discordstats.comparison.MatchComparison
import com.eafc26.discordstats.comparison.MatchComparisonOption
import com.eafc26.discordstats.comparison.MatchComparisonResult
import com.eafc26.discordstats.comparison.MatchDifferences
import com.eafc26.discordstats.comparison.NumericMatchDifference
import com.eafc26.discordstats.comparison.StoryMatchDifference
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class MatchComparisonService(
    private val matchHistoryService: MatchHistoryService,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) {
    fun listOptions(clubId: ClubId): List<MatchComparisonOption> =
        readOriginContext.withOrigin(CanonicalReadOrigin.COMPARISON) {
            matchHistoryService.list(clubId).map { canonical ->
                val result = canonical.interpretation.result
                val participants = canonical.footballMatch.participants.associateBy { it.club.id }
                MatchComparisonOption(
                    matchId = canonical.matchId,
                    playedAt = canonical.footballMatch.playedAt,
                    outcome = result.outcome,
                    ourClubName = participants[result.ourClub]?.club?.name?.value,
                    opponentClubName = participants[result.opponentClub]?.club?.name?.value,
                    ourScore = result.ourScore.goals,
                    opponentScore = result.opponentScore.goals,
                )
            }
        }

    fun compare(clubId: ClubId, firstMatchId: MatchId, secondMatchId: MatchId): MatchComparisonResult = readOriginContext.withOrigin(CanonicalReadOrigin.COMPARISON) {
        val firstCanonical = matchHistoryService.findById(clubId, firstMatchId)
        val secondCanonical = if (firstMatchId == secondMatchId) {
            firstCanonical
        } else {
            matchHistoryService.findById(clubId, secondMatchId)
        }
        val missing = buildSet {
            if (firstCanonical == null) add(firstMatchId)
            if (secondCanonical == null) add(secondMatchId)
        }
        if (missing.isNotEmpty()) return@withOrigin MatchComparisonResult.NotFound(missing)

        val first = requireNotNull(firstCanonical).compared()
        val second = requireNotNull(secondCanonical).compared()
        MatchComparisonResult.Success(
            MatchComparison(
                first = first,
                second = second,
                differences = differences(first, second),
            )
        )
    }

    private fun CanonicalMatch.compared(): ComparedMatch {
        val result = interpretation.result
        val participants = footballMatch.participants.associateBy { it.club.id }
        val ourClub = participants[result.ourClub]
        val opponent = participants[result.opponentClub]
        val players = ourClub?.players.orEmpty()
        val namesById = players.associate { performance ->
            performance.player.id to (
                performance.player.preferredDisplayName?.value ?: performance.player.id.value
            )
        }
        val awards = interpretation.awards.all()

        return ComparedMatch(
            matchId = matchId,
            playedAt = footballMatch.playedAt,
            competition = footballMatch.competition,
            outcome = result.outcome,
            ourClubName = ourClub?.club?.name?.value,
            opponentClubName = opponent?.club?.name?.value,
            ourScore = result.ourScore.goals,
            opponentScore = result.opponentScore.goals,
            statistics = ComparedStatistics(
                averageRating = interpretation.teamMetrics.averageRating,
                goals = interpretation.teamMetrics.totalGoals,
                assists = interpretation.teamMetrics.totalAssists,
                shots = players.sumKnown { it.attacking.shots },
                passesCompleted = interpretation.teamMetrics.passing.completed,
                passesAttempted = interpretation.teamMetrics.passing.attempted,
                passAccuracyPercent = interpretation.teamMetrics.passing.accuracy
                    ?.decimal
                    ?.multiply(BigDecimal("100")),
                tacklesCompleted = players.sumKnown { it.defending.tacklesCompleted },
                tacklesAttempted = players.sumKnown { it.defending.tacklesAttempted },
                redCards = players.sumKnown { it.discipline.redCards },
                goalkeeperSaves = players.sumKnown { it.goalkeeping?.saves },
            ),
            awards = awards.map { award ->
                ComparedAward(
                    type = award.type,
                    winnerId = award.winnerId,
                    winnerName = award.winnerId?.let(namesById::get),
                    reason = award.reason,
                )
            },
            stories = stories.stories.map { story ->
                ComparedStory(
                    story = story,
                    involvedPlayerNames = story.involvedPlayers.associateWith {
                        namesById[it] ?: it.value
                    },
                )
            },
        )
    }

    private fun differences(first: ComparedMatch, second: ComparedMatch): MatchDifferences {
        val numeric = buildList {
            add(numeric(ComparisonMetric.GOALS_SCORED, first.ourScore, second.ourScore))
            add(numeric(ComparisonMetric.GOALS_CONCEDED, first.opponentScore, second.opponentScore))
            add(numeric(ComparisonMetric.TEAM_AVERAGE_RATING, first.statistics.averageRating, second.statistics.averageRating, ComparisonUnit.RATING))
            add(numeric(ComparisonMetric.TEAM_GOALS, first.statistics.goals, second.statistics.goals))
            add(numeric(ComparisonMetric.TEAM_ASSISTS, first.statistics.assists, second.statistics.assists))
            add(numeric(ComparisonMetric.SHOTS, first.statistics.shots, second.statistics.shots))
            add(numeric(ComparisonMetric.PASSES_COMPLETED, first.statistics.passesCompleted, second.statistics.passesCompleted))
            add(numeric(ComparisonMetric.PASSES_ATTEMPTED, first.statistics.passesAttempted, second.statistics.passesAttempted))
            add(numeric(ComparisonMetric.PASS_ACCURACY_PERCENT, first.statistics.passAccuracyPercent, second.statistics.passAccuracyPercent, ComparisonUnit.PERCENTAGE_POINTS))
            add(numeric(ComparisonMetric.TACKLES_COMPLETED, first.statistics.tacklesCompleted, second.statistics.tacklesCompleted))
            add(numeric(ComparisonMetric.TACKLES_ATTEMPTED, first.statistics.tacklesAttempted, second.statistics.tacklesAttempted))
            add(numeric(ComparisonMetric.RED_CARDS, first.statistics.redCards, second.statistics.redCards))
            add(numeric(ComparisonMetric.GOALKEEPER_SAVES, first.statistics.goalkeeperSaves, second.statistics.goalkeeperSaves))
            add(numeric(ComparisonMetric.POSSESSION_PERCENT, first.statistics.possessionPercent, second.statistics.possessionPercent, ComparisonUnit.PERCENTAGE_POINTS))
        }
        val firstAwards = first.awards.associateBy { it.type }
        val secondAwards = second.awards.associateBy { it.type }
        val awardDifferences = AwardType.entries.map { type ->
            val firstAward = firstAwards[type]
            val secondAward = secondAwards[type]
            AwardMatchDifference(
                awardType = type,
                firstWinnerId = firstAward?.winnerId,
                firstWinnerName = firstAward?.winnerName,
                secondWinnerId = secondAward?.winnerId,
                secondWinnerName = secondAward?.winnerName,
                changed = firstAward?.winnerId != secondAward?.winnerId,
            )
        }
        val storyDifferences = StoryType.entries.map { type ->
            val firstCount = first.stories.count { it.story.type == type }
            val secondCount = second.stories.count { it.story.type == type }
            StoryMatchDifference(type, firstCount, secondCount, secondCount - firstCount)
        }
        return MatchDifferences(numeric, awardDifferences, storyDifferences)
    }

    private fun numeric(
        metric: ComparisonMetric,
        first: Int?,
        second: Int?,
        unit: ComparisonUnit = ComparisonUnit.COUNT,
    ) = numeric(metric, first?.toBigDecimal(), second?.toBigDecimal(), unit)

    private fun numeric(
        metric: ComparisonMetric,
        first: BigDecimal?,
        second: BigDecimal?,
        unit: ComparisonUnit,
    ) = NumericMatchDifference(
        metric = metric,
        firstValue = first,
        secondValue = second,
        delta = if (first != null && second != null) second.subtract(first) else null,
        unit = unit,
    )

    private fun List<PlayerMatchPerformance>.sumKnown(
        selector: (PlayerMatchPerformance) -> Int?,
    ): Int? {
        val values = mapNotNull(selector)
        return values.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun com.eafc26.discordstats.domain.interpretation.MatchAwards.all(): List<AwardDecision> =
        listOf(craque, bagre, xerife)
}
