package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.match.ClubId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PublishedMatchStoreClubIsolationTest {
    @TempDir lateinit var tempDir: Path

    private val clubA = ClubId("club-a")
    private val clubB = ClubId("club-b")
    private lateinit var originalUserHome: String

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun `same match id has independent states and survives restart`() {
        val store = store()
        store.saveRecord(clubA, PublicationRecord("same-match", PublicationState.DELIVERED, updatedAt = 100))
        store.saveRecord(clubB, PublicationRecord("same-match", PublicationState.FAILED_PERMANENT, updatedAt = 200))

        val restarted = store()
        assertThat(restarted.find(clubA, "same-match")?.state).isEqualTo(PublicationState.DELIVERED)
        assertThat(restarted.find(clubB, "same-match")?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        assertThat(AppDataPaths.publicationStoreFile(clubA)).exists()
        assertThat(AppDataPaths.publicationStoreFile(clubB)).exists()
    }

    @Test
    fun `resolving one club never changes the other`() {
        val store = store()
        store.saveRecord(clubA, PublicationRecord("same-match", PublicationState.DELIVERY_UNCERTAIN))
        store.saveRecord(clubB, PublicationRecord("same-match", PublicationState.FAILED_PERMANENT))

        store.resolveAsDelivered(clubA, "same-match")

        assertThat(store.find(clubA, "same-match")?.state).isEqualTo(PublicationState.DELIVERED)
        assertThat(store.find(clubB, "same-match")?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
    }

    @Test
    fun `legacy Associação store migration is idempotent and preserves scoped records`() {
        val association = PublishedMatchStore.LEGACY_ASSOCIATION_BF
        val legacy = AppDataPaths.storeFile
        val scoped = AppDataPaths.publicationStoreFile(association)
        legacy.parent.toFile().mkdirs()
        scoped.parent.toFile().mkdirs()
        legacy.writeText(
            """[{"matchId":"shared","state":"DELIVERED","updatedAt":100},{"matchId":"legacy-only","state":"DELIVERY_UNCERTAIN","updatedAt":200}]""",
        )
        scoped.writeText(
            """[{"matchId":"shared","state":"FAILED_PERMANENT","updatedAt":300}]""",
        )

        val first = store().loadRecords(association)
        val second = store().loadRecords(association)

        assertThat(first["shared"]?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        assertThat(first["shared"]?.updatedAt).isEqualTo(300)
        assertThat(first["legacy-only"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        assertThat(second).isEqualTo(first)
        assertThat(legacy).exists()
        assertThat(legacy.readText()).contains("legacy-only")
    }

    private fun store() = PublishedMatchStore(
        ObjectMapper().registerModule(KotlinModule.Builder().build()),
    )
}
