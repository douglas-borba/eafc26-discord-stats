package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import java.time.Instant

data class OperationalEvent(
    val id: Long? = null,
    val clubId: ClubId? = null,
    val matchId: String? = null,
    val eventType: String,
    val phase: String? = null,
    val status: EventStatus,
    val message: String? = null,
    val errorCode: String? = null,
    val durationMs: Long? = null,
    val createdAt: Instant = Instant.now(),
)

enum class EventStatus { SUCCESS, FAILURE, WARNING, INFO }
