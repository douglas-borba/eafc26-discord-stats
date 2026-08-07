package com.eafc26.discordstats.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class PublishedMatchStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var store: PublishedMatchStore
    private lateinit var storePath: Path
    private var originalUserHome: String? = null

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
        storePath = tempDir.resolve("Library/Application Support/EAFC26DiscordStats/published-matches.json")
        store = makeStore()
    }

    @AfterEach
    fun tearDown() {
        if (originalUserHome != null) System.setProperty("user.home", originalUserHome!!)
    }

    private fun makeStore(): PublishedMatchStore {
        val om = ObjectMapper().registerModule(KotlinModule.Builder().build())
        return PublishedMatchStore(om)
    }

    // =========================================================================
    // loadIds (backward compat)
    // =========================================================================

    @Test
    fun `loadIds returns empty set when file does not exist`() {
        assertThat(store.loadIds()).isEmpty()
    }

    @Test
    fun `loadIds returns IDs for DELIVERED and DELIVERY_UNCERTAIN records`() {
        store.saveRecord(PublicationRecord("id1", PublicationState.DELIVERED))
        store.saveRecord(PublicationRecord("id2", PublicationState.DELIVERY_UNCERTAIN))
        assertThat(store.loadIds()).containsExactlyInAnyOrder("id1", "id2")
    }

    @Test
    fun `loadIds throws IllegalStateException for malformed JSON`() {
        storePath.parent.toFile().mkdirs()
        storePath.writeText("{not valid json}")
        assertThatThrownBy { store.loadIds() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("malformed JSON")
    }

    // =========================================================================
    // saveIds (backward compat — baseline)
    // =========================================================================

    @Test
    fun `saveIds saves all IDs as BASELINED`() {
        store.saveIds(setOf("a", "b", "c"))
        val records = store.loadRecords()
        assertThat(records.keys).containsExactlyInAnyOrder("a", "b", "c")
        assertThat(records.values.map { it.state }.distinct()).containsExactly(PublicationState.BASELINED)
    }

    @Test
    fun `saveIds roundtrip persists all IDs`() {
        store.saveIds(setOf("match-1", "match-2", "match-3"))
        assertThat(store.loadIds()).containsExactlyInAnyOrder("match-1", "match-2", "match-3")
    }

    @Test
    fun `saveIds published IDs survive store restart`() {
        store.saveIds(setOf("match-1", "match-2"))
        assertThat(makeStore().loadIds()).containsExactlyInAnyOrder("match-1", "match-2")
    }

    @Test
    fun `saveIds overwrites previous contents`() {
        store.saveIds(setOf("old"))
        store.saveIds(setOf("new1", "new2"))
        assertThat(store.loadIds()).containsExactlyInAnyOrder("new1", "new2")
        assertThat(store.loadIds()).doesNotContain("old")
    }

    // =========================================================================
    // saveRecord / loadRecords
    // =========================================================================

    @Nested
    inner class RecordApi {

        @Test
        fun `saveRecord persists a DELIVERING record`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERING)
        }

        @Test
        fun `saveRecord updates existing record for same matchId`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `saveRecord preserves other records`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))
            store.saveRecord(PublicationRecord("m2", PublicationState.DELIVERING))
            assertThat(store.loadRecords()).hasSize(2)
        }

        @Test
        fun `removeRecord deletes a specific record`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))
            store.saveRecord(PublicationRecord("m2", PublicationState.DELIVERED))
            store.removeRecord("m1")
            assertThat(store.loadRecords().keys).containsExactly("m2")
        }

        @Test
        fun `removeRecord is no-op when matchId absent`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))
            store.removeRecord("nonexistent")
            assertThat(store.loadRecords()).hasSize(1)
        }
    }

    // =========================================================================
    // Startup upgrade: DELIVERING → DELIVERY_UNCERTAIN
    // =========================================================================

    @Nested
    inner class StartupUpgrade {

        @Test
        fun `DELIVERING records are upgraded to DELIVERY_UNCERTAIN on store init`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))
            // Restart
            val restarted = makeStore()
            assertThat(restarted.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        }

        @Test
        fun `DELIVERED records are unchanged on restart`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))
            val restarted = makeStore()
            assertThat(restarted.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `DELIVERY_UNCERTAIN records are unchanged on restart`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            val restarted = makeStore()
            assertThat(restarted.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        }
    }

    // =========================================================================
    // v1 → v2 migration
    // =========================================================================

    @Nested
    inner class LegacyMigration {

        @Test
        fun `v1 string-array is migrated to DELIVERED records on first load`() {
            storePath.parent.toFile().mkdirs()
            storePath.writeText("""["id1","id2"]""")
            val migrated = makeStore()
            assertThat(migrated.loadRecords().values.map { it.state }.distinct())
                .containsExactly(PublicationState.DELIVERED)
            assertThat(migrated.loadIds()).containsExactlyInAnyOrder("id1", "id2")
        }

        @Test
        fun `v1 migration creates backup`() {
            storePath.parent.toFile().mkdirs()
            storePath.writeText("""["id1"]""")
            makeStore()
            assertThat(storePath.resolveSibling("published-matches.json.v1.bak")).exists()
        }

        @Test
        fun `v1 migration never overwrites an existing backup`() {
            storePath.parent.toFile().mkdirs()
            storePath.writeText("""["id1","id2"]""")
            val backupPath = storePath.resolveSibling("published-matches.json.v1.bak")
            backupPath.toFile().writeText("ORIGINAL BACKUP CONTENT — must not be overwritten")

            // Second migration attempt (simulate accidental re-run)
            makeStore()

            assertThat(backupPath.toFile().readText()).isEqualTo("ORIGINAL BACKUP CONTENT — must not be overwritten")
        }

        @Test
        fun `empty store file treated as first run`() {
            assertThat(makeStore().loadIds()).isEmpty()
        }
    }

    // =========================================================================
    // Administrative resolution
    // =========================================================================

    @Nested
    inner class AdminResolution {

        @Test
        fun `resolveAsDelivered changes DELIVERY_UNCERTAIN to DELIVERED`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            store.resolveAsDelivered("m1")
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `resolveAsDelivered is no-op if already DELIVERED`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))
            store.resolveAsDelivered("m1")
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `resolveAsUndelivered removes the record`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            store.resolveAsUndelivered("m1")
            assertThat(store.loadRecords()).doesNotContainKey("m1")
        }
    }

    // =========================================================================
    // Atomicity
    // =========================================================================

    @Test
    fun `no temp file left after save`() {
        store.saveRecord(PublicationRecord("abc", PublicationState.DELIVERED))
        val tmpFiles = storePath.parent.toFile().listFiles { f -> f.name.endsWith(".tmp") }
        assertThat(tmpFiles ?: emptyArray()).isEmpty()
    }
}
