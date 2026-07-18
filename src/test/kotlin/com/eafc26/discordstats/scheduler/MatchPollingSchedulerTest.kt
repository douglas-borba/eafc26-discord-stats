package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import com.eafc26.discordstats.service.NotifyLatestService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MatchPollingSchedulerTest {

    private val service: MatchNotifierService = mock()
    private val notifyLatestService: NotifyLatestService = mock()
    private val statusHolder: PollingStatusHolder = PollingStatusHolder()

    @BeforeEach
    fun setUp() {
        // Default: runIfIdle always runs the action
        whenever(notifyLatestService.runIfIdle(any())).doAnswer { invocation ->
            val action = invocation.getArgument<() -> Unit>(0)
            action()
            true
        }
    }

    // -- Interval Configuration --

    @Test
    fun `INTERVAL_MS is 60 seconds`() {
        assertThat(MatchPollingScheduler.INTERVAL_MS).isEqualTo(60_000L)
    }

    @Test
    fun `statusHolder intervalSeconds is set to 60 on initialization`() {
        MatchPollingScheduler(service, notifyLatestService, statusHolder)
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
    fun `poll invokes service process immediately`() {
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll()
        verify(service).process(any())
    }

    @Test
    fun `initialDelay is 0 so first execution runs immediately`() {
        val pollMethod = MatchPollingScheduler::class.java.getMethod("poll")
        val scheduled = pollMethod.getAnnotation(Scheduled::class.java)
        assertThat(scheduled.initialDelay).isEqualTo(0L)
    }

    // -- Fresh HTTP Request Every Cycle --

    @Test
    fun `each poll cycle invokes service process for fresh HTTP request`() {
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)

        scheduler.poll()
        scheduler.poll()
        scheduler.poll()

        verify(service, times(3)).process(any())
    }

    // -- Exception Handling --

    @Test
    fun `exception from service does not propagate out of poll`() {
        whenever(service.process(any())).thenThrow(RuntimeException("boom"))
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll() // must not throw
    }

    @Test
    fun `exception sets lastResult to error message`() {
        whenever(service.process(any())).thenThrow(RuntimeException("boom"))
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll()
        assertThat(statusHolder.lastResult).isEqualTo("EA indisponível. Nova tentativa em 1 minuto.")
    }

    // -- Concurrency / Overlapping Executions --

    @Test
    fun `overlapping execution is skipped - service called only once`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callCount = AtomicInteger(0)

        whenever(notifyLatestService.runIfIdle(any())).doAnswer { invocation ->
            val attempt = callCount.incrementAndGet()
            if (attempt == 1) {
                // First call: signal start, wait for release, then run action
                val action = invocation.getArgument<() -> Unit>(0)
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                action()
                true
            } else {
                // Subsequent calls while first is running — return false (busy)
                false
            }
        }

        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        val executor = Executors.newFixedThreadPool(2)

        try {
            executor.submit { scheduler.poll() }
            started.await(5, TimeUnit.SECONDS)
            // Second firing while first is still running — should be skipped
            executor.submit { scheduler.poll() }.get(5, TimeUnit.SECONDS)
            release.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        verify(service, times(1)).process(any())
    }

    @Test
    fun `skipped overlapping cycle does not stop later cycles`() {
        val callCount = AtomicInteger(0)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        whenever(notifyLatestService.runIfIdle(any())).doAnswer { invocation ->
            val attempt = callCount.incrementAndGet()
            when (attempt) {
                1 -> {
                    // First call: run slowly
                    val action = invocation.getArgument<() -> Unit>(0)
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    action()
                    true
                }
                2 -> {
                    // Second call: busy (overlapping)
                    false
                }
                else -> {
                    // Third+ calls: run normally
                    val action = invocation.getArgument<() -> Unit>(0)
                    action()
                    true
                }
            }
        }

        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        val executor = Executors.newFixedThreadPool(3)

        try {
            // First poll (slow)
            executor.submit { scheduler.poll() }
            started.await(5, TimeUnit.SECONDS)

            // Second poll (should be skipped - busy)
            executor.submit { scheduler.poll() }.get(5, TimeUnit.SECONDS)

            // Release first poll
            release.countDown()
            Thread.sleep(100) // Let first poll complete

            // Third poll (should run normally)
            executor.submit { scheduler.poll() }.get(5, TimeUnit.SECONDS)

            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // First and third polls should have called service
        verify(service, times(2)).process(any())
    }

    @Test
    fun `when busy lastResult shows verification in progress`() {
        whenever(notifyLatestService.runIfIdle(any())).thenReturn(false)

        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll()

        assertThat(statusHolder.lastResult).isEqualTo("Uma verificação já está em andamento.")
        verify(service, never()).process(any())
    }

    // -- Status Updates --

    @Test
    fun `poll updates lastCheck timestamp`() {
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        val before = java.time.Instant.now()
        scheduler.poll()
        val after = java.time.Instant.now()

        assertThat(statusHolder.lastCheck).isNotNull
        assertThat(statusHolder.lastCheck).isBetween(before, after)
    }

    @Test
    fun `poll updates nextCheck to 60 seconds from lastCheck`() {
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll()

        assertThat(statusHolder.nextCheck).isNotNull
        assertThat(statusHolder.lastCheck).isNotNull
        val diff = java.time.Duration.between(statusHolder.lastCheck, statusHolder.nextCheck)
        assertThat(diff.toMillis()).isEqualTo(60_000L)
    }

    @Test
    fun `running is true during execution and false after`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var runningDuringExecution = false

        whenever(notifyLatestService.runIfIdle(any())).doAnswer { invocation ->
            val action = invocation.getArgument<() -> Unit>(0)
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            action()
            true
        }

        whenever(service.process(any())).doAnswer {
            runningDuringExecution = statusHolder.running
        }

        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit { scheduler.poll() }
            started.await(5, TimeUnit.SECONDS)
            release.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertThat(runningDuringExecution).isTrue()
        assertThat(statusHolder.running).isFalse()
    }

    // -- Fixed Rate Verification --

    @Test
    fun `fixed rate ensures schedule is not shifted by request duration`() {
        // This test verifies the annotation configuration
        // The actual fixed-rate behavior is handled by Spring's TaskScheduler
        val pollMethod = MatchPollingScheduler::class.java.getMethod("poll")
        val scheduled = pollMethod.getAnnotation(Scheduled::class.java)

        // fixedRate means: schedule at fixed intervals from start, not after completion
        assertThat(scheduled.fixedRate).isEqualTo(60_000L)
        // fixedDelay would mean: wait N ms after completion before next start
        assertThat(scheduled.fixedDelay).isEqualTo(-1L) // -1 means not set
    }

    // -- Initialization --

    @Test
    fun `scheduler sets enabled to true on initialization`() {
        val holder = PollingStatusHolder()
        assertThat(holder.enabled).isFalse() // default

        MatchPollingScheduler(service, notifyLatestService, holder)

        assertThat(holder.enabled).isTrue()
    }
}
