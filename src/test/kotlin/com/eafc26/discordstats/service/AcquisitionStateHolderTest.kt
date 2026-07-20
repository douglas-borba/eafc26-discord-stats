package com.eafc26.discordstats.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AcquisitionStateHolderTest {

    private lateinit var holder: AcquisitionStateHolder

    @BeforeEach
    fun setUp() {
        holder = AcquisitionStateHolder()
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state is idle`() {
        val state = holder.current()

        assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.IDLE)
        assertThat(state.executionId).isNull()
        assertThat(state.trigger).isNull()
        assertThat(state.completedPhases).isEmpty()
        assertThat(state.startedAt).isNull()
        assertThat(state.finishedAt).isNull()
        assertThat(state.lastError).isNull()
        assertThat(state.isRunning()).isFalse()
    }

    // -------------------------------------------------------------------------
    // State Transitions
    // -------------------------------------------------------------------------

    @Nested
    inner class StateTransitions {

        @Test
        fun `start transitions from IDLE to FETCHING`() {
            val executionId = holder.start(AcquisitionTrigger.SCHEDULER)

            val state = holder.current()
            assertThat(state.executionId).isEqualTo(executionId)
            assertThat(state.trigger).isEqualTo(AcquisitionTrigger.SCHEDULER)
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FETCHING)
            assertThat(state.startedAt).isNotNull()
            assertThat(state.isRunning()).isTrue()
        }

        @Test
        fun `enterPhase transitions to new phase and tracks completed phases`() {
            holder.start(AcquisitionTrigger.MANUAL)
            
            holder.enterPhase(AcquisitionPhase.PROCESSING, "Processing...")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.PROCESSING)
            assertThat(state.completedPhases).containsExactly(AcquisitionPhase.FETCHING)
            assertThat(state.currentStatus).isEqualTo("Processing...")
        }

        @Test
        fun `multiple enterPhase calls accumulate completed phases`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            
            holder.enterPhase(AcquisitionPhase.PROCESSING, "Processing...")
            holder.enterPhase(AcquisitionPhase.DELIVERING, "Delivering...")
            holder.enterPhase(AcquisitionPhase.PERSISTING, "Persisting...")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.PERSISTING)
            assertThat(state.completedPhases).containsExactly(
                AcquisitionPhase.FETCHING,
                AcquisitionPhase.PROCESSING,
                AcquisitionPhase.DELIVERING
            )
        }

        @Test
        fun `complete transitions to COMPLETED and sets finishedAt`() {
            holder.start(AcquisitionTrigger.CLI)
            holder.enterPhase(AcquisitionPhase.PROCESSING, "Processing...")
            
            holder.complete("Done!")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.COMPLETED)
            assertThat(state.finishedAt).isNotNull()
            assertThat(state.currentStatus).isEqualTo("Done!")
            assertThat(state.isRunning()).isFalse()
        }

        @Test
        fun `fail transitions to FAILED and records error`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            holder.enterPhase(AcquisitionPhase.FETCHING, "Fetching...")
            
            holder.fail("Network error", "EA unavailable")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FAILED)
            assertThat(state.finishedAt).isNotNull()
            assertThat(state.lastError).isEqualTo("Network error")
            assertThat(state.currentStatus).isEqualTo("EA unavailable")
            assertThat(state.isRunning()).isFalse()
        }
    }

    // -------------------------------------------------------------------------
    // Success Scenarios
    // -------------------------------------------------------------------------

    @Nested
    inner class SuccessScenarios {

        @Test
        fun `successful acquisition completes all phases`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            holder.enterPhase(AcquisitionPhase.PROCESSING, "Processing...")
            holder.enterPhase(AcquisitionPhase.DELIVERING, "Delivering...")
            holder.enterPhase(AcquisitionPhase.PERSISTING, "Persisting...")
            holder.complete("Match published!")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.COMPLETED)
            assertThat(state.completedPhases).containsExactly(
                AcquisitionPhase.FETCHING,
                AcquisitionPhase.PROCESSING,
                AcquisitionPhase.DELIVERING,
                AcquisitionPhase.PERSISTING
            )
            assertThat(state.lastError).isNull()
        }

        @Test
        fun `elapsed duration is calculated correctly`() {
            holder.start(AcquisitionTrigger.MANUAL)
            
            Thread.sleep(50)
            
            val state = holder.current()
            assertThat(state.elapsedDuration()?.toMillis()).isGreaterThanOrEqualTo(50)
        }
    }

    // -------------------------------------------------------------------------
    // Failure Scenarios
    // -------------------------------------------------------------------------

    @Nested
    inner class FailureScenarios {

        @Test
        fun `failure during FETCHING phase`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            
            holder.fail("HTTP 503", "EA indisponível")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FAILED)
            assertThat(state.completedPhases).isEmpty()
            assertThat(state.lastError).isEqualTo("HTTP 503")
        }

        @Test
        fun `failure during DELIVERING phase preserves completed phases`() {
            holder.start(AcquisitionTrigger.MANUAL)
            holder.enterPhase(AcquisitionPhase.PROCESSING, "Processing...")
            holder.enterPhase(AcquisitionPhase.DELIVERING, "Delivering...")
            
            holder.fail("Discord error", "Discord rejected the request")
            
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FAILED)
            assertThat(state.completedPhases).containsExactly(
                AcquisitionPhase.FETCHING,
                AcquisitionPhase.PROCESSING
            )
        }
    }

    // -------------------------------------------------------------------------
    // Busy Execution
    // -------------------------------------------------------------------------

    @Nested
    inner class BusyExecution {

        @Test
        fun `recordBusy updates status when acquisition is running`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            
            holder.recordBusy(AcquisitionTrigger.MANUAL)
            
            val state = holder.current()
            assertThat(state.currentStatus).contains("MANUAL")
            assertThat(state.currentStatus).contains("rejeitada")
        }

        @Test
        fun `recordBusy does not update status when not running`() {
            val initialStatus = holder.current().currentStatus
            
            holder.recordBusy(AcquisitionTrigger.MANUAL)
            
            assertThat(holder.current().currentStatus).isEqualTo(initialStatus)
        }
    }

    // -------------------------------------------------------------------------
    // Concurrent Execution
    // -------------------------------------------------------------------------

    @Nested
    inner class ConcurrentExecution {

        @Test
        fun `state updates are thread-safe`() {
            val executor = Executors.newFixedThreadPool(10)
            val startLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(10)
            
            // Start an acquisition
            holder.start(AcquisitionTrigger.SCHEDULER)
            
            // Multiple threads updating state concurrently
            repeat(10) { i ->
                executor.submit {
                    startLatch.await()
                    holder.enterPhase(AcquisitionPhase.PROCESSING, "Thread $i")
                    finishLatch.countDown()
                }
            }
            
            startLatch.countDown()
            finishLatch.await(2, TimeUnit.SECONDS)
            
            // State should be consistent (no corruption)
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.PROCESSING)
            assertThat(state.executionId).isNotNull()
            
            executor.shutdown()
        }

        @Test
        fun `concurrent start calls produce unique execution IDs`() {
            val executor = Executors.newFixedThreadPool(5)
            val executionIds = mutableSetOf<String>()
            val lock = Object()
            val finishLatch = CountDownLatch(5)
            
            repeat(5) {
                executor.submit {
                    val id = holder.start(AcquisitionTrigger.SCHEDULER)
                    synchronized(lock) {
                        executionIds.add(id)
                    }
                    finishLatch.countDown()
                }
            }
            
            finishLatch.await(2, TimeUnit.SECONDS)
            
            // All execution IDs should be unique
            assertThat(executionIds).hasSize(5)
            
            executor.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // State Persistence (reset was removed intentionally)
    // -------------------------------------------------------------------------

    @Nested
    inner class StatePersistence {

        @Test
        fun `completed state remains visible until next acquisition starts`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            holder.complete("Done!")
            
            // State should remain COMPLETED
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.COMPLETED)
            assertThat(state.currentStatus).isEqualTo("Done!")
            
            // Starting new acquisition clears previous state
            holder.start(AcquisitionTrigger.MANUAL)
            assertThat(holder.current().currentPhase).isEqualTo(AcquisitionPhase.FETCHING)
        }

        @Test
        fun `failed state remains visible until next acquisition starts`() {
            holder.start(AcquisitionTrigger.MANUAL)
            holder.fail("Network error", "Connection failed")
            
            // State should remain FAILED
            val state = holder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FAILED)
            assertThat(state.lastError).isEqualTo("Network error")
            
            // Starting new acquisition clears previous state
            holder.start(AcquisitionTrigger.CLI)
            assertThat(holder.current().currentPhase).isEqualTo(AcquisitionPhase.FETCHING)
            assertThat(holder.current().lastError).isNull()
        }
    }

    // -------------------------------------------------------------------------
    // Trigger Types
    // -------------------------------------------------------------------------

    @Nested
    inner class TriggerTypes {

        @Test
        fun `supports SCHEDULER trigger`() {
            holder.start(AcquisitionTrigger.SCHEDULER)
            assertThat(holder.current().trigger).isEqualTo(AcquisitionTrigger.SCHEDULER)
        }

        @Test
        fun `supports MANUAL trigger`() {
            holder.start(AcquisitionTrigger.MANUAL)
            assertThat(holder.current().trigger).isEqualTo(AcquisitionTrigger.MANUAL)
        }

        @Test
        fun `supports CLI trigger`() {
            holder.start(AcquisitionTrigger.CLI)
            assertThat(holder.current().trigger).isEqualTo(AcquisitionTrigger.CLI)
        }

        @Test
        fun `supports DEV_SIMULATOR trigger`() {
            holder.start(AcquisitionTrigger.DEV_SIMULATOR)
            assertThat(holder.current().trigger).isEqualTo(AcquisitionTrigger.DEV_SIMULATOR)
        }

        @Test
        fun `supports FORCE_RESEND trigger`() {
            holder.start(AcquisitionTrigger.FORCE_RESEND)
            assertThat(holder.current().trigger).isEqualTo(AcquisitionTrigger.FORCE_RESEND)
        }
    }
}

