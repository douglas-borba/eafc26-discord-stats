package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AppShellContractTest {
    private val resources = Path.of("src/main/resources")
    private val shellPages = mapOf(
        "index.html" to "overview",
        "history.html" to "matches",
        "players.html" to "players",
        "compare.html" to "compare",
        "insights.html" to "clubHistory",
        "settings.html" to "settings",
    )

    @Test
    fun `all product pages use the shared AppShell`() {
        shellPages.forEach { (fileName, pageId) ->
            val html = read(fileName)

            assertThat(html)
                .contains("""data-app-page="$pageId"""")
                .contains("data-app-content")
                .contains("""href="/app-shell.css"""")
                .contains("""src="/app-shell.js"""")
        }
    }

    @Test
    fun `product pages no longer define local navigation`() {
        shellPages.keys.forEach { fileName ->
            assertThat(read(fileName))
                .doesNotContain("""<nav class="nav">""")
                .doesNotContain("""class="settings-link"""")
        }
    }

    @Test
    fun `shared navigation preserves routes and separates settings`() {
        val shell = Files.readString(resources.resolve("static/app-shell.js"))

        assertThat(shell)
            .contains("""label: "Visão Geral", href: "/" """.trim())
            .contains("""label: "Partidas", href: "/history"""")
            .contains("""label: "Jogadores", href: "/players"""")
            .contains("""label: "História do Clube", href: "/insights"""")
            .contains("""class="app-shell-sublink" href="/compare"""")
            .contains("""class="app-shell-utility"""")
            .contains("""href="/settings"""")
    }

    private fun read(fileName: String): String =
        Files.readString(resources.resolve(fileName))
}
