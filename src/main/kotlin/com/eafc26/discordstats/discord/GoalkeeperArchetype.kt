package com.eafc26.discordstats.discord

/**
 * Classifies a goalkeeper's match into a performance archetype.
 *
 * The EA rating is the primary signal; the individual save-type statistics
 * (impactSaves, total saves, goals conceded) explain *why* that rating happened
 * and refine the classification when the numbers tell a story that the raw
 * rating alone might not capture (e.g. a bombarded goalkeeper who conceded goals
 * but stopped 10 shots should be classified as UNDER_SIEGE, not POOR).
 */
enum class GoalkeeperArchetype(
    /** Localised title shown in Discord and the web card. */
    val title: String,
) {
    /** Outstanding match — clean sheet or decisive interventions, high rating. */
    WALL("🧱 Paredão"),

    /** Solid, reliable match without spectacular moments. */
    SOLID("🧤 Seguro"),

    /** Bombarded — faced many shots; defence left the goalkeeper exposed. */
    UNDER_SIEGE("💣 Bombardeado"),

    /** Poor match — low rating, goals conceded, insufficient interventions. */
    POOR("🥬 Mão de Alface"),

    /** Very little involvement — barely worked during the match. */
    QUIET("🤷 Discreto"),
}

