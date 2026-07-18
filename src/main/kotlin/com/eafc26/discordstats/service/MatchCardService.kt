package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MatchCardService(
    private val gateway: EaClubsGateway,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    sealed class MatchCardResult {
        data class Success(val presentation: MatchSummaryPresentation) : MatchCardResult()
        object NoMatches : MatchCardResult()
        object EaUnavailable : MatchCardResult()
    }

    fun getLatestMatchCard(): MatchCardResult {
        val clubId = props.ea.clubId

        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                return MatchCardResult.NoMatches
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                return MatchCardResult.EaUnavailable
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                return MatchCardResult.EaUnavailable
            }
        }

        val latest = matches.maxByOrNull { it.timestamp }
            ?: return MatchCardResult.NoMatches

        log.info("Building match card for match {}", latest.matchId)
        val presentation = MatchSummaryBuilder.build(latest, clubId)
        
        return MatchCardResult.Success(presentation)
    }
}

