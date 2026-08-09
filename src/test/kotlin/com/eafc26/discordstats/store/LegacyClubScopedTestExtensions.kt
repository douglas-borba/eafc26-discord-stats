package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId

private val LEGACY_TEST_CLUB = ClubId("1104972")

internal fun PublishedMatchStore.loadRecords() = loadRecords(LEGACY_TEST_CLUB)
internal fun PublishedMatchStore.loadIds() = loadIds(LEGACY_TEST_CLUB)
internal fun PublishedMatchStore.saveRecord(record: PublicationRecord) = saveRecord(LEGACY_TEST_CLUB, record)
internal fun PublishedMatchStore.saveIds(ids: Set<String>) = saveIds(LEGACY_TEST_CLUB, ids)
internal fun PublishedMatchStore.removeRecord(matchId: String) = removeRecord(LEGACY_TEST_CLUB, matchId)
internal fun PublishedMatchStore.resolveAsDelivered(matchId: String) = resolveAsDelivered(LEGACY_TEST_CLUB, matchId)
internal fun PublishedMatchStore.resolveAsUndelivered(matchId: String) = resolveAsUndelivered(LEGACY_TEST_CLUB, matchId)
