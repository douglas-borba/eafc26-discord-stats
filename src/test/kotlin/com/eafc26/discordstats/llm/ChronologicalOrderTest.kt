package com.eafc26.discordstats.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Validates that chronological order is preserved in LLM context generation.
 * 
 * This is critical to ensure that:
 * - Manual republication/reconciliation does not affect editorial chronology
 * - The LLM receives matches in correct temporal order (newest first)
 * - Cache invalidation happens when match order changes
 */
class ChronologicalOrderTest {

    @Test
    fun `context key changes when match order changes`() {
        // Same matches, different chronological order -> different keys
        val key1 = LlmEditorialService.computeContextKey(
            "club1",
            listOf("match-5x1", "match-4x2", "match-3x0"), // newest first
            "v3",
            "model"
        )
        val key2 = LlmEditorialService.computeContextKey(
            "club1",
            listOf("match-4x2", "match-5x1", "match-3x0"), // different order (5x1 republished)
            "v3",
            "model"
        )
        
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `context key identical for same chronological order`() {
        val key1 = LlmEditorialService.computeContextKey(
            "club1",
            listOf("match-4x2", "match-3x1", "match-2x2"),
            "v3",
            "model"
        )
        val key2 = LlmEditorialService.computeContextKey(
            "club1",
            listOf("match-4x2", "match-3x1", "match-2x2"),
            "v3",
            "model"
        )
        
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `alphabetical ordering would break chronology`() {
        // This test documents why we must NOT sort alphabetically
        val chronological = listOf("match-c", "match-b", "match-a") // c is newest
        val alphabetical = chronological.sorted() // would be [a, b, c]
        
        val keyChronological = LlmEditorialService.computeContextKey(
            "club1", chronological, "v3", "model"
        )
        val keyAlphabetical = LlmEditorialService.computeContextKey(
            "club1", alphabetical, "v3", "model"
        )
        
        // They must be different - otherwise cache would be wrong
        assertThat(keyChronological).isNotEqualTo(keyAlphabetical)
    }

    @Test
    fun `context key includes all parameters`() {
        // Different club
        val key1 = LlmEditorialService.computeContextKey(
            "club1", listOf("m1", "m2"), "v3", "model"
        )
        val key2 = LlmEditorialService.computeContextKey(
            "club2", listOf("m1", "m2"), "v3", "model"
        )
        assertThat(key1).isNotEqualTo(key2)

        // Different prompt version
        val key3 = LlmEditorialService.computeContextKey(
            "club1", listOf("m1", "m2"), "v3", "model"
        )
        val key4 = LlmEditorialService.computeContextKey(
            "club1", listOf("m1", "m2"), "v4", "model"
        )
        assertThat(key3).isNotEqualTo(key4)

        // Different model
        val key5 = LlmEditorialService.computeContextKey(
            "club1", listOf("m1", "m2"), "v3", "model-a"
        )
        val key6 = LlmEditorialService.computeContextKey(
            "club1", listOf("m1", "m2"), "v3", "model-b"
        )
        assertThat(key5).isNotEqualTo(key6)
    }
}

