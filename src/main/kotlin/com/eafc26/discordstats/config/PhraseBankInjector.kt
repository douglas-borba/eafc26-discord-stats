package com.eafc26.discordstats.config

import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class PhraseBankInjector(private val phraseBank: PhraseBank) {

    @PostConstruct
    fun inject() {
        DiscordEmbedBuilder.phraseBank = phraseBank
    }
}
