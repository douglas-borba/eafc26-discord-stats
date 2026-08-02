package com.eafc26.discordstats.config.postgres

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.MirroringCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
@Import(DataSourceAutoConfiguration::class)
class PostgresMirrorConfig {

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.migrate()
        return flyway
    }

    @Bean
    fun postgresCanonicalMatchRepository(
        jdbcTemplate: JdbcTemplate,
        objectMapper: ObjectMapper,
    ): PostgresCanonicalMatchRepository {
        return PostgresCanonicalMatchRepository(jdbcTemplate, objectMapper)
    }

    @Bean
    @Primary
    fun mirroringCanonicalMatchRepository(
        jsonRepository: JsonCanonicalMatchRepository,
        postgresRepository: PostgresCanonicalMatchRepository,
    ): CanonicalMatchRepository {
        return MirroringCanonicalMatchRepository(jsonRepository, postgresRepository)
    }
}
