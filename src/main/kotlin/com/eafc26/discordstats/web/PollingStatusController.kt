package com.eafc26.discordstats.web

import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.service.AcquisitionStateHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
class PollingStatusController(
    private val statusHolder: PollingStatusHolder,
    private val acquisitionStateHolder: AcquisitionStateHolder,
) {

    private val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    @GetMapping("/api/polling/status", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun status(): ResponseEntity<Map<String, Any?>> {
        val state = acquisitionStateHolder.current()

        return ResponseEntity.ok(
            buildMap {
                // Existing fields (backwards compatibility)
                put("enabled", statusHolder.enabled)
                put("intervalSeconds", statusHolder.intervalSeconds)
                put("running", state.isRunning())  // Now derived from AcquisitionStateHolder
                put("lastCheck", statusHolder.lastCheck?.let { fmt.format(it) })
                put("nextCheck", statusHolder.nextCheck?.let { fmt.format(it) })
                put("lastResult", state.currentStatus)  // Now from AcquisitionStateHolder

                // New acquisition state fields
                put("acquisition", buildMap {
                    put("executionId", state.executionId)
                    put("trigger", state.trigger?.name)
                    put("currentPhase", state.currentPhase.name)
                    put("completedPhases", state.completedPhases.map { it.name })
                    put("startedAt", state.startedAt?.let { fmt.format(it) })
                    put("finishedAt", state.finishedAt?.let { fmt.format(it) })
                    put("elapsedMs", state.elapsedDuration()?.toMillis())
                    put("lastError", state.lastError)
                    put("currentStatus", state.currentStatus)
                })
            }
        )
    }
}
