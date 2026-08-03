package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Tests for [SingleInstanceGuard].
 *
 * Uses a [TempDir] to avoid touching the real Application Support directory.
 * The guard is instantiated directly (not via Spring), bypassing
 * [@ConditionalOnProperty] so the locking logic is tested in isolation.
 */
class SingleInstanceGuardTest {

    @TempDir
    lateinit var tempDir: Path

    private var originalUserHome: String? = null
    private val guards = mutableListOf<SingleInstanceGuard>()

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        guards.forEach { runCatching { it.close() } }
        if (originalUserHome != null) System.setProperty("user.home", originalUserHome!!)
    }

    private fun makeGuard(): SingleInstanceGuard {
        val guard = SingleInstanceGuard()
        guard.acquire()
        guards.add(guard)
        return guard
    }

    @Test
    fun `first instance acquires lock successfully`() {
        makeGuard()
        assertThat(AppDataPaths.lockFile).exists()
    }

    @Test
    fun `second instance in same JVM is rejected with clear error`() {
        makeGuard()
        assertThatThrownBy { makeGuard() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Another collector instance is already running")
            .hasMessageContaining("collector.lock")
    }

    @Test
    fun `second process cannot acquire lock while first holds it`() {
        val lockFile = AppDataPaths.lockFile
        lockFile.parent.toFile().mkdirs()
        val externalChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val externalLock = externalChannel.lock()
        try {
            assertThatThrownBy { makeGuard() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Another collector instance is already running")
        } finally {
            externalLock.release()
            externalChannel.close()
        }
    }

    @Test
    fun `released lock allows a new instance to acquire it`() {
        val first = makeGuard()
        first.close()
        guards.removeAt(guards.indexOf(first))
        makeGuard()
        assertThat(AppDataPaths.lockFile).exists()
    }

    @Test
    fun `lock file is created in app support directory`() {
        makeGuard()
        assertThat(AppDataPaths.lockFile)
            .exists()
            .hasFileName("collector.lock")
            .hasParent(AppDataPaths.appSupportDir)
    }
}



