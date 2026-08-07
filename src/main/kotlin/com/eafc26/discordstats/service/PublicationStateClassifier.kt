package com.eafc26.discordstats.service

import com.eafc26.discordstats.store.PublicationState

/**
 * Single source of truth for publication state classification rules.
 *
 * Centralizes all logic for:
 * - Determining if a state is safe for automatic publication
 * - Classifying states for UI display
 * - Understanding state semantics
 */
object PublicationStateClassifier {

    /**
     * Determines if a match with the given publication state (or no state)
     * is safe to automatically publish via `publishIfNeeded()`.
     *
     * Safe conditions:
     * - No record exists (null) - never attempted
     *
     * Unsafe conditions (require manual intervention):
     * - DELIVERED - already published
     * - DELIVERING - ambiguous, should have been upgraded
     * - DELIVERY_UNCERTAIN - may have been delivered
     * - FAILED_PERMANENT - explicit rejection, requires correction AND manual resend via forcePublish
     * - BASELINED - intentionally not published, requires explicit action to publish
     */
    fun isSafeToAutoPublish(state: PublicationState?): Boolean {
        return when (state) {
            null -> true // Never attempted - safe to try
            PublicationState.DELIVERED -> false // Already delivered
            PublicationState.DELIVERING -> false // Ambiguous (should be upgraded to UNCERTAIN)
            PublicationState.DELIVERY_UNCERTAIN -> false // Requires manual resolution
            PublicationState.FAILED_PERMANENT -> false // Requires correction + manual forcePublish
            PublicationState.BASELINED -> false // Intentionally not published, needs explicit action
        }
    }

    /**
     * Returns display information for a publication state.
     * Used for consistent UI rendering across dashboard and reconciliation panels.
     */
    fun getDisplayInfo(state: PublicationState?): PublicationStateDisplay {
        return when (state) {
            null -> PublicationStateDisplay(
                icon = "➖",
                label = "Nunca tentada",
                cssClass = "never-attempted",
                color = "#8b949e",
                requiresAction = false,
            )
            PublicationState.DELIVERED -> PublicationStateDisplay(
                icon = "✅",
                label = "Publicada",
                cssClass = "delivered",
                color = "#3fb950",
                requiresAction = false,
            )
            PublicationState.DELIVERING -> PublicationStateDisplay(
                icon = "⏳",
                label = "Enviando",
                cssClass = "delivering",
                color = "#f0883e",
                requiresAction = false,
            )
            PublicationState.DELIVERY_UNCERTAIN -> PublicationStateDisplay(
                icon = "⚠️",
                label = "Incerta",
                cssClass = "uncertain",
                color = "#d29922",
                requiresAction = true,
            )
            PublicationState.FAILED_PERMANENT -> PublicationStateDisplay(
                icon = "❌",
                label = "Falha permanente",
                cssClass = "failed-permanent",
                color = "#f85149",
                requiresAction = true,
            )
            PublicationState.BASELINED -> PublicationStateDisplay(
                icon = "📚",
                label = "Baseline (não publicada)",
                cssClass = "baselined",
                color = "#6e7681",
                requiresAction = false,
            )
        }
    }

    /**
     * Determines if a state requires administrative action.
     */
    fun requiresAdministrativeAction(state: PublicationState?): Boolean {
        return when (state) {
            PublicationState.DELIVERY_UNCERTAIN,
            PublicationState.FAILED_PERMANENT -> true
            else -> false
        }
    }

    /**
     * Determines if a state represents a successful delivery.
     */
    fun isSuccessfullyDelivered(state: PublicationState?): Boolean {
        return state == PublicationState.DELIVERED
    }
}

/**
 * Display information for a publication state.
 */
data class PublicationStateDisplay(
    val icon: String,
    val label: String,
    val cssClass: String,
    val color: String,
    val requiresAction: Boolean,
)

