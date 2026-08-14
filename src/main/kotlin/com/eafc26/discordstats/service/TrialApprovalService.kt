package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.TrialApprovalResult
import com.eafc26.discordstats.application.club.TrialService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Commits the commercial decision before any external EA snapshot is attempted. */
@Service
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class TrialApprovalService(
    private val trials: TrialService,
) {
    @Transactional
    fun approve(
        requestId: Long,
        clubId: ClubId,
        displayName: ClubName,
        platform: EaPlatform,
    ): TrialApprovalResult = trials.approve(requestId, clubId, displayName, platform)
}
