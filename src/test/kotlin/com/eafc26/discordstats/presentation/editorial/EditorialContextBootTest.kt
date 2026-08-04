package com.eafc26.discordstats.presentation.editorial

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.postgres.mirror-enabled=true",
        "app.polling.enabled=false",
        "eafc.dashboard.auto-open=false",
    ],
)
@Testcontainers
class EditorialContextBootTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
            withInitScript("init-anon-role.sql")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var editorialService: MatchEditorialPresentationService

    @Autowired
    private lateinit var editorialRepository: MatchEditorialPresentationRepository

    @Autowired
    private lateinit var backfillService: MatchEditorialBackfillService

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `editorial beans are created when mirror is enabled`() {
        assertThat(editorialService).isNotNull
        assertThat(editorialRepository).isNotNull
        assertThat(backfillService).isNotNull
        assertThat(clock).isNotNull
    }
}
