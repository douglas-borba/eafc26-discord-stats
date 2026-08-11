package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
@Qualifier("production")
class NodeEaClubsGateway(
    private val eaGatewayWebClient: WebClient,
    private val props: AppProperties,
    private val parser: EaResponseParser,
) : EaClubsGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchClubs(clubName: String) = request(
        "/ea/clubs/search",
        parser::parseSearch,
    ) { eaGatewayWebClient.get().uri { builder -> builder.path("/ea/clubs/search").queryParam("name", clubName).queryParam("platform", props.ea.platform).build() } }

    override fun getLatestMatches(clubId: String) = request(
        "/ea/clubs/${encode(clubId)}/matches?platform=${encode(props.ea.platform)}&maxResultCount=${props.ea.maxResultCount}",
        parser::parseMatches,
    )

    override fun getMembersStats(clubId: String) = request(
        "/ea/clubs/${encode(clubId)}/members?platform=${encode(props.ea.platform)}",
        parser::parseMembersStats,
    )

    private fun <T> request(path: String, parse: (String) -> EaApiResult<T>): EaApiResult<T> =
        request(path, parse) { eaGatewayWebClient.get().uri(path) }

    private fun <T> request(
        path: String,
        parse: (String) -> EaApiResult<T>,
        request: () -> WebClient.RequestHeadersSpec<*>,
    ): EaApiResult<T> {
        repeat(MAX_ATTEMPTS) { index ->
            try {
                val body = request().retrieve().bodyToMono(String::class.java).block() ?: "[]"
                return parse(body)
            } catch (ex: WebClientResponseException) {
                // An explicit HTTP response is not a transport/read failure. Never retry 4xx/5xx here.
                return httpFailure(path, index + 1, ex)
            } catch (ex: Exception) {
                if (index == MAX_ATTEMPTS - 1) return unavailableAfterRetries(path, index + 1, ex)
                log.warn("EA gateway transient read failure: path={}, attempt={}/{}", path, index + 1, MAX_ATTEMPTS, ex)
                Thread.sleep(RETRY_BACKOFF_MILLIS)
            }
        }
        error("unreachable")
    }

    private fun <T> httpFailure(path: String, attempt: Int, ex: WebClientResponseException): EaApiResult<T> {
        // HTTP status 2xx with body reading/parsing failure should be UnexpectedPayload, not Unavailable
        if (ex.statusCode.is2xxSuccessful) {
            return unexpectedPayload(path, attempt, ex)
        } else {
            log.warn("EA gateway HTTP failure: path={}, attempt={}, status={}", path, attempt, ex.statusCode.value())
            return EaApiResult.Unavailable(ex.statusCode.value(), ex.statusText)
        }
    }

    private fun <T> unexpectedPayload(path: String, attempt: Int, ex: Exception): EaApiResult<T> {
        log.error("EA gateway payload failure: path={}, attempt={}, exception={}, rootCause={}, message={}", path, attempt, ex.javaClass.name, rootCause(ex).javaClass.name, rootCause(ex).message)
        return EaApiResult.UnexpectedPayload(ex)
    }

    private fun <T> unavailableAfterRetries(path: String, attempt: Int, ex: Exception): EaApiResult<T> {
        val root = rootCause(ex)
        log.error("EA gateway transient failure exhausted: path={}, attempt={}, exception={}, rootCause={}, message={}", path, attempt, ex.javaClass.name, root.javaClass.name, root.message)
        return EaApiResult.Unavailable(0, "EA gateway transport failure after $attempt attempts: ${root.message ?: root.javaClass.simpleName}")
    }

    private fun rootCause(ex: Throwable): Throwable = generateSequence(ex) { it.cause }.last()

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private companion object { const val MAX_ATTEMPTS = 3; const val RETRY_BACKOFF_MILLIS = 400L }
}
