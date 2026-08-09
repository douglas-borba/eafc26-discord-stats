package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PublicationLockRegistryTest {

    @Test
    fun `same match id is serialized per club without blocking another club`() {
        val registry = PublicationLockRegistry()
        val clubA = ClubId("1104972")
        val clubB = ClubId("2200000")
        val executor = Executors.newFixedThreadPool(2)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val sameKeyEntered = AtomicBoolean(false)

        executor.submit {
            registry.withLock(clubA, "same-match") {
                firstStarted.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            }
        }
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue()

        val sameKey = executor.submit {
            registry.withLock(clubA, "same-match") { sameKeyEntered.set(true) }
        }
        Thread.sleep(50)
        assertThat(sameKeyEntered).isFalse()

        val otherClubResult = registry.withLock(clubB, "same-match") { "not blocked" }
        assertThat(otherClubResult).isEqualTo("not blocked")

        releaseFirst.countDown()
        sameKey.get(2, TimeUnit.SECONDS)
        assertThat(sameKeyEntered).isTrue()
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }
}
