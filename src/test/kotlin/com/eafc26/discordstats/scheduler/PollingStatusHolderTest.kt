package com.eafc26.discordstats.scheduler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [PollingStatusHolder] state management.
 *
 * These tests verify that the monitoring state behaves correctly,
 * particularly the default values that are important for proper
 * UI display during application startup.
 */
class PollingStatusHolderTest {

    @Test
    fun `enabled defaults to true since polling is enabled by default`() {
        val holder = PollingStatusHolder()
        
        // Default value should be true because:
        // - MatchPollingScheduler uses @ConditionalOnProperty with matchIfMissing=true
        // - This means polling is enabled unless explicitly disabled
        assertThat(holder.enabled).isTrue()
    }

    @Test
    fun `intervalSeconds defaults to 60`() {
        val holder = PollingStatusHolder()
        
        assertThat(holder.intervalSeconds).isEqualTo(60)
    }

    @Test
    fun `running defaults to false`() {
        val holder = PollingStatusHolder()
        
        assertThat(holder.running).isFalse()
    }

    @Test
    fun `lastCheck defaults to null`() {
        val holder = PollingStatusHolder()
        
        assertThat(holder.lastCheck).isNull()
    }

    @Test
    fun `nextCheck defaults to null`() {
        val holder = PollingStatusHolder()
        
        assertThat(holder.nextCheck).isNull()
    }

    @Test
    fun `lastResult has meaningful default message`() {
        val holder = PollingStatusHolder()
        
        assertThat(holder.lastResult).isNotBlank()
        assertThat(holder.lastResult).doesNotContain("null")
    }

    @Test
    fun `enabled state can be updated`() {
        val holder = PollingStatusHolder()
        
        holder.enabled = false
        assertThat(holder.enabled).isFalse()
        
        holder.enabled = true
        assertThat(holder.enabled).isTrue()
    }

    @Test
    fun `all fields are volatile for thread safety`() {
        // This test uses reflection to verify thread safety annotations
        val holderClass = PollingStatusHolder::class.java
        
        val volatileFields = holderClass.declaredFields
            .filter { java.lang.reflect.Modifier.isVolatile(it.modifiers) }
            .map { it.name }
        
        // All state fields should be volatile for proper visibility across threads
        assertThat(volatileFields).containsExactlyInAnyOrder(
            "enabled",
            "intervalSeconds", 
            "running",
            "lastCheck",
            "nextCheck",
            "lastResult"
        )
    }
}

