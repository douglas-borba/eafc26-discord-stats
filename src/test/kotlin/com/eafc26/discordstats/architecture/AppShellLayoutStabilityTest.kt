package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AppShellLayoutStabilityTest {
    @Test
    fun `app shell reserves scrollbar space and owns the full available width`() {
        val css = Path.of("src/main/resources/static/app-shell.css").toFile().readText()

        assertThat(css).contains(
            "overflow-y: scroll;",
            "scrollbar-gutter: stable;",
            "width: 100%;",
            "grid-template-columns: var(--shell-sidebar-width) minmax(0, 1fr);",
        )
    }
}
