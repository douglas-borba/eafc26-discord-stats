package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.club.*
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp

class PostgresTrialRequestRepository(private val jdbc: JdbcTemplate) : TrialRequestRepository {
    override fun create(request: TrialRequest): TrialRequest {
        val id = jdbc.queryForObject("INSERT INTO trial_requests (club_name, requester_name, contact, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long::class.java, request.clubName, request.requesterName, request.contact, request.status.name, Timestamp.from(request.createdAt), Timestamp.from(request.updatedAt))!!
        return findById(id)!!
    }
    override fun findAll(): List<TrialRequest> = jdbc.query("SELECT * FROM trial_requests ORDER BY created_at DESC", { rs, _ -> map(rs) })
    override fun findById(id: Long): TrialRequest? = jdbc.query("SELECT * FROM trial_requests WHERE id = ?", { rs, _ -> map(rs) }, id).firstOrNull()
    override fun save(request: TrialRequest): TrialRequest {
        jdbc.update("UPDATE trial_requests SET status=?, club_id=?, approved_at=?, rejected_at=?, updated_at=? WHERE id=?", request.status.name, request.clubId?.value, request.approvedAt?.let(Timestamp::from), request.rejectedAt?.let(Timestamp::from), Timestamp.from(request.updatedAt), request.id)
        return findById(requireNotNull(request.id))!!
    }
    override fun transition(id: Long, expected: TrialRequestStatus, replacement: TrialRequest): TrialRequest? {
        val changed = jdbc.update("UPDATE trial_requests SET status=?, club_id=?, approved_at=?, rejected_at=?, updated_at=? WHERE id=? AND status=?", replacement.status.name, replacement.clubId?.value, replacement.approvedAt?.let(Timestamp::from), replacement.rejectedAt?.let(Timestamp::from), Timestamp.from(replacement.updatedAt), id, expected.name)
        return if (changed == 1) findById(id) else null
    }
    override fun findRecentPendingEquivalent(clubName: String, contact: String, since: java.time.Instant): TrialRequest? = jdbc.query("SELECT * FROM trial_requests WHERE status='PENDING' AND lower(regexp_replace(trim(club_name), '\\s+', ' ', 'g')) = ? AND lower(regexp_replace(trim(contact), '\\s+', ' ', 'g')) = ? AND created_at >= ? ORDER BY created_at DESC LIMIT 1", { rs,_ -> map(rs) }, clubName, contact, Timestamp.from(since)).firstOrNull()
    private fun map(rs: java.sql.ResultSet) = TrialRequest(rs.getLong("id"), rs.getString("club_name"), rs.getString("requester_name"), rs.getString("contact"), TrialRequestStatus.valueOf(rs.getString("status")), rs.getString("club_id")?.let(::ClubId), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("approved_at")?.toInstant(), rs.getTimestamp("rejected_at")?.toInstant())
}

class PostgresTrialMatchConsumptionRepository(private val jdbc: JdbcTemplate, private val tx: TransactionTemplate) : TrialMatchConsumptionRepository {
    override fun tryConsume(clubId: ClubId, matchId: MatchId, now: java.time.Instant): TrialConsumption = tx.execute {
        val club = jdbc.query("SELECT access_status, trial_limit FROM monitored_clubs WHERE club_id=? FOR UPDATE", { rs,_ -> rs.getString("access_status") to (rs.getObject("trial_limit") as? Int ?: 3) }, clubId.value).firstOrNull()
            ?: return@execute TrialConsumption.NotTrialOrLimitReached
        if (club.first != ClubAccessStatus.TRIAL.name) return@execute TrialConsumption.NotTrialOrLimitReached
        val existing = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trial_match_consumptions WHERE club_id=? AND match_id=?)", Boolean::class.java, clubId.value, matchId.value) == true
        if (existing) return@execute TrialConsumption.AlreadyCounted
        val count = count(clubId)
        if (count >= club.second) return@execute TrialConsumption.NotTrialOrLimitReached
        jdbc.update("INSERT INTO trial_match_consumptions (club_id, match_id, consumed_at) VALUES (?, ?, ?)", clubId.value, matchId.value, Timestamp.from(now))
        val next = count + 1
        val expired = next >= club.second
        if (expired) jdbc.update("UPDATE monitored_clubs SET access_status='TRIAL_EXPIRED', monitoring_enabled=false, updated_at=? WHERE club_id=?", Timestamp.from(now), clubId.value)
        TrialConsumption.Counted(TrialProgress(next, club.second, expired))
    }!!
    override fun count(clubId: ClubId): Int = jdbc.queryForObject("SELECT count(*) FROM trial_match_consumptions WHERE club_id = ?", Int::class.java, clubId.value) ?: 0
}
