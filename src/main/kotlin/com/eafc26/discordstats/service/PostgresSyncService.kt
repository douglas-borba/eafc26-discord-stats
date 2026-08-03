package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

open class PostgresSyncService(
    private val jsonRepository: JsonCanonicalMatchRepository,
    private val postgresRepository: CanonicalMatchRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile private var _lastSyncStarted: Instant? = null
    val lastSyncStarted: Instant? get() = _lastSyncStarted

    @Volatile private var _lastSyncCompleted: Instant? = null
    val lastSyncCompleted: Instant? get() = _lastSyncCompleted

    @Volatile private var _lastSyncFailed: Instant? = null
    val lastSyncFailed: Instant? get() = _lastSyncFailed

    @Volatile private var _lastSyncResult: PostgresSyncResult? = null
    val lastSyncResult: PostgresSyncResult? get() = _lastSyncResult

    open fun sync(): PostgresSyncResult {
        _lastSyncStarted = Instant.now()
        val start = System.nanoTime()

        return try {
            val result = doSync(start)
            _lastSyncCompleted = Instant.now()
            _lastSyncResult = result
            log.info(
                "PostgreSQL sync completed: found={} synced={} failures={} duration={}ms",
                result.found, result.synced, result.failures.size, result.durationMs,
            )
            result
        } catch (ex: Exception) {
            _lastSyncFailed = Instant.now()
            val elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis()
            val result = PostgresSyncResult(
                found = 0, synced = 0, upToDate = 0,
                failures = listOf(PostgresSyncFailure("*", ex.message ?: ex.javaClass.simpleName)),
                durationMs = elapsed, localCount = 0, remoteCount = null,
            )
            _lastSyncResult = result
            log.error("PostgreSQL sync failed after {}ms", elapsed, ex)
            result
        }
    }

    private fun doSync(startNanos: Long): PostgresSyncResult {
        val allLocal = jsonRepository.findAll()
        var synced = 0
        val failures = mutableListOf<PostgresSyncFailure>()

        allLocal.forEach { match ->
            try {
                postgresRepository.save(match)
                synced++
            } catch (ex: Exception) {
                log.error("Sync failed for match {}", match.matchId.value, ex)
                failures += PostgresSyncFailure(match.matchId.value, ex.message ?: ex.javaClass.simpleName)
            }
        }

        val remoteCount = try {
            postgresRepository.metadata().matchCount
        } catch (_: Exception) {
            null
        }

        val elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis()

        return PostgresSyncResult(
            found = allLocal.size,
            synced = synced,
            upToDate = 0,
            failures = failures,
            durationMs = elapsed,
            localCount = allLocal.size,
            remoteCount = remoteCount,
        )
    }
}

data class PostgresSyncResult(
    val found: Int,
    val synced: Int,
    val upToDate: Int,
    val failures: List<PostgresSyncFailure>,
    val durationMs: Long,
    val localCount: Int,
    val remoteCount: Int?,
) {
    val pendingEstimate: Int? get() = remoteCount?.let { localCount - it }
}

data class PostgresSyncFailure(
    val matchId: String,
    val message: String,
)
