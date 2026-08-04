package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.config.PhraseBank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Clock

/**
 * Tests for phrase bank version stability using SHA-256.
 *
 * Guarantees:
 * - Same content always produces same hash
 * - Category change affects hash
 * - Phrase order change affects hash
 * - Single phrase change affects hash
 * - Category insertion order does NOT affect hash
 */
@ExtendWith(MockitoExtension::class)
class PhraseBankVersionStabilityTest {

    @Mock
    private lateinit var phraseBank: PhraseBank

    @Mock
    private lateinit var repository: MatchEditorialPresentationRepository

    @Mock
    private lateinit var matchSummaryBuilder: com.eafc26.discordstats.presentation.MatchSummaryBuilder

    @Mock
    private lateinit var clock: Clock

    private lateinit var service: MatchEditorialPresentationService

    @BeforeEach
    fun setUp() {
        service = MatchEditorialPresentationService(matchSummaryBuilder, phraseBank, repository, clock)
    }

    @Test
    fun `same content produces same hash`() {
        val phrases = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B", "Phrase C"),
            "XERIFE" to listOf("Phrase D", "Phrase E"),
        )

        whenever(phraseBank.getAll()).thenReturn(phrases)

        val hash1 = service.calculatePhraseBankVersion()
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).hasSize(64) // SHA-256 hex = 64 chars
    }

    @Test
    fun `adding category changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A"),
        )
        val withNewCategory = mapOf(
            "MVP" to listOf("Phrase A"),
            "XERIFE" to listOf("Phrase B"),
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(withNewCategory)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `removing category changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A"),
            "XERIFE" to listOf("Phrase B"),
        )
        val withoutCategory = mapOf(
            "MVP" to listOf("Phrase A"),
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(withoutCategory)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `changing phrase order changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B", "Phrase C"),
        )
        val reordered = mapOf(
            "MVP" to listOf("Phrase A", "Phrase C", "Phrase B"), // B and C swapped
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(reordered)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `changing single phrase changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B", "Phrase C"),
        )
        val modified = mapOf(
            "MVP" to listOf("Phrase A", "Modified Phrase", "Phrase C"),
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(modified)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `category insertion order does NOT affect hash`() {
        // Different insertion order in LinkedHashMap
        val order1 = linkedMapOf(
            "MVP" to listOf("Phrase A"),
            "XERIFE" to listOf("Phrase B"),
            "BAGRE" to listOf("Phrase C"),
        )
        val order2 = linkedMapOf(
            "BAGRE" to listOf("Phrase C"),
            "MVP" to listOf("Phrase A"),
            "XERIFE" to listOf("Phrase B"),
        )

        whenever(phraseBank.getAll()).thenReturn(order1)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(order2)
        val hash2 = service.calculatePhraseBankVersion()

        // Both should produce same hash because content is sorted alphabetically
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `adding phrase at end changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B"),
        )
        val withNewPhrase = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B", "Phrase C"),
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(withNewPhrase)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `removing phrase changes hash`() {
        val original = mapOf(
            "MVP" to listOf("Phrase A", "Phrase B", "Phrase C"),
        )
        val withoutPhrase = mapOf(
            "MVP" to listOf("Phrase A", "Phrase C"), // Removed B
        )

        whenever(phraseBank.getAll()).thenReturn(original)
        val hash1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(withoutPhrase)
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `empty phrase bank produces stable hash`() {
        val empty = emptyMap<String, List<String>>()

        whenever(phraseBank.getAll()).thenReturn(empty)

        val hash1 = service.calculatePhraseBankVersion()
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).hasSize(64)
    }

    @Test
    fun `unicode phrases produce stable hash`() {
        val phrases = mapOf(
            "MVP" to listOf("Frase com acentuação: São José", "Emoji: 🏆⚽🎯"),
        )

        whenever(phraseBank.getAll()).thenReturn(phrases)

        val hash1 = service.calculatePhraseBankVersion()
        val hash2 = service.calculatePhraseBankVersion()

        assertThat(hash1).isEqualTo(hash2)
    }
}
