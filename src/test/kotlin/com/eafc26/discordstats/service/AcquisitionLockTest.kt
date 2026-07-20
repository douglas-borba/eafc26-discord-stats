package com.eafc26.discordstats.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AcquisitionLockTest {

    private lateinit var lock: AcquisitionLock

    @BeforeEach
    fun setUp() {
        lock = AcquisitionLock()
    }

    @Test
    fun `tryRun executes action and returns result when lock is free`() {
        val result = lock.tryRun { "success" }

        assertThat(result).isEqualTo("success")
    }

    @Test
    fun `tryRun returns null when lock is already held`() {
        val executor = Executors.newSingleThreadExecutor()
        val actionStarted = CountDownLatch(1)
        val actionCanFinish = CountDownLatch(1)

        // Start a long-running action in another thread
        executor.submit {
            lock.tryRun {
                actionStarted.countDown()
                actionCanFinish.await(5, TimeUnit.SECONDS)
                "first"
            }
        }

        // Wait for the first action to start
        actionStarted.await(1, TimeUnit.SECONDS)

        // Try to run a second action — should return null
        val result = lock.tryRun { "second" }

        assertThat(result).isNull()

        // Clean up
        actionCanFinish.countDown()
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    @Test
    fun `lock is released after action completes`() {
        lock.tryRun { "first" }

        val result = lock.tryRun { "second" }

        assertThat(result).isEqualTo("second")
    }

    @Test
    fun `lock is released even when action throws exception`() {
        runCatching {
            lock.tryRun<Unit> { throw RuntimeException("boom") }
        }

        val result = lock.tryRun { "after exception" }

        assertThat(result).isEqualTo("after exception")
    }

    @Test
    fun `isBusy returns false when lock is free`() {
        assertThat(lock.isBusy()).isFalse()
    }

    @Test
    fun `isBusy returns true when lock is held`() {
        val executor = Executors.newSingleThreadExecutor()
        val actionStarted = CountDownLatch(1)
        val actionCanFinish = CountDownLatch(1)

        executor.submit {
            lock.tryRun {
                actionStarted.countDown()
                actionCanFinish.await(5, TimeUnit.SECONDS)
            }
        }

        actionStarted.await(1, TimeUnit.SECONDS)

        assertThat(lock.isBusy()).isTrue()

        actionCanFinish.countDown()
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    @Test
    fun `isBusy returns false after action completes`() {
        lock.tryRun { "done" }

        assertThat(lock.isBusy()).isFalse()
    }

    @Test
    fun `concurrent tryRun calls result in only one execution`() {
        val executor = Executors.newFixedThreadPool(10)
        val executionCount = AtomicInteger(0)
        val allStarted = CountDownLatch(10)
        val canProceed = CountDownLatch(1)
        val allFinished = CountDownLatch(10)

        repeat(10) {
            executor.submit {
                allStarted.countDown()
                canProceed.await(5, TimeUnit.SECONDS)
                lock.tryRun {
                    executionCount.incrementAndGet()
                    Thread.sleep(50) // Simulate work
                }
                allFinished.countDown()
            }
        }

        // Wait for all threads to be ready
        allStarted.await(1, TimeUnit.SECONDS)
        // Release them all at once
        canProceed.countDown()
        // Wait for all to finish
        allFinished.await(5, TimeUnit.SECONDS)

        // Only one should have executed
        assertThat(executionCount.get()).isEqualTo(1)

        executor.shutdown()
    }

    @Test
    fun `sequential tryRun calls all execute`() {
        val results = mutableListOf<String?>()

        results += lock.tryRun { "first" }
        results += lock.tryRun { "second" }
        results += lock.tryRun { "third" }

        assertThat(results).containsExactly("first", "second", "third")
    }
}

