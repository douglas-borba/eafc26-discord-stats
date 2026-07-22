package com.eafc26.discordstats.ea

import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats

interface EaClubsGateway {
    fun searchClubs(clubName: String): EaApiResult<List<ClubSearchResult>>
    fun getLatestMatches(clubId: String): EaApiResult<List<MatchResponse>>

    /**
     * Fetches Virtual Pro names for all club members.
     *
     * Returns a list of [MemberStats] associating each player's platform gamertag
     * with their in-game Virtual Pro name.
     *
     * Default implementation returns an empty success so existing implementations
     * are not required to override. Callers fall back to [PlayerEntry.playerName]
     * when the map is empty.
     */
    fun getMembersStats(clubId: String): EaApiResult<List<MemberStats>> =
        EaApiResult.Success(emptyList())
}
