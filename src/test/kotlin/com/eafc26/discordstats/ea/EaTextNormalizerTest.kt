package com.eafc26.discordstats.ea

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EaTextNormalizerTest {

    @Test
    fun `converts mojibake club name to correct UTF-8`() {
        assertThat(normalizeEaText("AssociaÃ§Ã£o BF"))
            .isEqualTo("Associação BF")
    }

    @Test
    fun `converts mojibake stadium name to correct UTF-8`() {
        assertThat(normalizeEaText("EstÃ¡dio"))
            .isEqualTo("Estádio")
    }

    @Test
    fun `leaves ASCII-only string unchanged`() {
        assertThat(normalizeEaText("SAF Refguiados")).isEqualTo("SAF Refguiados")
    }

    @Test
    fun `leaves already-correct UTF-8 name unchanged`() {
        assertThat(normalizeEaText("Associação BF"))
            .isEqualTo("Associação BF")
    }

    @Test
    fun `leaves already-correct single accented char unchanged`() {
        assertThat(normalizeEaText("café")).isEqualTo("café")
    }

    @Test
    fun `converts mojibake accented e to correct char`() {
        assertThat(normalizeEaText("cafÃ©")).isEqualTo("café")
    }

    @Test
    fun `leaves empty string unchanged`() {
        assertThat(normalizeEaText("")).isEqualTo("")
    }

    @Test
    fun `leaves numeric string unchanged`() {
        assertThat(normalizeEaText("1104972")).isEqualTo("1104972")
    }
}
