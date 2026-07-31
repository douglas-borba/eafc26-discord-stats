package com.eafc26.discordstats.domain.match

import java.time.Duration

data class Participation(
    val duration: Duration?,
    val status: ParticipationStatus?,
) {
    init {
        require(duration == null || !duration.isNegative) {
            "Participation duration must not be negative"
        }
    }
}

enum class ParticipationStatus {
    COMPLETED,
    DISCONNECTED,
    REPLACED,
    UNKNOWN,
}
