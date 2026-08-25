package io.github.vicitori.threading.highlighter.plugin.services

import io.github.vicitori.threading.highlighter.common.marker.MarkerInfo
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord
import io.github.vicitori.threading.highlighter.plugin.models.MarkerTraceData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the reverse index built by [TraceSnapshot]: what gets indexed, what is
 * skipped, and that lookups are consistent. Pure logic, no IntelliJ platform runtime.
 */
class TraceSnapshotTest {
    private val marker = MarkerInfo("com.intellij.util.SlowOperations", "assertSlowOperationsAreAllowed", "Slow", "desc")

    private fun record(
        fileName: String?,
        line: Int,
        ts: Long = 1_700_000_000_000L,
    ) = TraceRecord("com.example.OrderService", "placeOrder", fileName, line, ts)

    private fun snapshotOf(vararg traces: TraceRecord): TraceSnapshot = TraceSnapshot.of(mapOf(marker.markerFqn() to MarkerTraceData(marker, traces.toList())))

    @Test
    fun emptyMapProducesEmptySnapshot() {
        val snapshot = TraceSnapshot.of(emptyMap())

        assertTrue(snapshot.isEmpty)
        assertEquals(0, snapshot.markerCount)
        assertTrue(snapshot.getRecordsForLocation("OrderService.java", 42).isEmpty())
    }

    @Test
    fun indexesRecordByFileAndLine() {
        val snapshot = snapshotOf(record("OrderService.java", 42))

        val found = snapshot.getRecordsForLocation("OrderService.java", 42)

        assertEquals(1, found.size)
        assertEquals(marker, found.first().first)
        assertFalse(snapshot.isEmpty)
        assertEquals(1, snapshot.markerCount)
    }

    @Test
    fun lookupOnUnknownLocationReturnsEmpty() {
        val snapshot = snapshotOf(record("OrderService.java", 42))

        assertTrue(snapshot.getRecordsForLocation("OrderService.java", 7).isEmpty())
        assertTrue(snapshot.getRecordsForLocation("Other.java", 42).isEmpty())
    }

    @Test
    fun skipsRecordsWithoutFileName() {
        // synthetic/native frames have no file name and cannot be placed in the gutter
        val snapshot = snapshotOf(record(fileName = null, line = 42))

        assertFalse(snapshot.isEmpty) // marker is still counted
        assertTrue(snapshot.getRecordsForLocation("OrderService.java", 42).isEmpty())
    }

    @Test
    fun skipsNonPositiveLineNumbers() {
        // StackTraceElement uses -1/-2 for unknown/native line numbers
        val snapshot = snapshotOf(record("OrderService.java", -1), record("OrderService.java", 0))

        assertTrue(snapshot.getRecordsForLocation("OrderService.java", -1).isEmpty())
        assertTrue(snapshot.getRecordsForLocation("OrderService.java", 0).isEmpty())
    }

    @Test
    fun groupsMultipleRecordsOnSameLine() {
        val snapshot =
            snapshotOf(
                TraceRecord("com.example.A", "run", "Shared.java", 10, 1L),
                TraceRecord("com.example.B", "run", "Shared.java", 10, 2L),
            )

        assertEquals(2, snapshot.getRecordsForLocation("Shared.java", 10).size)
    }

    @Test
    fun forEachLocationVisitsEveryIndexedRecord() {
        val snapshot = snapshotOf(record("OrderService.java", 42), record("OrderService.java", 43))

        var visited = 0
        snapshot.forEachLocation { _, _, _, _ -> visited++ }

        assertEquals(2, visited)
    }
}
