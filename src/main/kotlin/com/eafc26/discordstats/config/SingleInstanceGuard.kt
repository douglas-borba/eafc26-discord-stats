package com.eafc26.discordstats.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Ensures only one collector instance runs at a time by holding an
 * exclusive OS-level file lock on [AppDataPaths.lockFile].
 *
 * The lock is acquired at startup via [@PostConstruct] and released
 * automatically when the JVM exits or [@PreDestroy] is called.
 *
 * A second process attempting to start while the first holds the lock
 * will fail with a clear error message and refuse to run the scheduler.
 *
 * This guard is active only when `app.polling.enabled=true` (the default)
 * so read-only CLI commands and test contexts with polling disabled are
 * unaffected.
 *
 * Cross-process safety: [java.nio.channels.FileLock] delegates to the OS,
 * giving mutual exclusion across JVM processes sharing the same data dir.
 */
@Component
@ConditionalOnProperty(
    name = ["app.polling.enabled", "app.acquisition.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SingleInstanceGuard : AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)

    private var channel: FileChannel? = null
    private var fileLock: FileLock? = null

    @PostConstruct
    fun acquire() {
        val lockPath: Path = AppDataPaths.lockFile
        Files.createDirectories(lockPath.parent)

        val ch = FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        channel = ch

        val lock = try {
            ch.tryLock()
        } catch (ex: OverlappingFileLockException) {
            ch.close()
            fail(lockPath)
        }

        if (lock == null) {
            ch.close()
            fail(lockPath)
        }

        fileLock = lock
        log.info("Collector instance lock acquired: {}", lockPath.toAbsolutePath())
    }

    @PreDestroy
    override fun close() {
        runCatching { fileLock?.release() }
        runCatching { channel?.close() }
        log.debug("Collector instance lock released")
    }

    private fun fail(lockPath: Path): Nothing {
        throw IllegalStateException(
            "Another collector instance is already running. " +
                "Only one instance may acquire matches at a time. " +
                "Lock file: ${lockPath.toAbsolutePath()}"
        )
    }
}


