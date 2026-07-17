package com.eafc26.discordstats.web

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.boot.SpringApplication
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Gracefully shuts down this Spring Boot process.
 *
 * Only accepts requests from 127.0.0.1 / ::1 so that network-mode users on
 * the local Wi-Fi cannot remotely stop the application.
 */
@RestController
class StopController(private val context: ApplicationContext) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/application/stop", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun stop(exchange: ServerWebExchange): Mono<ResponseEntity<Map<String, String>>> {
        val remoteAddr = exchange.request.remoteAddress?.address?.hostAddress ?: ""
        if (!isLocalhost(remoteAddr)) {
            log.warn("Rejected stop request from non-localhost address: {}", remoteAddr)
            return Mono.just(
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "Acessível apenas a partir de localhost."))
            )
        }

        log.info("Shutdown requested from localhost — stopping application")
        return Mono.fromCallable {
            Thread {
                Thread.sleep(300)
                SpringApplication.exit(context, { 0 })
            }.apply { isDaemon = false }.start()
            ResponseEntity.ok(mapOf("status" to "stopping", "message" to "Aplicativo encerrando..."))
        }
    }

    private fun isLocalhost(addr: String): Boolean =
        addr == "127.0.0.1" ||
        addr == "0:0:0:0:0:0:0:1" ||
        addr == "::1" ||
        addr.isEmpty()
}
