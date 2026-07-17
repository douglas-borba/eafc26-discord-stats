package com.eafc26.discordstats.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
        // Override user.home so AppDataPaths.storeFile resolves to temp directory
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())

        // After setting user.home, storeFile will resolve to:
        // tempDir/Library/Application Support/EAFC26DiscordStats/published-matches.json
        storePath = tempDir.resolve("Library/Application Support/EAFC26DiscordStats/published-matches.json")
        store = makeStore()
    }

    @AfterEach
    fun tearDown() {
        // Restore original user.home
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        }
    }

    // -- loadIds --

    @Test
    fun `loadIds returns empty set when file does not exist`() {
        assertThat(store.loadIds()).isEmpty()
    }

    @Test
    fun `loadIds returns stored IDs`() {
        storePath.parent.toFile().mkdirs()
        storePath.writeText("""["id1","id2","id3"]""")
        assertThat(store.loadIds()).containsExactlyInAnyOrder("id1", "id2", "id3")
    }

    @Test
    fun `loadIds throws IllegalStateException for malformed JSON`() {
        storePath.parent.toFile().mkdirs()
        storePath.writeText("{not valid json}")
        assertThatThrownBy { store.loadIds() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("malformed JSON")
    }

    // -- saveIds --

    @Test
    fun `saveIds creates file and parent directories`() {
        store.saveIds(setOf("aaa", "bbb"))
        val loaded = store.loadIds()
        assertThat(loaded).containsExactlyInAnyOrder("aaa", "bbb")
    }

    @Test
    fun `saveIds roundtrip preserves all IDs`() {
        val ids = setOf("match-1", "match-2", "match-3")
        store.saveIds(ids)
        assertThat(store.loadIds()).isEqualTo(ids)
    }

    @Test
    fun `saveIds does not store duplicates`() {
        store.saveIds(setOf("x", "x", "y"))
        assertThat(store.loadIds()).containsExactlyInAnyOrder("x", "y")
    }

    @Test
    fun `saveIds overwrites previous contents`() {
        store.saveIds(setOf("old"))
        store.saveIds(setOf("new1", "new2"))
        assertThat(store.loadIds()).containsExactlyInAnyOrder("new1", "new2")
        assertThat(store.loadIds()).doesNotContain("old")
    }

    @Test
    fun `no temp file left after save`() {
        store.saveIds(setOf("abc"))
        val tmpFiles = storePath.parent.toFile().listFiles { f -> f.name.endsWith(".tmp") }
        assertThat(tmpFiles ?: emptyArray()).isEmpty()
    }

    // -- helpers --

    private fun makeStore(): PublishedMatchStore {
        val om = ObjectMapper().registerModule(KotlinModule.Builder().build())
        return PublishedMatchStore(om)
    }
}
