package com.eafc26.discordstats.diagnostics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class CanonicalReadDiagnosticsTest {
    @Test
    fun `aggregates each canonical operation rows bytes and origin`() {
        val diagnostics = CanonicalReadDiagnostics()

        diagnostics.record(CanonicalReadOperation.FIND_ALL, CanonicalReadOrigin.HISTORY_LIST, rows = 3, estimatedReturnedBytes = 300)
        diagnostics.record(CanonicalReadOperation.FIND_RECENT, CanonicalReadOrigin.DASHBOARD_OVERVIEW, rows = 2, estimatedReturnedBytes = 120)
        diagnostics.record(CanonicalReadOperation.FIND_BY_ID, CanonicalReadOrigin.COMPARISON, rows = 1, estimatedReturnedBytes = 80)
        diagnostics.record(CanonicalReadOperation.FIND_MATCH_IDS, CanonicalReadOrigin.POLLING_CHECKPOINT, rows = 4, estimatedReturnedBytes = 40)
        diagnostics.record(CanonicalReadOperation.FIND_LATEST_MATCH_ID, CanonicalReadOrigin.POLLING_CHECKPOINT, rows = 1, estimatedReturnedBytes = 10)
        diagnostics.record(CanonicalReadOperation.FIND_EXISTING_MATCH_IDS, CanonicalReadOrigin.POLLING_CHECKPOINT, rows = 1, estimatedReturnedBytes = 10)
        diagnostics.record(CanonicalReadOperation.FIND_RECENT_MATCH_IDS, CanonicalReadOrigin.LLM_PANORAMA, rows = 2, estimatedReturnedBytes = 20)
        diagnostics.record(CanonicalReadOperation.FIND_RECENT_OVERVIEW, CanonicalReadOrigin.DASHBOARD_OVERVIEW, rows = 2, estimatedReturnedBytes = 60)

        val snapshot = diagnostics.snapshot()

        assertThat(snapshot.total.calls).isEqualTo(8)
        assertThat(snapshot.total.rows).isEqualTo(16)
        assertThat(snapshot.total.estimatedReturnedBytes).isEqualTo(640)
        val findAll = snapshot.operations.getValue("findAll")
        assertThat(findAll.calls).isEqualTo(1)
        assertThat(findAll.rows).isEqualTo(3)
        assertThat(findAll.estimatedReturnedBytes).isEqualTo(300)
        assertThat(snapshot.operations.getValue("findRecent").firstObservedAt).isNotNull
        assertThat(snapshot.operations.getValue("findById").lastObservedAt).isNotNull
        assertThat(snapshot.operations.getValue("findLatestMatchId").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findExistingMatchIds").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findRecentMatchIds").rows).isEqualTo(2)
        assertThat(snapshot.operations.getValue("findRecentOverview").rows).isEqualTo(2)
        assertThat(snapshot.origins.getValue("polling.checkpoint").rows).isEqualTo(6)
        assertThat(snapshot.origins.getValue("dashboard.overview").estimatedReturnedBytes).isEqualTo(180)
    }

    @Test
    fun `accumulates concurrent observations safely`() {
        val diagnostics = CanonicalReadDiagnostics()
        Executors.newFixedThreadPool(4).use { executor ->
            executor.invokeAll((1..100).map {
                Callable { diagnostics.record(CanonicalReadOperation.FIND_RECENT, CanonicalReadOrigin.DASHBOARD_OVERVIEW, 2, 64) }
            })
        }

        val snapshot = diagnostics.snapshot()
        assertThat(snapshot.total.calls).isEqualTo(100)
        assertThat(snapshot.total.rows).isEqualTo(200)
        assertThat(snapshot.total.estimatedReturnedBytes).isEqualTo(6_400)
    }

    @Test
    fun `reset replaces only process local counters`() {
        val diagnostics = CanonicalReadDiagnostics()
        diagnostics.record(CanonicalReadOperation.FIND_ALL, CanonicalReadOrigin.UNKNOWN, 1, 10)

        val reset = diagnostics.reset()

        assertThat(reset.total.calls).isZero()
        assertThat(reset.total.rows).isZero()
        assertThat(reset.total.estimatedReturnedBytes).isZero()
        assertThat(reset.lastUpdatedAt).isNull()
        assertThat(reset.instanceId).isEqualTo(diagnostics.instanceId)
    }

    @Test
    fun `origin context restores the prior origin after nested work`() {
        val context = CanonicalReadOriginContext()
        assertThat(context.current()).isEqualTo(CanonicalReadOrigin.UNKNOWN)

        context.withOrigin(CanonicalReadOrigin.PLAYERS) {
            assertThat(context.current()).isEqualTo(CanonicalReadOrigin.PLAYERS)
            context.withOrigin(CanonicalReadOrigin.COMPARISON) {
                assertThat(context.current()).isEqualTo(CanonicalReadOrigin.COMPARISON)
            }
            assertThat(context.current()).isEqualTo(CanonicalReadOrigin.PLAYERS)
        }

        assertThat(context.current()).isEqualTo(CanonicalReadOrigin.UNKNOWN)
    }
}
