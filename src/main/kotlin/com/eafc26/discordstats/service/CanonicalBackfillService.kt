package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Explicit, delivery-free import of the complete recent EA window.
 */
@Service
class CanonicalBackfillService(
    @Qualifier("production") private val gateway: EaClubsGateway,
    private val props: AppProperties,
    private val canonicalMatchFactory: CanonicalMatchFactory,
    private val canonicalMatchRepository: CanonicalMatchRepository,
) {
    fun backfill(): CanonicalBackfillResult {
        val clubId = props.ea.clubId
        val before = canonicalMatchRepository.metadata().matchCount
        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> return CanonicalBackfillResult.Completed(
                requested = props.ea.maxResultCount,
                returned = 0,
                processed = 0,
                created = 0,
                updated = 0,
                ignored = 0,
                failures = emptyList(),
                before = before,
                after = before,
            )
            is EaApiResult.Unavailable -> return CanonicalBackfillResult.Unavailable(
                result.statusCode,
                result.message,
            )
            is EaApiResult.UnexpectedPayload -> return CanonicalBackfillResult.Unavailable(
                0,
                result.cause.message ?: "Unexpected EA payload",
            )
        }

        val proNames = when (val result = gateway.getMembersStats(clubId)) {
            is EaApiResult.Success -> result.data
                .filter { !it.playerName.isNullOrBlank() && !it.proName.isNullOrBlank() }
                .associate { it.playerName!! to it.proName!! }
            else -> emptyMap()
        }

        val orderedUnique = matches
            .sortedWith(compareBy<MatchResponse> { it.timestamp }.thenBy { it.matchId })
            .distinctBy { it.matchId }
        var created = 0
        var updated = 0
        val failures = mutableListOf<CanonicalBackfillFailure>()

        orderedUnique.forEach { source ->
            try {
                val canonical = canonicalMatchFactory.create(source, clubId, proNames)
                val existed = canonicalMatchRepository.findById(canonical.matchId) != null
                canonicalMatchRepository.save(canonical)
                if (existed) updated++ else created++
            } catch (ex: Exception) {
                failures += CanonicalBackfillFailure(
                    matchId = source.matchId,
                    message = ex.message ?: ex.javaClass.simpleName,
                )
            }
        }

        return CanonicalBackfillResult.Completed(
            requested = props.ea.maxResultCount,
            returned = matches.size,
            processed = created + updated,
            created = created,
            updated = updated,
            ignored = matches.size - orderedUnique.size,
            failures = failures,
            before = before,
            after = canonicalMatchRepository.metadata().matchCount,
        )
    }
}

sealed interface CanonicalBackfillResult {
    data class Completed(
        val requested: Int,
        val returned: Int,
        val processed: Int,
        val created: Int,
        val updated: Int,
        val ignored: Int,
        val failures: List<CanonicalBackfillFailure>,
        val before: Int,
        val after: Int,
    ) : CanonicalBackfillResult

    data class Unavailable(
        val statusCode: Int,
        val message: String,
    ) : CanonicalBackfillResult
}

data class CanonicalBackfillFailure(
    val matchId: String,
    val message: String,
)
