package com.eafc26.discordstats.ea

/**
 * Recovers text that EA's backend emits with a mojibake encoding bug:
 * strings are stored as UTF-8 but passed through a Latin-1/Windows-1252
 * layer before being written to JSON, so each multi-byte UTF-8 sequence
 * becomes a pair of Latin-1 characters.
 *
 * Detection: a Latin-1 high byte (U+00C0-U+00FF) immediately followed by
 * a Latin-1 continuation-range byte (U+0080-U+00BF) is the reliable
 * signature of UTF-8 bytes misread as Latin-1. A correctly encoded Unicode
 * string never produces this pair naturally.
 *
 * Only call on user-visible name fields -- never on IDs, numbers, or URLs.
 */

// U+00C0-U+00FF: Latin-1 representations of UTF-8 lead bytes 0xC0-0xFF
// U+0080-U+00BF: Latin-1 representations of UTF-8 continuation bytes 0x80-0xBF
private val MOJIBAKE = Regex("[\u00C0-\u00FF][\u0080-\u00BF]")

// U+FFFD is the Unicode replacement character. Its presence in the decoded
// output means the original bytes were not valid UTF-8 -- not mojibake.
private const val REPLACEMENT_CHAR = '\uFFFD'

fun normalizeEaText(raw: String): String {
    if (!MOJIBAKE.containsMatchIn(raw)) return raw
    return try {
        val decoded = String(raw.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        if (REPLACEMENT_CHAR in decoded) raw else decoded
    } catch (_: Exception) {
        raw
    }
}
