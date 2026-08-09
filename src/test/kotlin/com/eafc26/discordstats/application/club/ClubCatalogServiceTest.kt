package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClubCatalogServiceTest {
    private val gateway = mock<EaClubsGateway>()
    private val service = ClubCatalogService(gateway)

    @Test
    fun `search maps EA DTOs to administrative candidates`() {
        whenever(gateway.searchClubs("Associação BF")).thenReturn(
            EaApiResult.Success(
                listOf(
                    ClubSearchResult(
                        clubId = "1104972",
                        clubName = "Associação BF",
                        platform = "common-gen5",
                        currentDivision = "3",
                        wins = "99",
                    ),
                ),
            ),
        )

        val result = service.search(" Associação BF ")

        assertThat(result).isEqualTo(
            ClubCatalogResult.Found(
                listOf(
                    ClubSearchCandidate(
                        clubId = com.eafc26.discordstats.domain.match.ClubId("1104972"),
                        displayName = com.eafc26.discordstats.domain.match.ClubName("Associação BF"),
                        platform = EaPlatform("common-gen5"),
                        currentDivision = 3,
                    ),
                ),
            ),
        )
        verify(gateway).searchClubs("Associação BF")
    }

    @Test
    fun `search does not expose unavailable gateway details`() {
        whenever(gateway.searchClubs("Club")).thenReturn(EaApiResult.Unavailable(502, "internal gateway detail"))

        assertThat(service.search("Club")).isEqualTo(ClubCatalogResult.Unavailable)
    }
}
