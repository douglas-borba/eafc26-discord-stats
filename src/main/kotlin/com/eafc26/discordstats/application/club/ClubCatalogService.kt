package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway

data class ClubSearchCandidate(
    val clubId: ClubId,
    val displayName: ClubName,
    val platform: EaPlatform,
    val currentDivision: Int?,
)

sealed interface ClubCatalogResult {
    data class Found(val candidates: List<ClubSearchCandidate>) : ClubCatalogResult
    data object Empty : ClubCatalogResult
    data object Unavailable : ClubCatalogResult
}

class ClubCatalogService(
    private val gateway: EaClubsGateway,
) {
    fun search(name: String): ClubCatalogResult {
        require(name.isNotBlank()) { "Club name must not be blank" }
        return when (val result = gateway.searchClubs(name.trim())) {
            is EaApiResult.Success -> {
                val candidates = result.data.mapNotNull { external ->
                    val displayName = external.resolvedName()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val platform = external.platform?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    external.clubId.takeIf(String::isNotBlank)?.let { clubId ->
                        ClubSearchCandidate(
                            clubId = ClubId(clubId),
                            displayName = ClubName(displayName),
                            platform = EaPlatform(platform),
                            currentDivision = external.currentDivision?.toIntOrNull(),
                        )
                    }
                }
                if (candidates.isEmpty()) ClubCatalogResult.Empty else ClubCatalogResult.Found(candidates)
            }
            EaApiResult.NoMatches -> ClubCatalogResult.Empty
            is EaApiResult.Unavailable,
            is EaApiResult.UnexpectedPayload -> ClubCatalogResult.Unavailable
        }
    }
}
