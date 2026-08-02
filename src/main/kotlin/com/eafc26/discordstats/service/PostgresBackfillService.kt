package com.eafc26.discordstats.service

import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class PostgresBackfillService(
    private val jsonRepository: JsonCanonicalMatchRepository,
    private val postgresRepository: PostgresCanonicalMatchRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun backfill(): PostgresBackfillResult {
        val allMatches = jsonRepository.findAll()
        var created = 0
        var updated = 0
        val failures = mutableListOf<PostgresBackfillFailure>()

        allMatches.forEach { match ->
            try {
                val existed = postgresRepository.findById(match.matchId) != null
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
