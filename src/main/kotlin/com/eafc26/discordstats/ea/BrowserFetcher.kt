package com.eafc26.discordstats.ea

data class BrowserFetchResult(
    val status: Int,
    val contentType: String?,
    val body: String,
    val error: String?,
)

interface BrowserFetcher {
    fun fetch(url: String): BrowserFetchResult
}
