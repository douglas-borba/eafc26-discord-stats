package com.eafc26.discordstats.domain.story

import com.eafc26.discordstats.domain.match.MatchId

data class MatchStories(
    val matchId: MatchId,
    val stories: List<Story>,
) {
    init {
        require(stories.count { it.type == StoryType.MATCH_OUTCOME } == 1) {
            "MatchStories must contain exactly one outcome story"
        }
    }
}
