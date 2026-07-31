package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.FootballMatch

sealed interface MatchNormalizationResult {
    data class Success(
        val match: FootballMatch,
        val warnings: List<NormalizationWarning>,
    ) : MatchNormalizationResult

    data class Rejected(
        val errors: List<NormalizationError>,
        val warnings: List<NormalizationWarning> = emptyList(),
    ) : MatchNormalizationResult {
        init {
            require(errors.isNotEmpty()) { "A rejected normalization must contain at least one error" }
        }
    }
}
