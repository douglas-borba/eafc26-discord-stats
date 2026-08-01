package com.eafc26.discordstats.web

import com.eafc26.discordstats.comparison.MatchComparisonResult
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.presentation.comparison.MatchComparisonOptionsResponse
import com.eafc26.discordstats.presentation.comparison.MatchComparisonPresenter
import com.eafc26.discordstats.presentation.comparison.MatchComparisonResponse
import com.eafc26.discordstats.service.MatchComparisonService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class MatchComparisonController(
    private val matchComparisonService: MatchComparisonService,
) {
    @GetMapping("/compare", produces = [MediaType.TEXT_HTML_VALUE])
    fun comparisonPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("compare.html"))

    @GetMapping("/api/match-comparisons/options", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listOptions(): Mono<ResponseEntity<MatchComparisonOptionsResponse>> =
        Mono.fromCallable {
            val options = matchComparisonService.listOptions()
            ResponseEntity.ok(
                MatchComparisonOptionsResponse(
                    status = if (options.isEmpty()) "empty" else "success",
                    matches = options.map(MatchComparisonPresenter::option),
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/api/match-comparisons", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun compare(
        @RequestParam firstMatchId: String,
        @RequestParam secondMatchId: String,
    ): Mono<ResponseEntity<MatchComparisonResponse>> =
        Mono.fromCallable {
            when (
                val result = matchComparisonService.compare(
                    MatchId(firstMatchId),
                    MatchId(secondMatchId),
                )
            ) {
                is MatchComparisonResult.Success -> ResponseEntity.ok(
                    MatchComparisonResponse(
                        status = "success",
                        comparison = MatchComparisonPresenter.comparison(result.comparison),
                    )
                )
                is MatchComparisonResult.NotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    MatchComparisonResponse(
                        status = "not_found",
                        missingMatchIds = result.missingMatchIds.map { it.value }.sorted(),
                        message = "Uma ou mais partidas não foram encontradas no histórico.",
                    )
                )
            }
        }.subscribeOn(Schedulers.boundedElastic())
}
