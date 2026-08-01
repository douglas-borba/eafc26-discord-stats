package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.MatchResponse
import java.time.ZoneId

/**
 * Test-only dual execution harness. It cannot participate in production flow.
 */
class DiscordShadowMode(
    private val canonical: DiscordRenderer,
) {
    fun compare(
        source: MatchResponse,
        clubId: String,
        zoneId: ZoneId,
        proNames: Map<String, String> = emptyMap(),
    ): DiscordShadowResult {
        val legacyMatch = DiscordEmbedBuilder.build(source, clubId, zoneId, proNames)
        val legacyHistory = HistoryEmbedBuilder.build(source, clubId, zoneId, proNames)
        val normalized = (EaMatchMapper().map(source, proNames) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(normalized, ClubId(clubId))
        val stories = MatchStoryExtractor().extract(interpretation)
        val canonicalMatch = canonical.renderMatch(normalized, interpretation, stories, zoneId)
        val canonicalHistory = canonical.renderHistory(normalized, interpretation, stories, zoneId)
        return DiscordShadowResult(
            legacyMatch,
            canonicalMatch,
            legacyHistory,
            canonicalHistory,
            comparePayload("match", legacyMatch, canonicalMatch) +
                comparePayload("history", legacyHistory, canonicalHistory),
        )
    }

    private fun comparePayload(
        prefix: String,
        legacy: DiscordPayload,
        canonical: DiscordPayload,
    ): List<String> = buildList {
        if (legacy.embeds.size != canonical.embeds.size) add("$prefix.embedCount")
        legacy.embeds.zip(canonical.embeds).forEachIndexed { index, (old, new) ->
            val path = "$prefix.embeds[$index]"
            if (old.title != new.title) add("$path.title")
            if (old.description != new.description) add("$path.description")
            if (old.color != new.color) add("$path.color")
            if (old.fields != new.fields) add("$path.fields")
            if (old.footer != new.footer) add("$path.footer")
            if (old.timestamp != new.timestamp) add("$path.timestamp")
        }
        if (legacy != canonical && isEmpty()) add("$prefix.payload")
    }
}

data class DiscordShadowResult(
    val legacyMatch: DiscordPayload,
    val canonicalMatch: DiscordPayload,
    val legacyHistory: DiscordPayload,
    val canonicalHistory: DiscordPayload,
    val divergences: List<String>,
) {
    val hasParity: Boolean
        get() = divergences.isEmpty() &&
            legacyMatch == canonicalMatch &&
            legacyHistory == canonicalHistory
}
