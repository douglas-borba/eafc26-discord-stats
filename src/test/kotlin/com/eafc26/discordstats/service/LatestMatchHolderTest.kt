package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class LatestMatchHolderTest {

    private lateinit var holder: LatestMatchHolder

    @BeforeEach
    fun setUp() {
        holder = LatestMatchHolder()
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Nested
    inner class InitialState {

        @Test
        fun `initial presentation is null`() {
            assertThat(holder.presentation()).isNull()
        }

        @Test
        fun `initial version is 0`() {
            assertThat(holder.version()).isEqualTo(0)
        }

        @Test
        fun `hasPresentation returns false initially`() {
            assertThat(holder.hasPresentation()).isFalse()
        }

        @Test
        fun `snapshot returns null presentation and version 0`() {
            val snapshot = holder.snapshot()
            assertThat(snapshot.presentation).isNull()
            assertThat(snapshot.version).isEqualTo(0)
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Nested
    inner class Update {

        @Test
        fun `update stores the presentation`() {
            val presentation = createPresentation("m1")

            holder.update(presentation)

            assertThat(holder.presentation()).isEqualTo(presentation)
        }

        @Test
        fun `update increments version`() {
            val presentation = createPresentation("m1")

            val version = holder.update(presentation)

            assertThat(version).isEqualTo(1)
            assertThat(holder.version()).isEqualTo(1)
        }

        @Test
        fun `multiple updates increment version each time`() {
            holder.update(createPresentation("m1"))
            holder.update(createPresentation("m2"))
            holder.update(createPresentation("m3"))

            assertThat(holder.version()).isEqualTo(3)
        }

        @Test
        fun `update replaces previous presentation`() {
            holder.update(createPresentation("m1"))
            holder.update(createPresentation("m2"))

            assertThat(holder.presentation()?.matchId).isEqualTo("m2")
        }

        @Test
        fun `hasPresentation returns true after update`() {
            holder.update(createPresentation("m1"))

            assertThat(holder.hasPresentation()).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    @Nested
    inner class Snapshot {

        @Test
        fun `snapshot contains current presentation and version`() {
            val presentation = createPresentation("m1")
            holder.update(presentation)

            val snapshot = holder.snapshot()

            assertThat(snapshot.presentation).isEqualTo(presentation)
            assertThat(snapshot.version).isEqualTo(1)
        }

        @Test
        fun `snapshot is immutable - changes to holder do not affect snapshot`() {
            holder.update(createPresentation("m1"))
            val snapshot = holder.snapshot()

            holder.update(createPresentation("m2"))

            assertThat(snapshot.presentation?.matchId).isEqualTo("m1")
            assertThat(snapshot.version).isEqualTo(1)
            assertThat(holder.presentation()?.matchId).isEqualTo("m2")
            assertThat(holder.version()).isEqualTo(2)
        }
    }

    // -------------------------------------------------------------------------
    // Thread Safety
    // -------------------------------------------------------------------------

    @Nested
    inner class ThreadSafety {

        @Test
        fun `concurrent updates increment version correctly`() {
            val executor = Executors.newFixedThreadPool(10)
            val startLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(100)

            repeat(100) { i ->
                executor.submit {
                    startLatch.await()
                    holder.update(createPresentation("m$i"))
                    finishLatch.countDown()
                }
            }

            startLatch.countDown()
            finishLatch.await(5, TimeUnit.SECONDS)

            assertThat(holder.version()).isEqualTo(100)

            executor.shutdown()
        }

        @Test
        fun `concurrent reads and writes do not corrupt state`() {
            val executor = Executors.newFixedThreadPool(20)
            val startLatch = CountDownLatch(1)
            val finishLatch = CountDownLatch(200)
            val errors = mutableListOf<Throwable>()
            val lock = Object()

            // 100 writers
            repeat(100) { i ->
                executor.submit {
                    try {
                        startLatch.await()
                        holder.update(createPresentation("m$i"))
                    } catch (e: Throwable) {
                        synchronized(lock) { errors.add(e) }
                    } finally {
                        finishLatch.countDown()
                    }
                }
            }

            // 100 readers
            repeat(100) {
                executor.submit {
                    try {
                        startLatch.await()
                        val snapshot = holder.snapshot()
                        // Verify consistency: if presentation exists, matchId should not be null
                        val pres = snapshot.presentation
                        if (pres != null) {
                            assertThat(pres.matchId).isNotNull()
                        }
                    } catch (e: Throwable) {
                        synchronized(lock) { errors.add(e) }
                    } finally {
                        finishLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            finishLatch.await(5, TimeUnit.SECONDS)

            assertThat(errors).isEmpty()
            assertThat(holder.version()).isEqualTo(100)

            executor.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Version Monotonicity
    // -------------------------------------------------------------------------

    @Nested
    inner class VersionMonotonicity {

        @Test
        fun `version only increases`() {
            val versions = mutableListOf<Long>()

            repeat(10) {
                versions.add(holder.update(createPresentation("m$it")))
            }

            assertThat(versions).isSorted()
            assertThat(versions).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
        }

        @Test
        fun `returned version matches current version`() {
            repeat(5) {
                val returnedVersion = holder.update(createPresentation("m$it"))
                assertThat(returnedVersion).isEqualTo(holder.version())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun createPresentation(matchId: String): MatchSummaryPresentation =
        MatchSummaryPresentation(
            ourName = "Test FC",
            oppName = "Opponent FC",
            ourScore = 2,
            oppScore = 1,
            outcome = MatchOutcome(
                emoji = "🏆",
                label = "Vitória",
                color = 0x00FF00,
                type = OutcomeType.WIN,
            ),
            date = "19 Jul 2026 • 10:00",
            timestamp = "2026-07-19T10:00:00Z",
            matchId = matchId,
            goals = null,
            assists = null,
            highlights = null,
            craque = null,
            offensiveNarratives = emptyList(),
            bagre = null,
            redCard = null,
            xerife = null,
            passePrecisao = null,
            correioExtraviado = null,
            muralha = null,
        )
}

