package com.eafc26.discordstats.service

import com.eafc26.discordstats.store.PublicationState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicationStateClassifierTest {

    @Test
    fun `null state is not safe because a durable publication intent is required`() {
        assertThat(PublicationStateClassifier.isSafeToAutoPublish(null)).isFalse
    }

    @Test
    fun `DELIVERED is not safe to auto-publish`() {
        assertThat(PublicationStateClassifier.isSafeToAutoPublish(PublicationState.DELIVERED)).isFalse
    }

    @Test
    fun `DELIVERING is not safe to auto-publish`() {
        assertThat(PublicationStateClassifier.isSafeToAutoPublish(PublicationState.DELIVERING)).isFalse
    }

    @Test
    fun `DELIVERY_UNCERTAIN is not safe to auto-publish`() {
        assertThat(PublicationStateClassifier.isSafeToAutoPublish(PublicationState.DELIVERY_UNCERTAIN)).isFalse
    }

    @Test
    fun `FAILED_PERMANENT is not safe to auto-publish - requires manual forcePublish`() {
        // Critical: FAILED_PERMANENT requires correction AND manual forcePublish
        // publishIfNeeded() will return SKIPPED_FAILED_PERMANENT for this state
        assertThat(PublicationStateClassifier.isSafeToAutoPublish(PublicationState.FAILED_PERMANENT)).isFalse
    }

    @Test
    fun `only PENDING and FAILED_TRANSIENT are safe for auto-publish`() {
        val safeStates = PublicationState.values().filter {
            PublicationStateClassifier.isSafeToAutoPublish(it)
        }
        assertThat(safeStates).containsExactly(PublicationState.PENDING, PublicationState.FAILED_TRANSIENT)
    }

    @Test
    fun `DELIVERY_UNCERTAIN requires administrative action`() {
        assertThat(PublicationStateClassifier.requiresAdministrativeAction(PublicationState.DELIVERY_UNCERTAIN)).isTrue
    }

    @Test
    fun `FAILED_PERMANENT requires administrative action`() {
        assertThat(PublicationStateClassifier.requiresAdministrativeAction(PublicationState.FAILED_PERMANENT)).isTrue
    }

    @Test
    fun `DELIVERED does not require administrative action`() {
        assertThat(PublicationStateClassifier.requiresAdministrativeAction(PublicationState.DELIVERED)).isFalse
    }

    @Test
    fun `only DELIVERED represents successful delivery`() {
        assertThat(PublicationStateClassifier.isSuccessfullyDelivered(PublicationState.DELIVERED)).isTrue
        assertThat(PublicationStateClassifier.isSuccessfullyDelivered(PublicationState.DELIVERING)).isFalse
        assertThat(PublicationStateClassifier.isSuccessfullyDelivered(PublicationState.DELIVERY_UNCERTAIN)).isFalse
        assertThat(PublicationStateClassifier.isSuccessfullyDelivered(PublicationState.FAILED_PERMANENT)).isFalse
        assertThat(PublicationStateClassifier.isSuccessfullyDelivered(null)).isFalse
    }

    @Test
    fun `getDisplayInfo returns correct information for each state`() {
        val delivered = PublicationStateClassifier.getDisplayInfo(PublicationState.DELIVERED)
        assertThat(delivered.icon).isEqualTo("✅")
        assertThat(delivered.requiresAction).isFalse

        val uncertain = PublicationStateClassifier.getDisplayInfo(PublicationState.DELIVERY_UNCERTAIN)
        assertThat(uncertain.icon).isEqualTo("⚠️")
        assertThat(uncertain.requiresAction).isTrue

        val failed = PublicationStateClassifier.getDisplayInfo(PublicationState.FAILED_PERMANENT)
        assertThat(failed.icon).isEqualTo("❌")
        assertThat(failed.requiresAction).isTrue

        val neverAttempted = PublicationStateClassifier.getDisplayInfo(null)
        assertThat(neverAttempted.icon).isEqualTo("➖")
        assertThat(neverAttempted.requiresAction).isFalse
    }
}
