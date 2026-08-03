package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppDataPaths
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Persists per-MatchId publication records as a JSON array in Application Support.
 *
 * Path: ~/Library/Application Support/EAFC26DiscordStats/published-matches.json
 *
 * ## Store format (v2)
 * ```json
 * [
 *   {"matchId":"abc","state":"DELIVERED","updatedAt":1722700000},
 *   {"matchId":"xyz","state":"DELIVERY_UNCERTAIN","updatedAt":1722699000}
 * ]
 * ```
 *
 * ## Backward compatibility (v1 → v2 migration)
 * The legacy format was a plain string array: `["id1","id2"]`.
 * On first read, all v1 IDs are migrated to `DELIVERED` records.
 * The original file is backed up as `published-matches.json.v1.bak`.
 *
 * ## Startup upgrade: DELIVERING → DELIVERY_UNCERTAIN
 * Any record left in `DELIVERING` state after a restart indicates the process was
 * killed between the pre-send write and the 2xx confirmation. These are upgraded to
 * `DELIVERY_UNCERTAIN` during [init] so automatic resend is permanently blocked.
 *
 * ## Atomicity
 * All writes are atomic: content goes to a `.tmp` sibling file first,
 * then renamed over the target. Falls back to non-atomic rename on ATOMIC_MOVE failure.
 *
 * ## Thread safety
 * All read-modify-write operations are serialised by an internal [ReentrantLock].
 */
