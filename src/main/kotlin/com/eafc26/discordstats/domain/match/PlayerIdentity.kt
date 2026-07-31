package com.eafc26.discordstats.domain.match

@JvmInline
value class PlayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlayerId must not be blank" }
    }
}

@JvmInline
value class DisplayName(val value: String) {
    init {
        require(value.isNotBlank()) { "DisplayName must not be blank" }
    }
}

data class PlayerIdentity(
    val id: PlayerId,
    val platformName: DisplayName?,
    val proName: DisplayName?,
) {
    val preferredDisplayName: DisplayName?
        get() = proName ?: platformName
}
