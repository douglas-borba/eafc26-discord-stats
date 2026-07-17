package com.eafc26.discordstats.web

import com.eafc26.discordstats.scheduler.PollingStatusHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
class PollingStatusController(private val statusHolder: PollingStatusHolder) {

    private val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    @GetMapping("/api/polling/status", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun status(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(
            buildMap {
                put("enabled", statusHolder.enabled)
                put("intervalSeconds", statusHolder.intervalSeconds)
                put("running", statusHolder.running)
                put("lastCheck", statusHolder.lastCheck?.let { fmt.format(it) })
                put("nextCheck", statusHolder.nextCheck?.let { fmt.format(it) })
                put("lastResult", statusHolder.lastResult)
            }
        )
}
