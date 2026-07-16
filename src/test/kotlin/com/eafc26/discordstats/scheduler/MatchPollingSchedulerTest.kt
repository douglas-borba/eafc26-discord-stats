package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import org.junit.jupiter.api.Test
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

    @Test
    fun `poll invokes service process`() {
        val scheduler = MatchPollingScheduler(service)
        scheduler.poll()
        verify(service).process()
    }

    @Test
    fun `exception from service does not propagate out of poll`() {
        whenever(service.process()).thenThrow(RuntimeException("boom"))
        val scheduler = MatchPollingScheduler(service)
        scheduler.poll() // must not throw
    }

    @Test
    fun `concurrent firing is skipped - service called only once`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        doAnswer {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }.whenever(service).process()

        val scheduler = MatchPollingScheduler(service)
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

        verify(service, times(1)).process()
    }
}
