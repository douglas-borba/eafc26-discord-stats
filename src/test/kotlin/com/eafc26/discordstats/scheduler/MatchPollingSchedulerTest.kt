package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MatchPollingSchedulerTest {

    private val acquisitionService: MatchAcquisitionService = mock()
    private val statusHolder: PollingStatusHolder = PollingStatusHolder()

    @BeforeEach
    fun setUp() {
        // Default: successful acquisition with no new matches
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = emptyList(),
            )
        )
    }

    // -- Interval Configuration --

    @Test
    fun `INTERVAL_MS is 60 seconds`() {
        assertThat(MatchPollingScheduler.INTERVAL_MS).isEqualTo(60_000L)
    }

    @Test
    fun `statusHolder intervalSeconds is set to 60 on initialization`() {
        MatchPollingScheduler(acquisitionService, statusHolder)
        assertThat(statusHolder.intervalSeconds).isEqualTo(60)
    }

    @Test
    fun `poll method uses fixedRate annotation with 60000ms and initialDelay 0`() {
        val pollMethod = MatchPollingScheduler::class.java.getMethod("poll")
        val scheduled = pollMethod.getAnnotation(Scheduled::class.java)

        assertThat(scheduled).isNotNull
        assertThat(scheduled.fixedRate).isEqualTo(60_000L)
        assertThat(scheduled.initialDelay).isEqualTo(0L)
        // Verify it's NOT using fixedDelay
        assertThat(scheduled.fixedDelay).isEqualTo(-1L)
        assertThat(scheduled.fixedDelayString).isEmpty()
    }

    // -- Immediate First Execution --

    @Test
    fun `poll invokes acquisition service with SCHEDULER trigger`() {
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        scheduler.poll()
        verify(acquisitionService).acquire(AcquisitionTrigger.SCHEDULER)
    }

    @Test
    fun `initialDelay is 0 so first execution runs immediately`() {
        val pollMethod = MatchPollingScheduler::class.java.getMethod("poll")
        val scheduled = pollMethod.getAnnotation(Scheduled::class.java)
        assertThat(scheduled.initialDelay).isEqualTo(0L)
    }

    // -- Fresh HTTP Request Every Cycle --

    @Test
    fun `each poll cycle invokes acquisition service for fresh HTTP request`() {
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)

        scheduler.poll()
        scheduler.poll()
        scheduler.poll()

        verify(acquisitionService, times(3)).acquire(AcquisitionTrigger.SCHEDULER)
    }

    // -- Status Updates --

    @Test
    fun `poll updates lastCheck and nextCheck`() {
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        val beforePoll = java.time.Instant.now()
        scheduler.poll()
        val afterPoll = java.time.Instant.now()

        assertThat(statusHolder.lastCheck).isNotNull()
        assertThat(statusHolder.lastCheck).isBetween(beforePoll, afterPoll)
        assertThat(statusHolder.nextCheck).isNotNull()
        assertThat(statusHolder.nextCheck).isEqualTo(
            statusHolder.lastCheck!!.plusMillis(MatchPollingScheduler.INTERVAL_MS)
        )
    }

    @Test
    fun `poll sets lastResult for successful publish`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.Processed(
                published = listOf(AcquisitionResult.MatchSummary("m1", "Team 2 × 1 Opp")),
                alreadyPublished = emptyList(),
                failed = emptyList(),
            )
        )
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("sucesso")
    }

    @Test
    fun `poll sets lastResult for no new matches`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = listOf(AcquisitionResult.MatchSummary("m1", "Team 2 × 1 Opp")),
                failed = emptyList(),
            )
        )
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("Nenhuma partida nova")
    }

    @Test
    fun `poll sets lastResult for EA unavailable`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.EaUnavailable(503, "Service unavailable")
        )
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("EA indisponível")
    }

    @Test
    fun `poll sets lastResult for busy`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(AcquisitionResult.Busy)
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("andamento")
    }

    @Test
    fun `poll sets lastResult for baseline established`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = emptyList(),
                baselineEstablished = true,
            )
        )
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("Histórico inicial")
    }

    @Test
    fun `poll sets lastResult for webhook not configured`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenReturn(
            AcquisitionResult.WebhookNotConfigured
        )
        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        
        scheduler.poll()

        assertThat(statusHolder.lastResult).contains("Webhook")
    }

    // -- Concurrency handled by MatchAcquisitionService --

    @Test
    fun `busy result indicates concurrent execution was rejected`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        var callCount = 0
        whenever(acquisitionService.acquire(AcquisitionTrigger.SCHEDULER)).thenAnswer {
            callCount++
            if (callCount == 1) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                AcquisitionResult.Processed(emptyList(), emptyList(), emptyList())
            } else {
                AcquisitionResult.Busy
            }
        }

        val scheduler = MatchPollingScheduler(acquisitionService, statusHolder)
        val executor = Executors.newFixedThreadPool(2)

        try {
            // First poll
            executor.submit { scheduler.poll() }
            started.await(5, TimeUnit.SECONDS)

            // Second poll while first is running
            executor.submit { scheduler.poll() }.get(5, TimeUnit.SECONDS)

            release.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // Both calls should have been made; second should have received Busy
        verify(acquisitionService, times(2)).acquire(AcquisitionTrigger.SCHEDULER)
    }

    // -- Fixed Rate Verification --

    @Test
    fun `fixed rate ensures schedule is not shifted by request duration`() {
        val pollMethod = MatchPollingScheduler::class.java.getMethod("poll")
        val scheduled = pollMethod.getAnnotation(Scheduled::class.java)

        // fixedRate means: schedule at fixed intervals from start, not after completion
        assertThat(scheduled.fixedRate).isEqualTo(60_000L)
        // fixedDelay would mean: wait N ms after completion before next start
        assertThat(scheduled.fixedDelay).isEqualTo(-1L) // -1 means not set
    }

    // -- Initialization --

    @Test
    fun `scheduler confirms enabled is true on initialization`() {
        val holder = PollingStatusHolder()
        assertThat(holder.enabled).isTrue() // default is true since scheduler is enabled by default

        MatchPollingScheduler(acquisitionService, holder)

        assertThat(holder.enabled).isTrue() // still true after scheduler init
    }
}
