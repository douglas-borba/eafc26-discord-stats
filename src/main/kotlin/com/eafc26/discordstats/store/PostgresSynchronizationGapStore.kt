package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.SynchronizationGap
import com.eafc26.discordstats.service.SynchronizationGapStore
import org.springframework.jdbc.core.JdbcTemplate

/** Durable historical-gap state. This table must never be included in event-log retention. */
class PostgresSynchronizationGapStore(
    private val jdbcTemplate: JdbcTemplate,
) : SynchronizationGapStore {
    override fun findOpen(clubId: ClubId): SynchronizationGap? = jdbcTemplate.query(
        """
        SELECT club_id, anchor_match_id, first_observable_match_id, opened_at
        FROM synchronization_gaps
        WHERE club_id = ? AND state = 'OPEN'
        """.trimIndent(),
        { rs, _ ->
            SynchronizationGap(
                clubId = ClubId(rs.getString("club_id")),
                anchorMatchId = rs.getString("anchor_match_id"),
                firstObservableMatchId = rs.getString("first_observable_match_id"),
                openedAt = rs.getTimestamp("opened_at").toInstant(),
            )
        },
        clubId.value,
    ).firstOrNull()

    override fun openGap(gap: SynchronizationGap) {
        jdbcTemplate.update(
            """
            INSERT INTO synchronization_gaps
                (club_id, anchor_match_id, first_observable_match_id, state, opened_at, last_observed_at)
            VALUES (?, ?, ?, 'OPEN', now(), now())
            ON CONFLICT (club_id) DO UPDATE SET
                last_observed_at = now()
            """.trimIndent(),
            gap.clubId.value,
            gap.anchorMatchId,
            gap.firstObservableMatchId,
        )
    }
}
