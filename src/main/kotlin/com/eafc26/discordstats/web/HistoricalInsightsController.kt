package com.eafc26.discordstats.web

import com.eafc26.discordstats.presentation.insight.HistoricalInsightPresenter
import com.eafc26.discordstats.presentation.insight.HistoricalInsightsResponse
import com.eafc26.discordstats.service.HistoricalInsightsService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class HistoricalInsightsController(
    private val historicalInsightsService: HistoricalInsightsService,
) {
    @GetMapping("/insights", produces = [MediaType.TEXT_HTML_VALUE])
    fun insightsPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("insights.html"))

    @GetMapping("/api/historical-insights", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun insights(): Mono<ResponseEntity<HistoricalInsightsResponse>> =
        Mono.fromCallable {
            ResponseEntity.ok(
                HistoricalInsightPresenter.response(historicalInsightsService.generate())
            )
        }.subscribeOn(Schedulers.boundedElastic())
}
