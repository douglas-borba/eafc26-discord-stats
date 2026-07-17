package com.eafc26.discordstats.web

import com.eafc26.discordstats.service.NotifyLatestService
import com.eafc26.discordstats.service.NotifyResult
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class MatchController(
    private val notifyLatestService: NotifyLatestService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/api/health", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "ok"))

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun index(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("index.html"))

    @PostMapping("/api/matches/notify-latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun notifyLatest(): Mono<ResponseEntity<NotifyResponse>> =
        Mono.fromCallable { notifyLatestService.notifyLatest() }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result ->
                when (result) {
                    is NotifyResult.Sent ->
                        ResponseEntity.ok(NotifyResponse("sent", "Notificação enviada com sucesso.", result.summary))
                    is NotifyResult.SentPersistenceError ->
                        ResponseEntity.ok(NotifyResponse("sent_persistence_error", "A partida foi enviada ao Discord, mas não foi possível salvar o histórico local.", result.summary))
                    is NotifyResult.AlreadyPublished ->
                        ResponseEntity.ok(NotifyResponse("already_published", "Partida já publicada anteriormente.", result.summary))
                    NotifyResult.NoMatches ->
                        ResponseEntity.ok(NotifyResponse("no_matches", "Nenhuma partida encontrada."))
                    NotifyResult.EaUnavailable ->
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(NotifyResponse("ea_unavailable", "API da EA indisponível. Tente novamente mais tarde."))
                    NotifyResult.DiscordError ->
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(NotifyResponse("discord_error", "Falha ao enviar para o Discord."))
                    NotifyResult.Busy ->
                        ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(NotifyResponse("busy", "Outra notificação já está em andamento."))
                }
            }
}
