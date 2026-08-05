package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class PublicationReconciliationServiceTest {

    private lateinit var canonicalRepo: CanonicalMatchRepository
    private lateinit var store: PublishedMatchStore
    private lateinit var publicationService: DiscordMatchPublicationService
    private lateinit var reconciliationService: PublicationReconciliationService

    private val clubId = "42"

    @BeforeEach
    fun setup() {
        canonicalRepo = mock()
        store = mock()
        publicationService = mock()
        reconciliationService = PublicationReconciliationService(
            canonicalMatchRepository = canonicalRepo,
            store = store,
            publicationService = publicationService,
        )
    }

    private fun canonical(matchId: String, ourScore: String = "3", oppScore: String = "1"): CanonicalMatch {
        return CanonicalMatchFactory().create(
            source = MatchResponse(
                matchId = matchId,
                timestamp = System.currentTimeMillis() / 1000,
                clubs = mapOf(
                    clubId to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = ourScore, result = "1"),
                    "opp" to ClubMatchEntry(details = ClubDetails(name = "Rival FC"), score = oppScore, result = "0"),
                ),
                players = emptyMap(),
            ),
            perspectiveClubId = clubId,
        )
    }

    // =========================================================================
    // inspectLatestPublications
    // =========================================================================

    @Nested
    inner class InspectLatestPublications {

        @Test
        fun `inspects 5 matches by default`() {
            val matches = (1..10).map { canonical("m$it") }
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.inspections).hasSize(5)
            assertThat(report.summary.totalInspected).isEqualTo(5)
        }

        @Test
        fun `respects custom limit`() {
            val matches = (1..10).map { canonical("m$it") }
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())

            val report = reconciliationService.inspectLatestPublications(limit = 3)

            assertThat(report.inspections).hasSize(3)
            assertThat(report.summary.totalInspected).isEqualTo(3)
        }

        @Test
        fun `identifies never attempted matches`() {
            val matches = listOf(canonical("m1"), canonical("m2"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.neverAttempted).isEqualTo(2)
            assertThat(report.inspections.all { it.publicationState == null }).isTrue
            assertThat(report.inspections.all { it.safeToAutoPublish }).isTrue
        }

        @Test
        fun `identifies delivered matches`() {
            val matches = listOf(canonical("m1"), canonical("m2"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERED),
                "m2" to PublicationRecord("m2", PublicationState.DELIVERED),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.delivered).isEqualTo(2)
            assertThat(report.inspections.all { it.publicationState == PublicationState.DELIVERED }).isTrue
            assertThat(report.inspections.all { !it.safeToAutoPublish }).isTrue
        }

        @Test
        fun `identifies uncertain matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.uncertain).isEqualTo(1)
            assertThat(report.inspections[0].publicationState).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(report.inspections[0].safeToAutoPublish).isFalse
        }

        @Test
        fun `identifies failed permanent matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.FAILED_PERMANENT, lastHttpStatus = 404),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.failedPermanent).isEqualTo(1)
            assertThat(report.inspections[0].publicationState).isEqualTo(PublicationState.FAILED_PERMANENT)
            assertThat(report.inspections[0].safeToAutoPublish).isFalse // Requires correction + manual forcePublish
            assertThat(report.inspections[0].lastHttpStatus).isEqualTo(404)
        }

        @Test
        fun `identifies delivering matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERING),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.delivering).isEqualTo(1)
            assertThat(report.inspections[0].publicationState).isEqualTo(PublicationState.DELIVERING)
            assertThat(report.inspections[0].safeToAutoPublish).isFalse // Should be upgraded to uncertain
        }

        @Test
        fun `includes audit metadata in inspection`() {
            val matches = listOf(canonical("m1"))
            val now = Instant.now().epochSecond
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord(
                    matchId = "m1",
                    state = PublicationState.DELIVERY_UNCERTAIN,
                    attemptCount = 3,
                    lastAttemptAt = now,
                    lastError = "Connection timeout",
                    lastHttpStatus = null,
                ),
            ))

            val report = reconciliationService.inspectLatestPublications()

            val inspection = report.inspections[0]
            assertThat(inspection.attemptCount).isEqualTo(3)
            assertThat(inspection.lastAttemptAt).isEqualTo(Instant.ofEpochSecond(now))
            assertThat(inspection.lastError).isEqualTo("Connection timeout")
            assertThat(inspection.lastHttpStatus).isNull()
        }

        @Test
        fun `handles mixed states correctly`() {
            val matches = (1..5).map { canonical("m$it") }
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERED),
                "m2" to PublicationRecord("m2", PublicationState.DELIVERY_UNCERTAIN),
                "m3" to PublicationRecord("m3", PublicationState.FAILED_PERMANENT),
                // m4 and m5 have no records (never attempted)
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.summary.totalInspected).isEqualTo(5)
            assertThat(report.summary.delivered).isEqualTo(1)
            assertThat(report.summary.uncertain).isEqualTo(1)
            assertThat(report.summary.failedPermanent).isEqualTo(1)
            assertThat(report.summary.neverAttempted).isEqualTo(2)
            assertThat(report.summary.delivering).isEqualTo(0)
        }
    }

    // =========================================================================
    // autoPublishSafe
    // =========================================================================

    @Nested
    inner class AutoPublishSafe {

        @Test
        fun `publishes never attempted matches`() {
            val matches = listOf(canonical("m1"), canonical("m2"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())
            whenever(publicationService.publishIfNeeded(any())).thenReturn(
                DiscordPublicationResult(PublicationOutcome.PUBLISHED, "m1")
            )

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(2)
            assertThat(result.skippedCount).isEqualTo(0)
            assertThat(result.errorCount).isEqualTo(0)
            verify(publicationService, times(2)).publishIfNeeded(any())
        }

        @Test
        fun `skips failed permanent matches - requires manual forcePublish`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.FAILED_PERMANENT),
            ))

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(publicationService, never()).publishIfNeeded(any())
        }

        @Test
        fun `skips delivered matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERED),
            ))

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(publicationService, never()).publishIfNeeded(any())
        }

        @Test
        fun `skips delivery uncertain matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN),
            ))

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(publicationService, never()).publishIfNeeded(any())
        }

        @Test
        fun `skips delivering matches`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERING),
            ))

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(1)
            verify(publicationService, never()).publishIfNeeded(any())
        }

        @Test
        fun `records errors when publication fails`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())
            whenever(publicationService.publishIfNeeded(any())).thenReturn(
                DiscordPublicationResult(
                    PublicationOutcome.FAILED_BEFORE_SEND,
                    "m1",
                    errorMessage = "Webhook not configured"
                )
            )

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.errorCount).isEqualTo(1)
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].matchId).isEqualTo("m1")
            assertThat(result.errors[0].error).isEqualTo("Webhook not configured")
        }

        @Test
        fun `handles exceptions during publication`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(emptyMap())
            whenever(publicationService.publishIfNeeded(any())).thenThrow(
                RuntimeException("Database error")
            )

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.errorCount).isEqualTo(1)
            assertThat(result.errors[0].error).contains("Database error")
        }

        @Test
        fun `handles mixed results correctly`() {
            val matches = listOf(canonical("m1"), canonical("m2"), canonical("m3"), canonical("m4"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m2" to PublicationRecord("m2", PublicationState.DELIVERED), // Skip
                "m3" to PublicationRecord("m3", PublicationState.DELIVERY_UNCERTAIN), // Skip
            ))
            
            // m1 and m4 are safe to publish (no records)
            whenever(publicationService.publishIfNeeded(argThat { matchId.value == "m1" })).thenReturn(
                DiscordPublicationResult(PublicationOutcome.PUBLISHED, "m1")
            )
            whenever(publicationService.publishIfNeeded(argThat { matchId.value == "m4" })).thenReturn(
                DiscordPublicationResult(
                    PublicationOutcome.FAILED_BEFORE_SEND,
                    "m4",
                    errorMessage = "Error"
                )
            )

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(1) // m1
            assertThat(result.skippedCount).isEqualTo(2) // m2, m3
            assertThat(result.errorCount).isEqualTo(1) // m4
        }

        @Test
        fun `returns empty result when no matches exist`() {
            whenever(canonicalRepo.findAll()).thenReturn(emptyList())
            whenever(store.loadRecords()).thenReturn(emptyMap())

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(0)
            assertThat(result.errorCount).isEqualTo(0)
        }
    }

    // =========================================================================
    // Safety guarantees
    // =========================================================================

    @Nested
    inner class SafetyGuarantees {

        @Test
        fun `never auto-publishes uncertain deliveries`() {
            val matches = (1..10).map { canonical("m$it") }
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(
                matches.associate { it.matchId.value to PublicationRecord(it.matchId.value, PublicationState.DELIVERY_UNCERTAIN) }
            )

            val result = reconciliationService.autoPublishSafe()

            assertThat(result.publishedCount).isEqualTo(0)
            assertThat(result.skippedCount).isEqualTo(10)
            verify(publicationService, never()).publishIfNeeded(any())
        }

        @Test
        fun `inspection correctly flags unsafe matches`() {
            val matches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.DELIVERED),
                "m2" to PublicationRecord("m2", PublicationState.DELIVERY_UNCERTAIN),
                "m3" to PublicationRecord("m3", PublicationState.DELIVERING),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.inspections.none { it.safeToAutoPublish }).isTrue
        }

        @Test
        fun `inspection correctly flags safe matches`() {
            val matches = listOf(canonical("m1"), canonical("m2"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                // m1 has no record - safe
                // m2 has no record - safe
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.inspections.all { it.safeToAutoPublish }).isTrue
        }

        @Test
        fun `FAILED_PERMANENT is not safe for auto-publish`() {
            val matches = listOf(canonical("m1"))
            whenever(canonicalRepo.findAll()).thenReturn(matches)
            whenever(store.loadRecords()).thenReturn(mapOf(
                "m1" to PublicationRecord("m1", PublicationState.FAILED_PERMANENT),
            ))

            val report = reconciliationService.inspectLatestPublications()

            assertThat(report.inspections[0].safeToAutoPublish).isFalse
        }
    }
}


