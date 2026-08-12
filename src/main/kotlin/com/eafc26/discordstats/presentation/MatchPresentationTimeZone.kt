package com.eafc26.discordstats.presentation

import java.time.ZoneId

/**
 * Time zone used when presenting football match instants to the product's
 * Brazilian audience. Canonical timestamps remain UTC [java.time.Instant]s.
 */
object MatchPresentationTimeZone {
    val BRAZIL: ZoneId = ZoneId.of("America/Sao_Paulo")
}
