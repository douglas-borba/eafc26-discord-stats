package com.eafc26.discordstats.ea

data class BrowserFetchResult(
    val status: Int,
    val contentType: String?,
    val body: String,
    val error: String?,
    // Caching headers for diagnostics
    val cacheControl: String? = null,
    val etag: String? = null,
    val expires: String? = null,
    val age: String? = null,
    val lastModified: String? = null,
)

interface BrowserFetcher {
    fun fetch(url: String): BrowserFetchResult
}
