package com.eafc26.discordstats.domain.interpretation

@JvmInline
value class RuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "RuleId must not be blank" }
    }
}

data class RuleReference(
    val id: RuleId,
    val version: Int,
) {
    init {
        require(version >= 1) { "Rule version must be at least 1" }
    }
}
