package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.MatchResponse
import java.time.ZoneId

/**
 * Test-only dual execution harness. It never participates in production flow.
 */
class DashboardShadowMode(
    private val legacy: LegacyMatchSummaryBuilder,
    private val canonical: MatchSummaryBuilder,
) {
    fun compare(
        source: MatchResponse,
        clubId: String,
        zoneId: ZoneId,
        proNames: Map<String, String> = emptyMap(),
    ): DashboardShadowResult {
        val legacyResult = legacy.build(source, clubId, zoneId, proNames = proNames)
        val normalized = (EaMatchMapper().map(source, proNames) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(normalized, ClubId(clubId))
        val stories = MatchStoryExtractor().extract(interpretation)
        val canonicalResult = canonical.build(normalized, interpretation, stories, zoneId)
        return DashboardShadowResult(
            legacy = legacyResult,
            canonical = canonicalResult,
            divergentSections = divergentSections(legacyResult, canonicalResult),
        )
    }

    private fun divergentSections(
        legacy: MatchSummaryPresentation,
        canonical: MatchSummaryPresentation,
    ): List<String> = buildList {
        if (legacy.ourName != canonical.ourName || legacy.oppName != canonical.oppName) add("clubs")
        if (legacy.ourScore != canonical.ourScore || legacy.oppScore != canonical.oppScore) add("score")
        if (legacy.outcome != canonical.outcome) add("outcome")
        if (legacy.date != canonical.date || legacy.timestamp != canonical.timestamp) add("time")
        if (legacy.matchId != canonical.matchId) add("matchId")
        if (legacy.goals != canonical.goals) add("goals")
        if (legacy.assists != canonical.assists) add("assists")
        if (legacy.highlights != canonical.highlights) add("highlights")
        if (legacy.craque != canonical.craque) add("craque")
        if (legacy.offensiveNarratives != canonical.offensiveNarratives) add("offensiveNarratives")
        if (legacy.bagre != canonical.bagre) add("bagre")
        if (legacy.redCard != canonical.redCard) add("redCard")
        if (legacy.xerife != canonical.xerife) add("xerife")
        if (legacy.passePrecisao != canonical.passePrecisao) add("passePrecisao")
        if (legacy.correioExtraviado != canonical.correioExtraviado) add("correioExtraviado")
        if (legacy.muralha != canonical.muralha) add("muralha")
    }
}

data class DashboardShadowResult(
    val legacy: MatchSummaryPresentation,
    val canonical: MatchSummaryPresentation,
    val divergentSections: List<String>,
) {
    val hasParity: Boolean
        get() = divergentSections.isEmpty() && legacy == canonical
}
