package io.github.vicitori.threading.highlighter.agent.trace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import io.github.vicitori.threading.highlighter.common.trace.TraceJson;
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the buffering logic of {@link TraceWriter} that has no live-JVM dependency:
 * deduplication, the per-marker capacity limit, restore-on-failure merging, throttle,
 * fresh-session cleanup and the flush-to-disk step.
 *
 * <p>The tests use the package-private test constructor (no scheduler, no shutdown
 * hook) and feed frames and timestamps explicitly through {@link
 * TraceWriter#recordFrames}, so no stack capture or wall clock is involved and the
 * assertions stay deterministic.
 */
class TraceWriterTest {

    private static final String CLASS = "com.example.App";
    private static final String METHOD = "m";
    private static final String FILE = "App.java";

    @TempDir
    Path tracesDir;

    // A stack frame at a given line; different lines are different code locations.
    private static StackTraceElement frame(int line) {
        return new StackTraceElement(CLASS, METHOD, FILE, line);
    }

    private static String keyOfLine(int line) {
        return CLASS + "#" + METHOD + "@" + line;
    }

    private static TraceRecord recordAtLine(int line, long timestamp) {
        return new TraceRecord(CLASS, METHOD, FILE, line, timestamp);
    }

    private TraceWriter newWriter() {
        return new TraceWriter(tracesDir, true); // append mode: never touch the temp dir at construction
    }

    // --- Deduplication ------------------------------------------------------

    @Test
    void sameLocationAcrossCallsIsStoredOnceWithNewestTimestamp() {
        TraceWriter writer = newWriter();

        writer.recordFrames("EDT", List.of(frame(5)), 100L);
        writer.recordFrames("EDT", List.of(frame(5)), 250L);

        Map<String, TraceRecord> buffer = writer.snapshotBuffer("EDT");
        assertEquals(1, buffer.size(), "same location must be stored once");
        assertEquals(250L, buffer.get(keyOfLine(5)).getLastSeenTimestampEpochMillis(), "newest timestamp must win");
    }

    @Test
    void duplicateFramesWithinOneCaptureCollapseToOneEntry() {
        TraceWriter writer = newWriter();

        writer.recordFrames("EDT", List.of(frame(5), frame(5), frame(5)), 100L);

        assertEquals(1, writer.snapshotBuffer("EDT").size());
    }

    @Test
    void differentMarkersDoNotShareBuffers() {
        TraceWriter writer = newWriter();

        writer.recordFrames("EDT", List.of(frame(1)), 100L);
        writer.recordFrames("WRITE", List.of(frame(1)), 100L);

        assertEquals(1, writer.snapshotBuffer("EDT").size());
        assertEquals(1, writer.snapshotBuffer("WRITE").size());
    }

    // --- Capacity limit -----------------------------------------------------

    @Test
    void bufferNeverExceedsPerMarkerLimit() {
        TraceWriter writer = newWriter();
        int overCap = TraceWriter.MAX_LOCATIONS_PER_MARKER + 10;

        List<StackTraceElement> frames = new ArrayList<>(overCap);
        for (int line = 0; line < overCap; line++) {
            frames.add(frame(line));
        }
        writer.recordFrames("EDT", frames, 1L);

        assertEquals(
                TraceWriter.MAX_LOCATIONS_PER_MARKER,
                writer.snapshotBuffer("EDT").size(),
                "buffer must be capped at the per-marker limit");
    }

    @Test
    void atCapacityNewLocationIsDroppedButExistingOneStillUpdates() {
        TraceWriter writer = newWriter();
        int cap = TraceWriter.MAX_LOCATIONS_PER_MARKER;

        List<StackTraceElement> firstBatch = new ArrayList<>(cap);
        for (int line = 0; line < cap; line++) {
            firstBatch.add(frame(line));
        }
        writer.recordFrames("EDT", firstBatch, 1L);

        // a brand-new location at capacity is skipped
        writer.recordFrames("EDT", List.of(frame(cap + 1)), 2L);
        assertEquals(cap, writer.snapshotBuffer("EDT").size());
        assertFalse(writer.snapshotBuffer("EDT").containsKey(keyOfLine(cap + 1)));

        // an already-known location is still updated at capacity
        writer.recordFrames("EDT", List.of(frame(0)), 999L);
        Map<String, TraceRecord> buffer = writer.snapshotBuffer("EDT");
        assertEquals(cap, buffer.size());
        assertEquals(999L, buffer.get(keyOfLine(0)).getLastSeenTimestampEpochMillis());
    }

    // --- restoreFailed: merge rule and trim ---------------------------------

    @Test
    void restoreKeepsNewerRecordRegardlessOfSide() {
        TraceWriter writer = newWriter();

        // live buffer already holds a newer record for the shared key
        Map<String, TraceRecord> live = new LinkedHashMap<>();
        live.put(keyOfLine(1), recordAtLine(1, 200L));

        // drained (failed write) holds an older copy of the same key plus a unique key
        Map<String, TraceRecord> drained = new LinkedHashMap<>();
        drained.put(keyOfLine(1), recordAtLine(1, 100L));
        drained.put(keyOfLine(2), recordAtLine(2, 50L));

        writer.restoreFailed(live, drained);

        assertEquals(2, live.size(), "unique failed record must be merged back");
        assertEquals(200L, live.get(keyOfLine(1)).getLastSeenTimestampEpochMillis(), "live newer copy must survive");
        assertEquals(50L, live.get(keyOfLine(2)).getLastSeenTimestampEpochMillis());
    }

    @Test
    void restoreBringsBackNewerFailedRecord() {
        TraceWriter writer = newWriter();

        Map<String, TraceRecord> live = new LinkedHashMap<>();
        live.put(keyOfLine(1), recordAtLine(1, 100L));

        Map<String, TraceRecord> drained = new LinkedHashMap<>();
        drained.put(keyOfLine(1), recordAtLine(1, 300L));

        writer.restoreFailed(live, drained);

        assertEquals(300L, live.get(keyOfLine(1)).getLastSeenTimestampEpochMillis(), "newer failed record must win");
    }

    @Test
    void restoreTrimsOldestWhenMergePushesOverLimit() {
        TraceWriter writer = newWriter();
        int cap = TraceWriter.MAX_LOCATIONS_PER_MARKER;

        // live buffer is exactly full; line number encodes age via timestamp
        Map<String, TraceRecord> live = new LinkedHashMap<>();
        for (int line = 0; line < cap; line++) {
            live.put(keyOfLine(line), recordAtLine(line, 1000L + line)); // oldest = line 0
        }

        // three newer unique records arrive from a failed write
        Map<String, TraceRecord> drained = new LinkedHashMap<>();
        for (int line = cap; line < cap + 3; line++) {
            drained.put(keyOfLine(line), recordAtLine(line, 9000L));
        }

        writer.restoreFailed(live, drained);

        assertEquals(cap, live.size(), "size must return to the cap after trimming");
        assertFalse(live.containsKey(keyOfLine(0)), "oldest entries must be dropped");
        assertFalse(live.containsKey(keyOfLine(1)));
        assertFalse(live.containsKey(keyOfLine(2)));
        assertTrue(live.containsKey(keyOfLine(cap)), "newer restored entries must be kept");
    }

    // --- Throttle -----------------------------------------------------------

    @Test
    void throttleDisabledByDefaultKeepsEveryCapture() {
        withMinCaptureInterval(null, () -> {
            TraceWriter writer = newWriter();
            assertFalse(writer.throttled("EDT", 1L));
            assertFalse(writer.throttled("EDT", 1L), "no throttle means never skipped");
        });
    }

    @Test
    void throttleSkipsCapturesWithinIntervalPerMarker() {
        withMinCaptureInterval("100", () -> {
            TraceWriter writer = newWriter();

            assertFalse(writer.throttled("EDT", 1000L), "first capture is always kept");
            assertTrue(writer.throttled("EDT", 1050L), "within the interval it is skipped");
            assertFalse(writer.throttled("EDT", 1200L), "after the interval it is kept again");
            assertFalse(writer.throttled("WRITE", 1050L), "throttle is tracked per marker");
        });
    }

    // --- Fresh session vs append -------------------------------------------

    @Test
    void freshSessionRemovesPreviousTraceFiles() throws IOException {
        Path old = Files.writeString(tracesDir.resolve("EDT.jsonl"), "stale");

        new TraceWriter(tracesDir, false);

        assertFalse(Files.exists(old), "fresh session must delete previous *.jsonl files");
    }

    @Test
    void appendSessionKeepsPreviousTraceFiles() throws IOException {
        Path old = Files.writeString(tracesDir.resolve("EDT.jsonl"), "stale");

        new TraceWriter(tracesDir, true);

        assertTrue(Files.exists(old), "append session must keep previous traces");
    }

    @Test
    void freshSessionOnlyTouchesJsonlFiles() throws IOException {
        Path jsonl = Files.writeString(tracesDir.resolve("EDT.jsonl"), "stale");
        Path other = Files.writeString(tracesDir.resolve("notes.txt"), "keep me");

        new TraceWriter(tracesDir, false);

        assertFalse(Files.exists(jsonl));
        assertTrue(Files.exists(other), "non-trace files must not be removed");
    }

    @Test
    void freshSessionOnMissingDirectoryDoesNotThrow() {
        assertDoesNotThrow(() -> new TraceWriter(tracesDir.resolve("does-not-exist"), false));
    }

    // --- Flush to disk ------------------------------------------------------

    @Test
    void flushWritesBufferedRecordsAndClearsBuffer() throws IOException {
        TraceWriter writer = newWriter();
        String marker = "com.intellij.openapi.application.impl.ApplicationImpl#assertIsDispatchThread";
        writer.recordFrames(marker, List.of(frame(7)), 100L);

        writer.flushAllOnce();

        Path file = tracesDir.resolve(ThreadingHighlighterConfig.getTraceFileName(marker));
        assertTrue(Files.exists(file), "flush must create the marker's trace file");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertEquals(keyOfLine(7), TraceJson.decode(lines.get(0)).getKey());
        assertTrue(writer.snapshotBuffer(marker).isEmpty(), "buffer must be drained after a successful flush");
    }

    @Test
    void flushAppendsAcrossInvocations() throws IOException {
        TraceWriter writer = newWriter();
        String marker = "EDT";

        writer.recordFrames(marker, List.of(frame(1)), 100L);
        writer.flushAllOnce();
        writer.recordFrames(marker, List.of(frame(2)), 200L);
        writer.flushAllOnce();

        Path file = tracesDir.resolve(ThreadingHighlighterConfig.getTraceFileName(marker));
        assertEquals(2, Files.readAllLines(file, StandardCharsets.UTF_8).size(), "each flush appends to the file");
    }

    // --- helpers ------------------------------------------------------------

    // Runs the action with the min-capture-interval system property set (or cleared
    // when value is null), restoring the previous value afterwards. The property is
    // read in the TraceWriter constructor, so it must be set before newWriter().
    private static void withMinCaptureInterval(String value, Runnable action) {
        String property = "threading.highlighter.min.capture.interval.millis";
        String previous = System.getProperty(property);
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
