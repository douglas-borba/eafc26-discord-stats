package com.eafc26.discordstats.scheduler

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** In-memory diagnostic of the latest lightweight reconciliation cycle. */
@Component
class PublicationReconciliationStatusHolder {
    private val status = AtomicReference(PublicationReconciliationStatus())

    fun current(): PublicationReconciliationStatus = status.get()

    fun complete(startedAt: Instant, claimed: Int, completed: Int, failed: Int) {
        status.set(
            PublicationReconciliationStatus(
                lastRunAt = startedAt,
                lastResult = "COMPLETED",
                claimed = claimed,
                completed = completed,
                failed = failed,
            ),
        )
    }

    fun fail(startedAt: Instant, errorType: String) {
        status.set(PublicationReconciliationStatus(lastRunAt = startedAt, lastResult = "FAILED", errorType = errorType))
    }
}

data class PublicationReconciliationStatus(
    val lastRunAt: Instant? = null,
    val lastResult: String? = null,
    val claimed: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val errorType: String? = null,
)
