package com.eafc26.discordstats.ea.mapping

enum class NormalizationIssueCode {
    BLANK_MATCH_ID,
    INVALID_TIMESTAMP,
    INSUFFICIENT_CLUBS,
    BLANK_CLUB_ID,
    MISSING_CLUB_NAME,
    INVALID_COMPETITION_TYPE,
    INVALID_INTEGER,
    NEGATIVE_STATISTIC,
    INVALID_DECIMAL,
    NEGATIVE_RATING,
    INVALID_BOOLEAN_FLAG,
    INVALID_REPORTED_RESULT,
    SCORE_FALLBACK_TO_GOALS_AGAINST,
    SCORE_FALLBACK_TO_ZERO,
    SCORE_GOALS_AGAINST_CONFLICT,
    COMPLETED_EXCEEDS_ATTEMPTED,
    BLANK_PLAYER_ID,
    MISSING_PLAYER_NAME,
    UNKNOWN_PLAYER_CLUB,
    INVALID_MATCH_COMPLETION,
}

sealed interface NormalizationIssue {
    val code: NormalizationIssueCode
    val path: String
    val message: String
    val rawValue: String?
}

data class NormalizationWarning(
    override val code: NormalizationIssueCode,
    override val path: String,
    override val message: String,
    override val rawValue: String? = null,
) : NormalizationIssue

data class NormalizationError(
    override val code: NormalizationIssueCode,
    override val path: String,
    override val message: String,
    override val rawValue: String? = null,
) : NormalizationIssue
