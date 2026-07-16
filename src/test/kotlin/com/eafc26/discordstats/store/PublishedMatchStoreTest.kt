package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.StoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class PublishedMatchStoreTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var store: PublishedMatchStore
    private lateinit var storePath: Path

    @BeforeEach
    fun setUp() {
        storePath = dir.resolve("published-matches.json")
        store = makeStore(storePath)
    }

    // -- loadIds --

    @Test
    fun `loadIds returns empty set when file does not exist`() {
        assertThat(store.loadIds()).isEmpty()
    }

    @Test
    fun `loadIds returns stored IDs`() {
        storePath.writeText("""["id1","id2","id3"]""")
        assertThat(store.loadIds()).containsExactlyInAnyOrder("id1", "id2", "id3")
    }

    @Test
    fun `loadIds throws IllegalStateException for malformed JSON`() {
        storePath.writeText("{not valid json}")
        assertThatThrownBy { store.loadIds() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("malformed JSON")
    }

    // -- saveIds --

    @Test
    fun `saveIds creates file and parent directories`() {
        val nested = dir.resolve("sub/dir/ids.json")
        val nestedStore = makeStore(nested)
        nestedStore.saveIds(setOf("aaa", "bbb"))
        val loaded = nestedStore.loadIds()
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
        val tmpFiles = dir.toFile().listFiles { f -> f.name.endsWith(".tmp") }
        assertThat(tmpFiles).isEmpty()
    }

    // -- helpers --

    private fun makeStore(path: Path): PublishedMatchStore {
        val om = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val props = AppProperties(store = StoreProperties(path = path.toString()))
        return PublishedMatchStore(props, om)
    }
}
