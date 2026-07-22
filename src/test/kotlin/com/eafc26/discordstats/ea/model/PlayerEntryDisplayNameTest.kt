package com.eafc26.discordstats.ea.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlayerEntryDisplayNameTest {

    // -------------------------------------------------------------------------
    // displayName() — no proNames map
    // -------------------------------------------------------------------------

    @Nested
    inner class DisplayNameNoMap {

        @Test
        fun `returns playerName when present`() {
            val player = PlayerEntry(playerName = "dbeng_bass")
            assertThat(player.displayName()).isEqualTo("dbeng_bass")
        }

        @Test
        fun `returns Desconhecido when playerName is null`() {
            val player = PlayerEntry(playerName = null)
            assertThat(player.displayName()).isEqualTo("Desconhecido")
        }

        @Test
        fun `returns Desconhecido when playerName is blank`() {
            val player = PlayerEntry(playerName = "   ")
            assertThat(player.displayName()).isEqualTo("Desconhecido")
        }

        @Test
        fun `returns Goleiro BOT when goalkeeper playerName is null`() {
            val player = PlayerEntry(playerName = null, position = "0")
            assertThat(player.displayName()).isEqualTo("Goleiro BOT")
        }

        @Test
        fun `returns Goleiro BOT when goalkeeper playerName is blank`() {
            val player = PlayerEntry(playerName = "  ", position = "0")
            assertThat(player.displayName()).isEqualTo("Goleiro BOT")
        }

        @Test
        fun `returns playerName for goalkeeper when name is present`() {
            val player = PlayerEntry(playerName = "GoalieKing", position = "0")
            assertThat(player.displayName()).isEqualTo("GoalieKing")
        }
    }

    // -------------------------------------------------------------------------
    // displayName(proNames) — with proNames map
    // -------------------------------------------------------------------------

    @Nested
    inner class DisplayNameWithMap {

        @Test
        fun `returns proName when exact match found`() {
            val player = PlayerEntry(playerName = "dbeng_bass")
            val proNames = mapOf("dbeng_bass" to "R. Nazário")
            assertThat(player.displayName(proNames)).isEqualTo("R. Nazário")
        }

        @Test
        fun `returns proName with case-insensitive match`() {
            val player = PlayerEntry(playerName = "Striker99")
            val proNames = mapOf("striker99" to "Ronaldinho")
            assertThat(player.displayName(proNames)).isEqualTo("Ronaldinho")
        }

        @Test
        fun `falls back to displayName when playerName not in map`() {
            val player = PlayerEntry(playerName = "unknown_user")
            val proNames = mapOf("dbeng_bass" to "R. Nazário")
            assertThat(player.displayName(proNames)).isEqualTo("unknown_user")
        }

        @Test
        fun `falls back to displayName when map is empty`() {
            val player = PlayerEntry(playerName = "dbeng_bass")
            assertThat(player.displayName(emptyMap())).isEqualTo("dbeng_bass")
        }

        @Test
        fun `falls back to Desconhecido when playerName is null and not in map`() {
            val player = PlayerEntry(playerName = null)
            assertThat(player.displayName(mapOf("dbeng_bass" to "R. Nazário"))).isEqualTo("Desconhecido")
        }

        @Test
        fun `falls back to Goleiro BOT when goalkeeper not in map`() {
            val player = PlayerEntry(playerName = null, position = "0")
            assertThat(player.displayName(mapOf("dbeng_bass" to "R. Nazário"))).isEqualTo("Goleiro BOT")
        }

        @Test
        fun `ignores proName entry with blank value`() {
            val player = PlayerEntry(playerName = "dbeng_bass")
            val proNames = mapOf("dbeng_bass" to "  ")
            // blank proName — the map lookup returns blank, which is not excluded by current impl,
            // but the fallback kicks in since displayName() with proNames checks isNullOrBlank
            assertThat(player.displayName(proNames)).isEqualTo("dbeng_bass")
        }

        @Test
        fun `goalkeeper returns proName when found in map`() {
            val player = PlayerEntry(playerName = "GoalieKing", position = "0")
            val proNames = mapOf("GoalieKing" to "G. Buffon")
            assertThat(player.displayName(proNames)).isEqualTo("G. Buffon")
        }
    }
}

