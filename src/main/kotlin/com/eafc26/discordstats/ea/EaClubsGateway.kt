package com.eafc26.discordstats.ea

import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse

interface EaClubsGateway {
    fun searchClubs(clubName: String): EaApiResult<List<ClubSearchResult>>
    fun getLatestMatches(clubId: String): EaApiResult<List<MatchResponse>>
}
