package com.eafc26.discordstats.domain.match

/**
 * Experimental capture of JSON properties received from the EA API that are
 * not declared in our DTOs. Purely exploratory — not a stable domain contract.
 *
 * Each entry preserves the original field name and its JSON value as a string
 * (serialized JsonNode). The [jsonType] field records the JSON type for display
 * without requiring re-parsing.
 *
 * [truncated] is true when the serialized value exceeded the safety limit and
 * was cut. [originalSize] records the pre-truncation byte count.
 */
data class RawUnknownField(
    val name: String,
    val jsonType: String,
    val value: String,
    val truncated: Boolean = false,
    val originalSize: Int = 0,
)

/**
 * Unknown fields captured at a specific DTO scope (player, match, etc.).
 *
 * [capturedAt] distinguishes between:
 * - null: unknown field capture was not active when this data was acquired
 * - empty list: capture was active, but no unknown fields were received
 * - non-empty list: unknown fields were received
 *
 * This three-way distinction is critical:
 * UNAVAILABLE (null) != EMPTY (no unknowns received) != PRESENT (unknowns found)
 */
data class RawUnknownFields(
    val scope: String,
    val fields: List<RawUnknownField>?,
) {
    companion object {
        fun unavailable(scope: String) = RawUnknownFields(scope, null)
        fun empty(scope: String) = RawUnknownFields(scope, emptyList())
        fun of(scope: String, fields: List<RawUnknownField>) = RawUnknownFields(scope, fields)
    }
}
