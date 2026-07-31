package com.eafc26.discordstats.ea.model

import com.eafc26.discordstats.ea.normalizeEaText

/**
 * Test-only DTO presentation behavior retained for legacy characterization.
 */
fun PlayerEntry.displayName(): String {
    val normalized = playerName
        ?.let(::normalizeEaText)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return normalized ?: if (isGoalkeeper()) "Goleiro BOT" else "Desconhecido"
}

fun PlayerEntry.displayName(proNames: Map<String, String>): String {
    val key = playerName?.trim()?.lowercase()
    if (key != null) {
        val proName = proNames[playerName]
            ?: proNames.entries.firstOrNull { it.key.trim().lowercase() == key }?.value
        if (!proName.isNullOrBlank()) return proName
    }
    return displayName()
}
