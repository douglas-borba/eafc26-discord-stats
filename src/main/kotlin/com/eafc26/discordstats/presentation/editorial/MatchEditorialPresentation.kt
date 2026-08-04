package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import java.time.Instant

/**
 * Editorial read model - presentation layer for match summaries.
 *
 * This is NOT part of the canonical domain. It contains formatted copy,
 * editorial phrases and presentation-ready values derived from canonical
 * facts.
 *
 * The same [CanonicalMatch] may generate different editorial presentations
 * if the phrase bank is modified. This is intentional - copy evolution
 * should not require re-interpretation of football decisions.
 */
data class MatchEditorialPresentation(
    val clubId: ClubId,
    val matchId: MatchId,
    val playedAt: Instant,
    val schemaVersion: Int,
    val phraseBankVersion: String,
    val presentation: MatchSummaryPresentation,
    val generatedAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
