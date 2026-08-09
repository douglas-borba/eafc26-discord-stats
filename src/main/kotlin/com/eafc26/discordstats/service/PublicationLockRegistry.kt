package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Serializes one publication identity without coupling equal MatchIds across clubs. */
@Component
class PublicationLockRegistry {
    private val locks = ConcurrentHashMap<PublicationLockKey, ReentrantLock>()

    fun <T> withLock(clubId: ClubId, matchId: String, action: () -> T): T =
        locks.computeIfAbsent(PublicationLockKey(clubId, matchId)) { ReentrantLock() }
            .withLock(action)

    private data class PublicationLockKey(val clubId: ClubId, val matchId: String)
}
