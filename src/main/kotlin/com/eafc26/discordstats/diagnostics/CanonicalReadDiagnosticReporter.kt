package com.eafc26.discordstats.diagnostics

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/** Emits one compact, delta-based diagnostic line every ten minutes. */
@Component
class CanonicalReadDiagnosticReporter(
    private val diagnostics: CanonicalReadDiagnostics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var previous: CanonicalReadDiagnosticsSnapshot? = null

    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = INTERVAL_MS)
    @Synchronized
    fun report() {
        val current = diagnostics.snapshot()
        val baseline = previous?.takeIf { it.startedAt == current.startedAt }
        val total = delta(current.total, baseline?.total)
        val operations = CanonicalReadOperation.entries.associate { operation ->
            val name = operation.apiName()
            name to delta(current.operations[name], baseline?.operations?.get(name))
        }
        val period = baseline?.lastUpdatedAt?.let { Duration.between(it, current.lastUpdatedAt ?: it) }
            ?: Duration.ofMillis(INTERVAL_MS)
        log.info(
            "DB_EGRESS_DIAGNOSTIC instance={} period={} calls={} rows={} estimatedReturnedBytes={} findAllCalls={} findRecentCalls={} findByIdCalls={} findMatchIdsCalls={}",
            current.instanceId,
            "${period.toMinutes()}m",
            total.calls,
            total.rows,
            total.estimatedReturnedBytes,
            operations.getValue("findAll").calls,
            operations.getValue("findRecent").calls,
            operations.getValue("findById").calls,
            operations.getValue("findMatchIds").calls,
        )
        previous = current
    }

    private fun delta(current: CanonicalReadMetricSnapshot?, previous: CanonicalReadMetricSnapshot?): CanonicalReadMetricSnapshot {
        val now = current ?: emptyMetric()
        val before = previous ?: emptyMetric()
        return CanonicalReadMetricSnapshot(
            calls = (now.calls - before.calls).coerceAtLeast(0),
            rows = (now.rows - before.rows).coerceAtLeast(0),
            estimatedReturnedBytes = (now.estimatedReturnedBytes - before.estimatedReturnedBytes).coerceAtLeast(0),
            firstObservedAt = now.firstObservedAt,
            lastObservedAt = now.lastObservedAt,
        )
    }

    private fun emptyMetric() = CanonicalReadMetricSnapshot(0, 0, 0, null, null)

    private companion object {
        const val INTERVAL_MS = 600_000L
    }
}
