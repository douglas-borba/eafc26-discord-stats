package com.eafc26.discordstats.service

sealed class NotifyResult {
    data class Sent(val summary: String) : NotifyResult()
    data class SentPersistenceError(val summary: String) : NotifyResult()
    data class AlreadyPublished(val summary: String) : NotifyResult()
    object NoMatches : NotifyResult()
    object EaUnavailable : NotifyResult()
    object DiscordError : NotifyResult()
    object Busy : NotifyResult()
}
