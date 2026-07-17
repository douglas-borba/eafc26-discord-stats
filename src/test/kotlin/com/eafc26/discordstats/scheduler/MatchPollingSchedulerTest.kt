package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import com.eafc26.discordstats.service.NotifyLatestService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test
    fun `poll invokes service process`() {
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll()
        verify(service).process(any())
    }

    @Test
    fun `exception from service does not propagate out of poll`() {
        whenever(service.process(any())).thenThrow(RuntimeException("boom"))
        val scheduler = MatchPollingScheduler(service, notifyLatestService, statusHolder)
        scheduler.poll() // must not throw
    }

    @Test
    fun `concurrent firing is skipped - service called only once`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        // The first call runs the action after signaling start
        var firstCall = true
        whenever(notifyLatestService.runIfIdle(any())).doAnswer { invocation ->
            if (firstCall) {
                firstCall = false
                val action = invocation.getArgument<() -> Unit>(0)
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                action()
                true
            } else {
                // Second call while first is running — return false (busy)
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
}
