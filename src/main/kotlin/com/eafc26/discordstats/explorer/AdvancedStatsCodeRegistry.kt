package com.eafc26.discordstats.explorer

enum class CodeConfidence {
    CONFIRMED,
    HIGH_CONFIDENCE,
    HYPOTHESIS,
    UNKNOWN,
}

data class CodeMapping(
    val aggregate: Int,
    val code: Int,
    val metricName: String?,
    val confidence: CodeConfidence,
    val evidence: String? = null,
)

object AdvancedStatsCodeRegistry {

    private val mappings: List<CodeMapping> = listOf(
        CodeMapping(0, 115, "Pre-assists", CodeConfidence.CONFIRMED, "Validated against EA gameplay data"),
        CodeMapping(0, 152, "Through passes", CodeConfidence.CONFIRMED, "Validated against EA gameplay data"),
        // Code 174 reproduces a third-party UI label, but EA has not documented
        // its football meaning. Keep it visible as raw evidence, not a known stat.
        CodeMapping(0, 174, null, CodeConfidence.UNKNOWN, "EA football semantics unverified"),
        CodeMapping(0, 112, "Beats", CodeConfidence.CONFIRMED, "Validated against EA gameplay data"),
        CodeMapping(0, 6, null, CodeConfidence.HYPOTHESIS, "Possible interception-related counter — NOT VALIDATED"),
        CodeMapping(1, 6, null, CodeConfidence.HYPOTHESIS, "Possible interception-related counter — NOT VALIDATED"),
    )

    private val index: Map<Pair<Int, Int>, CodeMapping> =
        mappings.associateBy { it.aggregate to it.code }

    fun lookup(aggregate: Int, code: Int): CodeMapping =
        index[aggregate to code] ?: CodeMapping(aggregate, code, null, CodeConfidence.UNKNOWN)

    fun isKnown(aggregate: Int, code: Int): Boolean {
        val mapping = index[aggregate to code] ?: return false
        return mapping.confidence == CodeConfidence.CONFIRMED || mapping.confidence == CodeConfidence.HIGH_CONFIDENCE
    }

    fun allMappings(): List<CodeMapping> = mappings.toList()
}
