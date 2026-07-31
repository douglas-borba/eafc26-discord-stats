package com.eafc26.discordstats.ea.mapping

import java.math.BigDecimal

internal class EaStatParser(
    private val warnings: MutableList<NormalizationWarning>,
) {
    fun nonNegativeInt(raw: String?, path: String): Int? {
        if (raw == null) return null
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            warn(
                NormalizationIssueCode.INVALID_INTEGER,
                path,
                "Blank numeric value treated as absent",
                raw,
            )
            return null
        }

        val parsed = normalized.toIntOrNull()
        if (parsed == null) {
            warn(
                NormalizationIssueCode.INVALID_INTEGER,
                path,
                "Invalid integer treated as absent",
                raw,
            )
            return null
        }
        if (parsed < 0) {
            warn(
                NormalizationIssueCode.NEGATIVE_STATISTIC,
                path,
                "Negative statistic treated as absent",
                raw,
            )
            return null
        }
        return parsed
    }

    fun nonNegativeDecimal(raw: String?, path: String): BigDecimal? {
        if (raw == null) return null
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            warn(
                NormalizationIssueCode.INVALID_DECIMAL,
                path,
                "Blank decimal value treated as absent",
                raw,
            )
            return null
        }

        val parsed = normalized.toBigDecimalOrNull()
        if (parsed == null) {
            warn(
                NormalizationIssueCode.INVALID_DECIMAL,
                path,
                "Invalid decimal treated as absent",
                raw,
            )
            return null
        }
        if (parsed < BigDecimal.ZERO) {
            warn(
                NormalizationIssueCode.NEGATIVE_RATING,
                path,
                "Negative rating treated as absent",
                raw,
            )
            return null
        }
        return parsed
    }

    fun booleanFlag(raw: String?, path: String): Boolean? {
        if (raw == null) return null
        return when (raw.trim().lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> {
                warn(
                    NormalizationIssueCode.INVALID_BOOLEAN_FLAG,
                    path,
                    "Invalid boolean flag treated as absent",
                    raw,
                )
                null
            }
        }
    }

    fun completedAttempts(
        attempted: Int?,
        completed: Int?,
        path: String,
    ): Pair<Int?, Int?> {
        if (attempted == null || completed == null || completed <= attempted) {
            return attempted to completed
        }

        warn(
            NormalizationIssueCode.COMPLETED_EXCEEDS_ATTEMPTED,
            path,
            "Completed count clamped to attempted count",
            "$completed/$attempted",
        )
        return attempted to attempted
    }

    private fun warn(
        code: NormalizationIssueCode,
        path: String,
        message: String,
        rawValue: String?,
    ) {
        warnings += NormalizationWarning(code, path, message, rawValue)
    }
}
