package com.eafc26.discordstats.domain.match

@JvmInline
value class ClubId(val value: String) {
    init {
        require(value.isNotBlank()) { "ClubId must not be blank" }
    }
}

@JvmInline
value class ClubName(val value: String) {
    init {
        require(value.isNotBlank()) { "ClubName must not be blank" }
    }
}

data class ClubIdentity(
    val id: ClubId,
    val name: ClubName?,
)
