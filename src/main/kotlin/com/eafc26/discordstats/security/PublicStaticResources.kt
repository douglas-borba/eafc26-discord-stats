package com.eafc26.discordstats.security

object PublicStaticResources {
    val pathPatterns = arrayOf(
        "/*.css", "/*.js", "/*.png", "/*.jpg", "/*.jpeg", "/*.gif", "/*.svg", "/*.webp",
        "/*.ico", "/*.woff", "/*.woff2", "/*.ttf", "/favicon.ico",
        "/static/**", "/images/**", "/icons/**", "/fonts/**",
    )

    fun contains(path: String): Boolean {
        val lower = path.lowercase()
        return lower == "/favicon.ico" ||
            STATIC_DIRECTORIES.any { lower.startsWith(it) } ||
            STATIC_EXTENSIONS.any { lower.endsWith(it) }
    }

    private val STATIC_DIRECTORIES = listOf("/static/", "/images/", "/icons/", "/fonts/")
    private val STATIC_EXTENSIONS = listOf(
        ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".woff", ".woff2", ".ttf",
    )
}
