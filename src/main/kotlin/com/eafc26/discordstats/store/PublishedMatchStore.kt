package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.match.ClubId
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Persists Discord publication state in an independent namespace per official EA [ClubId].
 *
 * Each club is stored at `clubs/{clubId}/published-matches.json`. Records retain the
 * existing WAL state machine and JSON schema; the directory supplies the club side of
 * the persistent `(ClubId, MatchId)` identity.
 *
 * The Associação BF legacy file is imported idempotently on its first access. The
 * original file is never deleted or overwritten. If the scoped target already exists,
 * its records win and only missing legacy MatchIds are imported.
 */
@Component
class PublishedMatchStore(private val objectMapper: ObjectMapper) : PublicationStateStore {
    private val log = LoggerFactory.getLogger(javaClass)
    private val storeLock = ReentrantLock()
    private val initializedClubs = mutableSetOf<ClubId>()

    override fun loadRecords(clubId: ClubId): Map<String, PublicationRecord> = storeLock.withLock {
        ensureInitialized(clubId)
        loadRecordsInternal(pathFor(clubId))
    }

    override fun find(clubId: ClubId, matchId: String): PublicationRecord? = loadRecords(clubId)[matchId]

    override fun saveRecord(clubId: ClubId, record: PublicationRecord) = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val updated = loadRecordsInternal(path).toMutableMap()
        updated[record.matchId] = record
        saveAllRecordsAtomic(path, updated.values.toList())
        log.debug("Saved publication record: clubId={}, matchId={}, state={}", clubId.value, record.matchId, record.state)
    }

    override fun createRecordIfAbsent(clubId: ClubId, record: PublicationRecord): Boolean = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val current = loadRecordsInternal(path).toMutableMap()
        if (current.containsKey(record.matchId)) return@withLock false
        current[record.matchId] = record
        saveAllRecordsAtomic(path, current.values.toList())
        true
    }

    override fun claimForAutomaticDelivery(
        clubId: ClubId,
        expected: PublicationRecord,
        attemptedAt: Instant,
    ): PublicationRecord? = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val current = loadRecordsInternal(path).toMutableMap()
        val actual = current[expected.matchId] ?: return@withLock null
        if (
            actual.state != expected.state ||
            actual.attemptCount != expected.attemptCount ||
            actual.baselineReason != expected.baselineReason ||
            !actual.isAutomaticClaimable()
        ) return@withLock null

        val claimed = actual.copy(
            state = PublicationState.DELIVERING,
            attemptCount = actual.attemptCount + 1,
            lastAttemptAt = attemptedAt.epochSecond,
            updatedAt = attemptedAt.epochSecond,
        )
        current[claimed.matchId] = claimed
        saveAllRecordsAtomic(path, current.values.toList())
        claimed
    }

    override fun removeRecord(clubId: ClubId, matchId: String) = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val current = loadRecordsInternal(path).toMutableMap()
        if (current.remove(matchId) != null) {
            saveAllRecordsAtomic(path, current.values.toList())
            log.debug("Removed publication record: clubId={}, matchId={}", clubId.value, matchId)
        }
    }

    override fun resolveAsDelivered(clubId: ClubId, matchId: String) = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val current = loadRecordsInternal(path).toMutableMap()
        val existing = current[matchId] ?: return@withLock
        if (existing.state == PublicationState.DELIVERED) return@withLock
        current[matchId] = existing.copy(state = PublicationState.DELIVERED, updatedAt = Instant.now().epochSecond)
        saveAllRecordsAtomic(path, current.values.toList())
        log.info("Publication resolved as DELIVERED: clubId={}, matchId={}", clubId.value, matchId)
    }

    override fun resolveAsUndelivered(clubId: ClubId, matchId: String) = removeRecord(clubId, matchId)

    override fun loadIds(clubId: ClubId): Set<String> = loadRecords(clubId).keys

    override fun saveIds(clubId: ClubId, ids: Set<String>) = storeLock.withLock {
        ensureInitialized(clubId)
        val path = pathFor(clubId)
        val records = loadRecordsInternal(path).toMutableMap()
        ids.sorted().forEach { matchId ->
            records.putIfAbsent(matchId, PublicationRecord(matchId, PublicationState.BASELINED, baselineReason = BaselineReason.FIRST_RUN))
        }
        saveAllRecordsAtomic(path, records.values.toList())
        log.debug("Publication baseline saved: clubId={}, count={}", clubId.value, ids.size)
    }

    override fun metadata(clubId: ClubId): PublicationStoreMetadata {
        val records = loadRecords(clubId).values
        return PublicationStoreMetadata(
            clubId = clubId,
            recordCount = records.size,
            latestUpdatedAt = records.maxOfOrNull { it.updatedAt }?.let(Instant::ofEpochSecond),
            states = records.groupingBy { it.state }.eachCount(),
        )
    }

    private fun ensureInitialized(clubId: ClubId) {
        if (!initializedClubs.add(clubId)) return
        try {
            migrateLegacyDefaultClubStore(clubId)
            migrateV1Format(pathFor(clubId))
            upgradeDeliveringRecords(clubId)
            log.info("Publication store ready: clubId={}, configuredPath=true", clubId.value)
        } catch (ex: Exception) {
            initializedClubs.remove(clubId)
            throw ex
        }
    }

    private fun migrateLegacyDefaultClubStore(clubId: ClubId) {
        if (clubId != LEGACY_ASSOCIATION_BF) return
        val legacy = AppDataPaths.storeFile
        if (!legacy.exists()) return

        val target = pathFor(clubId)
        try {
            val legacyRecords = parseRecords(legacy.readText())
            val scopedRecords = if (target.exists()) parseRecords(target.readText()) else emptyMap()
            val merged = legacyRecords + scopedRecords // scoped/newer namespace wins on collision
            if (!target.exists() || merged != scopedRecords) {
                saveAllRecordsAtomic(target, merged.values.toList())
            }
            log.info(
                "Legacy publication store imported: clubId={}, imported={}, scopedTotal={}",
                clubId.value,
                legacyRecords.keys.count { it !in scopedRecords },
                merged.size,
            )
        } catch (ex: Exception) {
            initializedClubs.remove(clubId)
            throw IllegalStateException("Cannot migrate legacy publication store for clubId=${clubId.value}", ex)
        }
    }

    private fun migrateV1Format(path: Path) {
        if (!path.exists()) return
        val tree = try {
            objectMapper.readTree(path.readText())
        } catch (ex: JsonProcessingException) {
            throw IllegalStateException("Publication store contains malformed JSON", ex)
        }
        if (!tree.isArray || tree.isEmpty || !tree[0].isTextual) return
        val backup = path.resolveSibling(path.fileName.toString() + ".v1.bak")
        if (!backup.exists()) Files.copy(path, backup)
        saveAllRecordsAtomic(path, parseRecords(path.readText()).values.toList())
    }

    private fun upgradeDeliveringRecords(clubId: ClubId) {
        val path = pathFor(clubId)
        if (!path.exists()) return
        val records = loadRecordsInternal(path)
        if (records.values.none { it.state == PublicationState.DELIVERING }) return
        val upgraded = records.mapValues { (_, record) ->
            if (record.state == PublicationState.DELIVERING) {
                record.copy(
                    state = PublicationState.DELIVERY_UNCERTAIN,
                    updatedAt = Instant.now().epochSecond,
                    lastError = record.lastError ?: DeliveryUncertaintyReason.STARTUP_RECOVERY.diagnosticMessage(
                        "Registro DELIVERING encontrado na inicialização; a causa original não está disponível.",
                    ),
                )
            } else record
        }
        saveAllRecordsAtomic(path, upgraded.values.toList())
        log.warn("Interrupted publications marked DELIVERY_UNCERTAIN: clubId={}", clubId.value)
    }

    private fun loadRecordsInternal(path: Path): Map<String, PublicationRecord> {
        if (!path.exists()) return emptyMap()
        val json = try {
            path.readText()
        } catch (ex: Exception) {
            throw IllegalStateException("Cannot read publication store", ex)
        }
        return try {
            parseRecords(json)
        } catch (ex: JsonProcessingException) {
            throw IllegalStateException("Publication store contains malformed JSON", ex)
        }
    }

    private fun parseRecords(json: String): Map<String, PublicationRecord> {
        val tree = objectMapper.readTree(json)
        require(tree.isArray) { "Store JSON root must be an array" }
        if (tree.isEmpty) return emptyMap()
        return if (tree[0].isTextual) {
            objectMapper.readValue<List<String>>(json)
                .map { PublicationRecord(it, PublicationState.DELIVERED) }
                .associateBy { it.matchId }
        } else {
            objectMapper.readValue<List<PublicationRecord>>(json).associateBy { it.matchId }
        }
    }

    private fun saveAllRecordsAtomic(path: Path, records: List<PublicationRecord>) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        tmp.toFile().writeText(objectMapper.writeValueAsString(records.sortedBy { it.matchId }))
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun pathFor(clubId: ClubId): Path = AppDataPaths.publicationStoreFile(clubId)

    companion object {
        val LEGACY_ASSOCIATION_BF = ClubId("1104972")
    }
}

private fun PublicationRecord.isAutomaticClaimable(): Boolean =
    state == PublicationState.PENDING ||
        state == PublicationState.FAILED_TRANSIENT ||
        state == PublicationState.BASELINED && baselineReason == BaselineReason.NO_DESTINATION

data class PublicationStoreMetadata(
    val clubId: ClubId,
    val recordCount: Int,
    val latestUpdatedAt: Instant?,
    val states: Map<PublicationState, Int>,
)
