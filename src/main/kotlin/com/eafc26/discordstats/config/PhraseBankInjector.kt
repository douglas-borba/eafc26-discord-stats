package com.eafc26.discordstats.config

import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Injects PhraseBank into objects that still use global state.
 *
 * Note: [MatchSummaryBuilder] is now a Spring component and receives
 * PhraseBank via constructor injection. Only [DiscordEmbedBuilder] still
 * requires this injector (due to its object-based design).
 */
@Component
class PhraseBankInjector(private val phraseBank: PhraseBank) {

    @PostConstruct
    fun inject() {
        DiscordEmbedBuilder.phraseBank = phraseBank
    }
}
