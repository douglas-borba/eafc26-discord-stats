package com.eafc26.discordstats.scheduler

import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PollingStatusHolder {
    @Volatile var enabled: Boolean = false
    @Volatile var intervalSeconds: Int = 120
    @Volatile var running: Boolean = false
    @Volatile var lastCheck: Instant? = null
    @Volatile var nextCheck: Instant? = null
    @Volatile var lastResult: String = "Ainda não executada"
}
