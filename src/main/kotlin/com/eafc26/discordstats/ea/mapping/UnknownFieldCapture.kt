package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.RawUnknownField
import com.eafc26.discordstats.domain.match.RawUnknownFields
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/**
 * Converts raw unknown field maps from Jackson's @JsonAnySetter into the
 * domain's [RawUnknownFields] with size and security protections.
 *
 * Experimental — for exploratory analysis only.
 */
object UnknownFieldCapture {

    private val log = LoggerFactory.getLogger(javaClass)

    private const val MAX_FIELDS = 50
    private const val MAX_VALUE_BYTES = 4096
    private const val MAX_TOTAL_BYTES = 16384

    private val SENSITIVE_PATTERNS = setOf(
        "token", "cookie", "auth", "secret", "password", "credential",
        "session", "key", "bearer", "jwt", "csrf", "xsrf",
    )

    fun capture(scope: String, unknownFields: Map<String, JsonNode>): RawUnknownFields {
        if (unknownFields.isEmpty()) return RawUnknownFields.empty(scope)

        val discovered = mutableSetOf<String>()
        var totalBytes = 0
        val fields = mutableListOf<RawUnknownField>()

        for ((name, node) in unknownFields) {
            if (fields.size >= MAX_FIELDS) {
                log.warn("Unknown field capture truncated at {} fields for scope={}", MAX_FIELDS, scope)
                break
            }

            if (isSensitive(name)) {
                log.debug("Skipping sensitive-looking unknown field: scope={}, name={}", scope, name)
                continue
            }

            val jsonType = classifyJsonType(node)
            val serialized = node.toString()
            val originalSize = serialized.toByteArray(Charsets.UTF_8).size

            val (value, truncated) = if (originalSize > MAX_VALUE_BYTES) {
                serialized.take(MAX_VALUE_BYTES) to true
            } else {
                serialized to false
            }

            totalBytes += value.toByteArray(Charsets.UTF_8).size
            if (totalBytes > MAX_TOTAL_BYTES) {
                log.warn("Unknown field capture total size exceeded {}B for scope={}", MAX_TOTAL_BYTES, scope)
                fields.add(RawUnknownField(name, jsonType, value, truncated = true, originalSize = originalSize))
                break
            }

            fields.add(RawUnknownField(name, jsonType, value, truncated, originalSize))
            discovered.add(name)
        }

        if (discovered.isNotEmpty()) {
            log.info(
                "EA unknown fields discovered: scope={}, count={}, names={}",
                scope, discovered.size, discovered.sorted(),
            )
        }

        return RawUnknownFields.of(scope, fields)
    }

    private fun classifyJsonType(node: JsonNode): String = when {
        node.isObject -> "object"
        node.isArray -> "array"
        node.isTextual -> "string"
        node.isNumber -> "number"
        node.isBoolean -> "boolean"
        node.isNull -> "null"
        else -> "unknown"
    }

    private fun isSensitive(name: String): Boolean {
        val lower = name.lowercase()
        return SENSITIVE_PATTERNS.any { lower.contains(it) }
    }
}
