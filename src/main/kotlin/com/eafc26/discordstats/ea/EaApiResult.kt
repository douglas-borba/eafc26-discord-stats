package com.eafc26.discordstats.ea

/**
 * Explicit result type for every EA API call.
 * Distinguishes outcomes so callers never need to interpret null or catch exceptions.
 */
sealed class EaApiResult<out T> {
    /** The endpoint responded with valid, parseable data. */
    data class Success<T>(val data: T) : EaApiResult<T>()

    /** The endpoint responded successfully but returned an empty collection. */
    data object NoMatches : EaApiResult<Nothing>()

    /**
     * The endpoint returned a non-2xx HTTP status.
     * Temporary (503, 403, timeout) or permanent — the caller decides how to react.
     */
    data class Unavailable(val statusCode: Int, val message: String) : EaApiResult<Nothing>()

    /**
     * The response was received and had a 2xx status but could not be parsed
     * into the expected DTO shape. Likely an undocumented schema change.
     */
    data class UnexpectedPayload(val cause: Throwable) : EaApiResult<Nothing>()
}
