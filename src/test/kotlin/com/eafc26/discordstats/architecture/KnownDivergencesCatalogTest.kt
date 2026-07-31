package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Keeps the Phase 0 divergence catalog present and explicitly classified.
 *
 * Behavioral proof for each ID lives in
 * [CurrentPresentationCharacterizationTest.KnownDivergences].
 */
class KnownDivergencesCatalogTest {

    @Test
    fun `catalog contains every characterized divergence with a disposition`() {
        val catalog = loadCatalog()

        assertThat(catalog).contains(
            "DIV-001",
            "DIV-002",
            "DIV-003",
            "KNOWN DEFECT",
            "INTENTIONAL PRESENTATION DIFFERENCE",
        )
    }

    @Test
    fun `catalog does not leave any Phase 0 divergence unexplained`() {
        val catalog = loadCatalog()

        assertThat(catalog).doesNotContain("UNEXPLAINED")
        assertThat(Regex("""DIV-\d{3}""").findAll(catalog).map { it.value }.toSet())
            .containsExactlyInAnyOrder("DIV-001", "DIV-002", "DIV-003")
    }

    private fun loadCatalog(): String {
        val path = "architecture/phase-0-known-divergences.md"
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing Phase 0 divergence catalog: $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
