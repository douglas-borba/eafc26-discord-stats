package com.eafc26.discordstats.domain.match

sealed interface PlayerRole {
    data object Goalkeeper : PlayerRole
    data class Outfield(val position: OutfieldPosition?) : PlayerRole
    data object Unknown : PlayerRole
}

enum class OutfieldPosition {
    DEFENDER,
    MIDFIELDER,
    FORWARD,
}
