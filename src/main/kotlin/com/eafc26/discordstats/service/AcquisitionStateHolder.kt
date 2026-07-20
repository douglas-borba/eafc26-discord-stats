package com.eafc26.discordstats.service

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the current state of match acquisition.
 *
 * This component is the single source of truth for acquisition state.
 * It is updated exclusively by [MatchAcquisitionService] during acquisition.
 *
 * The state model supports acquisitions from any trigger:
 * - Scheduler
 * - Manual endpoint
 * - CLI
 * - Development simulator
 *
 * State is NOT derived from [AcquisitionLock]. Instead, [MatchAcquisitionService]
 * explicitly updates the holder at each phase transition.
 *
 * Thread-safety is achieved via [AtomicReference] for the immutable [AcquisitionState].
 */
@Component
class AcquisitionStateHolder {

    private val stateRef = AtomicReference(AcquisitionState.idle())

    /**
     * Returns a snapshot of the current acquisition state.
     */
    fun current(): AcquisitionState = stateRef.get()

    /**
     * Starts a new acquisition. Called when the lock is acquired.
     *
     * @param trigger The origin of the acquisition request.
     * @return The new execution ID.
     */
    fun start(trigger: AcquisitionTrigger): String {
        val executionId = UUID.randomUUID().toString().take(8)
        val newState = AcquisitionState(
            executionId = executionId,
            trigger = trigger,
            currentPhase = AcquisitionPhase.FETCHING,
            completedPhases = emptyList(),
            startedAt = Instant.now(),
            finishedAt = null,
            lastError = null,
            currentStatus = "Iniciando aquisição...",
        )
        stateRef.set(newState)
        return executionId
    }

    /**
     * Transitions to the next phase.
     *
     * @param phase The phase being entered.
     * @param status Human-readable status message.
     */
    fun enterPhase(phase: AcquisitionPhase, status: String) {
        stateRef.updateAndGet { current ->
            val completed = if (current.currentPhase != AcquisitionPhase.IDLE &&
                current.currentPhase != AcquisitionPhase.COMPLETED &&
                current.currentPhase != AcquisitionPhase.FAILED
            ) {
                current.completedPhases + current.currentPhase
            } else {
                current.completedPhases
            }
            current.copy(
                currentPhase = phase,
                completedPhases = completed,
                currentStatus = status,
            )
        }
    }

    /**
     * Marks the acquisition as successfully completed.
     *
     * @param status Final status message.
     */
    fun complete(status: String) {
        stateRef.updateAndGet { current ->
            val completed = if (current.currentPhase != AcquisitionPhase.IDLE) {
                current.completedPhases + current.currentPhase
            } else {
                current.completedPhases
            }
            current.copy(
                currentPhase = AcquisitionPhase.COMPLETED,
                completedPhases = completed,
                finishedAt = Instant.now(),
                currentStatus = status,
            )
        }
    }

    /**
     * Marks the acquisition as failed.
     *
     * @param error Description of the failure.
     * @param status Human-readable status message.
     */
    fun fail(error: String, status: String) {
        stateRef.updateAndGet { current ->
            current.copy(
                currentPhase = AcquisitionPhase.FAILED,
                finishedAt = Instant.now(),
                lastError = error,
                currentStatus = status,
            )
        }
    }

    /**
     * Records that an acquisition attempt was rejected because another is in progress.
     *
     * @param trigger The trigger that was rejected.
     */
    fun recordBusy(trigger: AcquisitionTrigger) {
        // Don't overwrite the current running state.
        // Just update the status to reflect that a request was rejected.
        stateRef.updateAndGet { current ->
            // Only update if there's actually a running acquisition
            if (current.isRunning()) {
                current.copy(
                    currentStatus = "Aquisição em andamento. Requisição ${trigger.name} rejeitada.",
                )
            } else {
                current
            }
        }
    }

    // Note: reset() was removed intentionally.
    // The final COMPLETED or FAILED state is kept until the next acquisition starts.
    // This preserves visibility into the last acquisition's outcome.
}

/**
 * Immutable snapshot of acquisition state.
 */
data class AcquisitionState(
    /** Unique identifier for this acquisition execution. Null when idle. */
    val executionId: String?,

    /** The trigger that initiated this acquisition. Null if never run. */
    val trigger: AcquisitionTrigger?,

    /** Current phase of the acquisition pipeline. */
    val currentPhase: AcquisitionPhase,

    /** Phases that have been completed in this execution. */
    val completedPhases: List<AcquisitionPhase>,

    /** When this acquisition started. Null when idle. */
    val startedAt: Instant?,

    /** When this acquisition finished. Null if still running or never run. */
    val finishedAt: Instant?,

    /** Error message if the last acquisition failed. */
    val lastError: String?,

    /** Human-readable status message. */
    val currentStatus: String,
) {
    /**
     * Returns true if an acquisition is currently in progress.
     */
    fun isRunning(): Boolean =
        currentPhase != AcquisitionPhase.IDLE &&
            currentPhase != AcquisitionPhase.COMPLETED &&
            currentPhase != AcquisitionPhase.FAILED

    /**
     * Returns the elapsed duration of the current or last acquisition.
     */
    fun elapsedDuration(): Duration? {
        val start = startedAt ?: return null
        val end = finishedAt ?: Instant.now()
        return Duration.between(start, end)
    }

    companion object {
        fun idle(): AcquisitionState = AcquisitionState(
            executionId = null,
            trigger = null,
            currentPhase = AcquisitionPhase.IDLE,
            completedPhases = emptyList(),
            startedAt = null,
            finishedAt = null,
            lastError = null,
            currentStatus = "Aguardando aquisição.",
        )
    }
}

