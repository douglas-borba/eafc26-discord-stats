package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class PostgresBackfillService(
    private val jsonRepository: JsonCanonicalMatchRepository,
    private val postgresRepository: PostgresCanonicalMatchRepository,
    private val defaultClubProvider: DefaultClubProvider,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun backfill(): PostgresBackfillResult {
        return backfill(defaultClubProvider.get().clubId)
    }

    /** Legacy adapter above is intentionally limited to the configured default club. */
    fun backfill(clubId: ClubId): PostgresBackfillResult {
        val allMatches = jsonRepository.findAll(clubId)
        var created = 0
        var updated = 0
        val failures = mutableListOf<PostgresBackfillFailure>()

        allMatches.forEach { match ->
            try {
                val existed = readOriginContext.withOrigin(CanonicalReadOrigin.CLI_POSTGRES_BACKFILL) {
                    postgresRepository.findById(clubId, match.matchId) != null
                }
                postgresRepository.save(match)
                if (existed) updated++ else created++
            } catch (ex: Exception) {
                log.error("Backfill failed for match {}", match.matchId.value, ex)
                failures += PostgresBackfillFailure(match.matchId.value, ex.message ?: ex.javaClass.simpleName)
            }
        }

        return PostgresBackfillResult(
            found = allMatches.size,
            created = created,
            updated = updated,
            failures = failures,
        )
    }
}

data class PostgresBackfillResult(
    val found: Int,
    val created: Int,
    val updated: Int,
    val failures: List<PostgresBackfillFailure>,
)

data class PostgresBackfillFailure(
    val matchId: String,
    val message: String,
)
