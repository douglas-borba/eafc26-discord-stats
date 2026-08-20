package com.eafc26.discordstats.diagnostics

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

enum class CanonicalReadOperation {
    FIND_ALL,
    FIND_RECENT,
    FIND_BY_ID,
    FIND_MATCH_IDS,
    FIND_LATEST_MATCH_ID,
    FIND_EXISTING_MATCH_IDS,
}

enum class CanonicalReadOrigin(val value: String) {
    POLLING_CHECKPOINT("polling.checkpoint"),
    DASHBOARD_OVERVIEW("dashboard.overview"),
    HISTORY_LIST("history.list"),
    HISTORY_LATEST("history.latest"),
    LLM_PANORAMA("llm.panorama"),
    LLM_DISCORD("llm.discord"),
    PLAYERS("players"),
    OPPONENTS("opponents"),
    COMPARISON("comparison"),
    ADMIN("admin"),
    UNKNOWN("unknown"),
}

data class CanonicalReadMetricSnapshot(
    val calls: Long,
    val rows: Long,
    val estimatedReturnedBytes: Long,
    val firstObservedAt: Instant?,
    val lastObservedAt: Instant?,
)

data class CanonicalReadDiagnosticsSnapshot(
    val instanceId: String,
    val startedAt: Instant,
    val lastUpdatedAt: Instant?,
    val total: CanonicalReadMetricSnapshot,
    val operations: Map<String, CanonicalReadMetricSnapshot>,
    val origins: Map<String, CanonicalReadMetricSnapshot>,
)

/**
 * Process-local observation of bytes returned by canonical repository reads.
 * These counters deliberately do not represent JDBC protocol bytes or Supabase billing.
 */
@Component
class CanonicalReadDiagnostics {
    private val lock = ReentrantReadWriteLock()
    private val state = AtomicReference(State())
    val instanceId: String = UUID.randomUUID().toString().take(8)

    fun record(
        operation: CanonicalReadOperation,
        origin: CanonicalReadOrigin,
        rows: Int,
        estimatedReturnedBytes: Long,
    ) {
        require(rows >= 0) { "rows must be non-negative" }
        require(estimatedReturnedBytes >= 0) { "estimatedReturnedBytes must be non-negative" }
        lock.read {
            state.get().record(operation, origin, rows.toLong(), estimatedReturnedBytes)
        }
    }

    fun snapshot(): CanonicalReadDiagnosticsSnapshot = lock.read { state.get().snapshot(instanceId) }

    fun reset(): CanonicalReadDiagnosticsSnapshot = lock.write {
        State().also(state::set).snapshot(instanceId)
    }

    private class State {
        private val startedAt = Instant.now()
        private val lastUpdatedAt = AtomicReference<Instant?>(null)
        private val total = Bucket()
        private val operations = ConcurrentHashMap<CanonicalReadOperation, Bucket>()
        private val origins = ConcurrentHashMap<CanonicalReadOrigin, Bucket>()

        fun record(operation: CanonicalReadOperation, origin: CanonicalReadOrigin, rows: Long, bytes: Long) {
            total.add(rows, bytes)
            operations.computeIfAbsent(operation) { Bucket() }.add(rows, bytes)
            origins.computeIfAbsent(origin) { Bucket() }.add(rows, bytes)
            lastUpdatedAt.set(Instant.now())
        }

        fun snapshot(instanceId: String): CanonicalReadDiagnosticsSnapshot = CanonicalReadDiagnosticsSnapshot(
            instanceId = instanceId,
            startedAt = startedAt,
            lastUpdatedAt = lastUpdatedAt.get(),
            total = total.snapshot(),
            operations = operations.entries.associate { (operation, bucket) -> operation.apiName() to bucket.snapshot() }
                .toSortedMap(),
            origins = origins.entries.associate { (origin, bucket) -> origin.value to bucket.snapshot() }
                .toSortedMap(),
        )
    }

    private class Bucket {
        private val calls = AtomicLong()
        private val rows = AtomicLong()
        private val bytes = AtomicLong()
        private val firstObservedAt = AtomicReference<Instant?>(null)
        private val lastObservedAt = AtomicReference<Instant?>(null)

        fun add(rowCount: Long, byteCount: Long) {
            val observedAt = Instant.now()
            firstObservedAt.compareAndSet(null, observedAt)
            lastObservedAt.set(observedAt)
            calls.incrementAndGet()
            rows.addAndGet(rowCount)
            bytes.addAndGet(byteCount)
        }

        fun snapshot() = CanonicalReadMetricSnapshot(
            calls = calls.get(),
            rows = rows.get(),
            estimatedReturnedBytes = bytes.get(),
            firstObservedAt = firstObservedAt.get(),
            lastObservedAt = lastObservedAt.get(),
        )
    }
}

internal fun CanonicalReadOperation.apiName(): String = when (this) {
    CanonicalReadOperation.FIND_ALL -> "findAll"
    CanonicalReadOperation.FIND_RECENT -> "findRecent"
    CanonicalReadOperation.FIND_BY_ID -> "findById"
    CanonicalReadOperation.FIND_MATCH_IDS -> "findMatchIds"
    CanonicalReadOperation.FIND_LATEST_MATCH_ID -> "findLatestMatchId"
    CanonicalReadOperation.FIND_EXISTING_MATCH_IDS -> "findExistingMatchIds"
}

/** Thread-local logical source propagated by application services around synchronous JDBC reads. */
@Component
class CanonicalReadOriginContext {
    private val origin = ThreadLocal<CanonicalReadOrigin?>()

    fun current(): CanonicalReadOrigin = origin.get() ?: CanonicalReadOrigin.UNKNOWN

    fun <T> withOrigin(value: CanonicalReadOrigin, block: () -> T): T {
        val previous = origin.get()
        origin.set(value)
        return try {
            block()
        } finally {
            if (previous == null) origin.remove() else origin.set(previous)
        }
    }
}
