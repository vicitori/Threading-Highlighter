package io.github.vicitori.threading.highlighter.agent.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import io.github.vicitori.threading.highlighter.common.trace.TraceJson;
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stress tests the per-marker locking of {@link TraceWriter}: the key claim of the
 * design is that concurrent {@code record} calls (and a concurrent flush) neither
 * lose nor corrupt code locations. Both tests drive the same fixed set of {@code K}
 * distinct locations from many threads and assert the deduplicated result is exactly
 * that set.
 */
class TraceWriterConcurrencyTest {

    private static final String CLASS = "com.example.App";
    private static final String METHOD = "m";
    private static final String FILE = "App.java";
    private static final String MARKER = "EDT";

    private static final int THREADS = 8;
    private static final int DISTINCT_LOCATIONS = 500;
    private static final int REPEATS_PER_THREAD = 50;

    @TempDir
    Path tracesDir;

    private static List<StackTraceElement> distinctFrames() {
        List<StackTraceElement> frames = new ArrayList<>(DISTINCT_LOCATIONS);
        for (int line = 0; line < DISTINCT_LOCATIONS; line++) {
            frames.add(new StackTraceElement(CLASS, METHOD, FILE, line));
        }
        return frames;
    }

    private static Set<String> expectedKeys() {
        Set<String> keys = new HashSet<>();
        for (int line = 0; line < DISTINCT_LOCATIONS; line++) {
            keys.add(CLASS + "#" + METHOD + "@" + line);
        }
        return keys;
    }

    @Test
    void concurrentRecordsDeduplicateWithoutLoss() throws InterruptedException {
        TraceWriter writer = new TraceWriter(tracesDir, true);
        List<StackTraceElement> frames = distinctFrames();

        runOnManyThreads(startAtSameTime -> {
            for (int r = 0; r < REPEATS_PER_THREAD; r++) {
                writer.recordFrames(MARKER, frames, System.currentTimeMillis());
            }
        });

        Set<String> keys = writer.snapshotBuffer(MARKER).keySet();
        assertEquals(
                expectedKeys(),
                new HashSet<>(keys),
                "concurrent records must yield exactly the distinct locations, deduplicated");
    }

    @Test
    void concurrentRecordAndFlushLoseNoLocation() throws Exception {
        TraceWriter writer = new TraceWriter(tracesDir, true);
        List<StackTraceElement> frames = distinctFrames();

        AtomicBoolean recordingDone = new AtomicBoolean(false);
        Thread flusher = new Thread(() -> {
            while (!recordingDone.get()) {
                writer.flushAllOnce();
            }
        });
        flusher.start();

        runOnManyThreads(ignored -> {
            for (int r = 0; r < REPEATS_PER_THREAD; r++) {
                writer.recordFrames(MARKER, frames, System.currentTimeMillis());
            }
        });

        recordingDone.set(true);
        flusher.join(TimeUnit.SECONDS.toMillis(10));
        writer.flushAllOnce(); // drain whatever remained in the buffer

        Set<String> keysOnDisk = readKeysFromDisk();
        assertEquals(
                expectedKeys(), keysOnDisk, "no location may be lost or corrupted across concurrent record and flush");
        assertTrue(writer.snapshotBuffer(MARKER).isEmpty(), "buffer must be empty after the final flush");
    }

    private Set<String> readKeysFromDisk() throws Exception {
        Path file = tracesDir.resolve(ThreadingHighlighterConfig.getTraceFileName(MARKER));
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            TraceRecord record = TraceJson.decode(line); // throws if a line is corrupted
            keys.add(record.getKey());
        }
        return keys;
    }

    // Starts THREADS workers, releases them together for maximum contention, and
    // waits for all of them to finish.
    private static void runOnManyThreads(java.util.function.Consumer<Void> work) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        List<Thread> threads = new ArrayList<>(THREADS);
        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(() -> {
                try {
                    startGate.await();
                    work.accept(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        startGate.countDown();
        assertTrue(doneGate.await(30, TimeUnit.SECONDS), "worker threads must finish in time");
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