@Component
class PublishedMatchStore(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val storeLock = ReentrantLock()
    private val storePath: Path get() = AppDataPaths.storeFile

    init {
        log.info("Published match store initialized at: {}", storePath.toAbsolutePath())
        migrateLegacyFileLocation()
        upgradeDeliveringRecords()
    }

    // -------------------------------------------------------------------------
    // Public API — record-based (WAL)
    // -------------------------------------------------------------------------

    /**
     * Loads all publication records, keyed by MatchId.
     *
     * - Missing file → empty map (first installation).
     * - Legacy v1 format → migrated to v2 automatically with backup.
     * - Malformed JSON → [IllegalStateException]; caller decides how to handle.
     */
    fun loadRecords(): Map<String, PublicationRecord> = storeLock.withLock {
        loadRecordsInternal()
    }

    /**
     * Persists or replaces a single record for the given MatchId.
     * Thread-safe; uses an internal store-level lock.
     */
    fun saveRecord(record: PublicationRecord) = storeLock.withLock {
        val updated = loadRecordsInternal().toMutableMap()
        updated[record.matchId] = record
        saveAllRecordsAtomic(updated.values.toList())
        log.debug("Saved record: matchId={}, state={}", record.matchId, record.state)
    }

    /**
     * Removes a record by MatchId.
     *
     * Used to clean up a DELIVERING marker when an HTTP call **definitively** failed
     * (i.e., an exception was thrown before or by the webhook), allowing future retry.
     */
    fun removeRecord(matchId: String) = storeLock.withLock {
        val current = loadRecordsInternal().toMutableMap()
        if (current.remove(matchId) != null) {
            saveAllRecordsAtomic(current.values.toList())
            log.debug("Removed DELIVERING marker for matchId={}", matchId)
        }
    }

    /**
     * Administratively resolves a DELIVERY_UNCERTAIN record as DELIVERED.
     * No-op if the record does not exist or is already DELIVERED.
     */
    fun resolveAsDelivered(matchId: String) = storeLock.withLock {
        val current = loadRecordsInternal().toMutableMap()
        val existing = current[matchId] ?: return@withLock
        if (existing.state == PublicationState.DELIVERED) return@withLock
        current[matchId] = existing.copy(
            state = PublicationState.DELIVERED,
            updatedAt = java.time.Instant.now().epochSecond,
        )
        saveAllRecordsAtomic(current.values.toList())
        log.info("Administratively resolved matchId={} as DELIVERED", matchId)
    }

    /**
     * Administratively removes a DELIVERY_UNCERTAIN record, allowing future automatic
     * resend if the match has not actually been delivered.
     * No-op if the record does not exist.
     */
    fun resolveAsUndelivered(matchId: String) = storeLock.withLock {
        val current = loadRecordsInternal().toMutableMap()
        if (current.remove(matchId) != null) {
            saveAllRecordsAtomic(current.values.toList())
            log.info("Administratively removed record for matchId={} — next cycle may resend", matchId)
        }
    }

    // -------------------------------------------------------------------------
    // Backward-compatible API (used by MatchAcquisitionService)
    // -------------------------------------------------------------------------

    /**
     * Returns the set of all known MatchIds regardless of state
     * (DELIVERED and DELIVERY_UNCERTAIN).
     *
     * Used for:
     * - First-run detection: empty = fresh installation.
     * - Pre-filtering in [processAllNew]: matches in this set are not passed to
     *   [DiscordMatchPublicationService] (avoiding redundant calls for uncertain matches).
     */
    fun loadIds(): Set<String> = loadRecords().keys

    /**
     * Saves all [ids] as DELIVERED, replacing the entire store contents.
     *
     * Used exclusively by [MatchAcquisitionService.establishBaseline] to mark the
     * initial EA-window as "already seen" without publishing them to Discord.
     */
    fun saveIds(ids: Set<String>) = storeLock.withLock {
        val records = ids.sorted().map { PublicationRecord(it, PublicationState.DELIVERED) }
        saveAllRecordsAtomic(records)
        log.debug("Baseline: saved {} IDs as DELIVERED (full replacement)", ids.size)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Reads records WITHOUT acquiring [storeLock]. Must be called from within the lock.
     */
    private fun loadRecordsInternal(): Map<String, PublicationRecord> {
        val path = storePath
        if (!path.exists()) return emptyMap()
        val json = try {
            path.readText()
        } catch (ex: Exception) {
            throw IllegalStateException("Cannot read published match store at $path", ex)
        }
        return try {
            parseRecords(json)
        } catch (ex: JsonProcessingException) {
            throw IllegalStateException("Published match store at $path contains malformed JSON", ex)
        }
    }

    /**
     * Parses store JSON, auto-migrating v1 format (string array) to v2 (record array).
     *
     * Called from within [storeLock].
     */
    private fun parseRecords(json: String): Map<String, PublicationRecord> {
        val tree = objectMapper.readTree(json)
        if (!tree.isArray) throw IllegalStateException("Store JSON root must be an array")
        if (tree.isEmpty) return emptyMap()

        return if (tree[0].isTextual) {
            // v1 format: ["id1", "id2", ...]
            val ids = objectMapper.readValue<List<String>>(json)
            log.info("Detected legacy store format (v1) with {} IDs — migrating to v2", ids.size)

            // Backup before migration — never overwrite an existing backup
            val backupPath = storePath.resolveSibling(storePath.fileName.toString() + ".v1.bak")
            if (!backupPath.exists()) {
                Files.copy(storePath, backupPath)
                log.info("Legacy store backed up to {}", backupPath.toAbsolutePath())
            } else {
                log.debug("Legacy backup already exists at {} — not overwriting original", backupPath.toAbsolutePath())
            }

            val records = ids.map { PublicationRecord(it, PublicationState.DELIVERED) }
            saveAllRecordsAtomic(records)
            log.info("Migration complete: {} IDs → DELIVERED records", ids.size)
            records.associateBy { it.matchId }
        } else {
            // v2 format: [{"matchId":..., "state":..., "updatedAt":...}]
            objectMapper.readValue<List<PublicationRecord>>(json).associateBy { it.matchId }
        }
    }

    /**
     * Writes [records] atomically via a `.tmp` sibling file.
     * Called from within [storeLock].
     */
    private fun saveAllRecordsAtomic(records: List<PublicationRecord>) {
        val path = storePath
        Files.createDirectories(path.parent)
        val json = objectMapper.writeValueAsString(records.sortedBy { it.matchId })
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        tmp.toFile().writeText(json)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
        log.debug("Atomically saved {} publication records", records.size)
    }

    /**
     * On startup: upgrades any DELIVERING records to DELIVERY_UNCERTAIN.
     *
     * A DELIVERING record after restart means the process was killed in the delivery
     * window (between the pre-send write and the 2xx confirmation). The delivery
     * outcome is unknown, so automatic resend is permanently blocked.
     */
    private fun upgradeDeliveringRecords() = storeLock.withLock {
        if (!storePath.exists()) return@withLock
        val records = try {
            loadRecordsInternal()
        } catch (ex: Exception) {
            log.warn("Could not read store during startup upgrade: {}", ex.message)
            return@withLock
        }
        val delivering = records.values.filter { it.state == PublicationState.DELIVERING }
        if (delivering.isEmpty()) return@withLock

        log.warn(
            "{} match(es) found in DELIVERING state after restart — upgrading to DELIVERY_UNCERTAIN. " +
                "Automatic resend is blocked. Administrative resolution required for: {}",
            delivering.size,
            delivering.map { it.matchId },
        )
        val upgraded = records.mapValues { (_, r) ->
            if (r.state == PublicationState.DELIVERING)
                r.copy(state = PublicationState.DELIVERY_UNCERTAIN, updatedAt = java.time.Instant.now().epochSecond)
            else r
        }
        saveAllRecordsAtomic(upgraded.values.toList())
    }

    /**
     * Migrates the legacy file location (`./data/published-matches.json` → Application Support).
     */
    private fun migrateLegacyFileLocation() {
        val legacy = Path.of("data", "published-matches.json")
        val dest = storePath
        if (!legacy.exists() || dest.exists()) return
        try {
            Files.createDirectories(dest.parent)
            Files.copy(legacy, dest)
            log.info("Migrated published match store from legacy location to Application Support")
        } catch (ex: Exception) {
            log.warn("Could not migrate legacy match store: {}", ex.message)
        }
    }
}
