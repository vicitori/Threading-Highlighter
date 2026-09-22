package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.trace.TraceJson
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Reading side of the trace pipeline: [TraceRepository] turns a JSONL file into
 * deduplicated, user-code-only records. Pure file I/O logic, no IntelliJ runtime.
 *
 * These tests mirror on the reader the invariants the agent guarantees on the writer
 * (dedup with newest timestamp, user-code filtering) and add reader-only concerns:
 * missing files, and blank or corrupted lines that must be skipped rather than fail
 * the whole load.
 */
class TraceRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val repository = TraceRepository()

    private fun record(className: String, line: Int, ts: Long): TraceRecord = TraceRecord(className, "m", "App.java", line, ts)

    private fun writeLines(vararg lines: String): Path {
        val file = dir.resolve("EDT.jsonl")
        file.writeText(lines.joinToString("\n"))
        return file
    }

    @Test
    fun missingFileYieldsEmptyList() {
        val records = repository.readTraceFile(dir.resolve("absent.jsonl"), emptyList())
        assertTrue(records.isEmpty())
    }

    @Test
    fun readsWellFormedRecords() {
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 1, 100L)),
            TraceJson.encode(record("com.example.App", 2, 100L)),
        )

        val records = repository.readTraceFile(file, listOf("com.example"))

        assertEquals(setOf("com.example.App#m@1", "com.example.App#m@2"), records.map { it.key }.toSet())
    }

    @Test
    fun blankLinesAreSkipped() {
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 1, 100L)),
            "",
            "   ",
            TraceJson.encode(record("com.example.App", 2, 100L)),
        )

        val records = repository.readTraceFile(file, listOf("com.example"))

        assertEquals(2, records.size)
    }

    @Test
    fun corruptedLinesAreSkippedNotFatal() {
        // one broken line must not discard the whole file's valid records
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 1, 100L)),
            "{this is not valid json",
            "garbage",
            TraceJson.encode(record("com.example.App", 2, 100L)),
        )

        val records = repository.readTraceFile(file, listOf("com.example"))

        assertEquals(setOf("com.example.App#m@1", "com.example.App#m@2"), records.map { it.key }.toSet())
    }

    @Test
    fun sameLocationDeduplicatedKeepingNewestTimestamp() {
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 5, 100L)),
            TraceJson.encode(record("com.example.App", 5, 300L)),
            TraceJson.encode(record("com.example.App", 5, 200L)),
        )

        val records = repository.readTraceFile(file, listOf("com.example"))

        assertEquals(1, records.size)
        assertEquals(300L, records.single().lastSeenTimestampEpochMillis, "newest timestamp must win")
    }

    @Test
    fun nonUserCodeFramesAreFilteredOut() {
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 1, 100L)),
            TraceJson.encode(record("com.intellij.openapi.Foo", 2, 100L)),
            TraceJson.encode(record("kotlinx.coroutines.BuildersKt", 3, 100L)),
        )

        val records = repository.readTraceFile(file, listOf("com.example"))

        assertEquals(setOf("com.example.App#m@1"), records.map { it.key }.toSet())
    }

    @Test
    fun emptyUserPackagesKeepsEverything() {
        // no packages detected: show all, hide nothing (mirrors UserCodeFilter contract)
        val file = writeLines(
            TraceJson.encode(record("com.example.App", 1, 100L)),
            TraceJson.encode(record("com.intellij.openapi.Foo", 2, 100L)),
        )

        val records = repository.readTraceFile(file, emptyList())

        assertEquals(2, records.size)
    }
}
