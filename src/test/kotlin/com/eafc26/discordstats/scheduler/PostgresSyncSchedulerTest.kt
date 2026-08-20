package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.support.defaultClubProvider
import com.eafc26.discordstats.service.PostgresSyncResult
import com.eafc26.discordstats.service.PostgresSyncService
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class PostgresSyncSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scheduler does not overlap executions`() {
        val maxConcurrent = AtomicInteger(0)
        val concurrentCalls = AtomicInteger(0)
        val latch = CountDownLatch(1)

        val emptyRepo = object : CanonicalMatchRepository {
            override fun save(match: CanonicalMatch) {}
            override fun findById(clubId: ClubId, matchId: MatchId) = null
            override fun findMatchIds(clubId: ClubId) = emptySet<MatchId>()
            override fun findLatestMatchId(clubId: ClubId) = null
            override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>) = emptySet<MatchId>()
            override fun findRecentMatchIds(clubId: ClubId, limit: Int) = emptyList<MatchId>()
            override fun findAll(clubId: ClubId) = emptyList<CanonicalMatch>()
            override fun metadata(clubId: ClubId) = CanonicalRepositoryMetadata(0, null, null, null, emptySet(), emptySet())
        }

        val slowSync = object : PostgresSyncService(
            JsonCanonicalMatchRepository(jacksonObjectMapper().findAndRegisterModules(), tempDir, CLUB_ID),
            emptyRepo,
            defaultClubProvider(CLUB_ID),
        ) {
            override fun sync(): PostgresSyncResult {
                val current = concurrentCalls.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                try { latch.await() } catch (_: InterruptedException) {}
                concurrentCalls.decrementAndGet()
                return PostgresSyncResult(0, 0, 0, emptyList(), 0, 0, 0)
            }
        }

        val scheduler = PostgresSyncScheduler(slowSync)

        val t1 = Thread { scheduler.sync() }
        t1.start()
        Thread.sleep(50)

        scheduler.sync()

        latch.countDown()
        t1.join(5000)

        assertThat(maxConcurrent.get()).isEqualTo(1)
    }

    private companion object {
        val CLUB_ID = ClubId("club")
    }
}
