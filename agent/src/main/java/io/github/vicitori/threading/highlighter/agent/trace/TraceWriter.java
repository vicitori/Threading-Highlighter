package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.AgentConfig;
import io.github.vicitori.threading.highlighter.agent.common.TraceRecord;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Collects trace records in memory and writes them to JSONL files.
 *
 * <p>Records are grouped by marker. The same code location (key
 * {@code class#method@line}) is kept only once, with its latest timestamp. A daemon
 * thread writes the buffer to disk on a timer, and a shutdown hook writes the rest
 * when the JVM stops. All access to the buffer goes through one lock.
 */
public final class TraceWriter {
    private static final String FLUSH_INTERVAL_PROPERTY = "threading.highlighter.flush.interval.minutes";
    private static final long DEFAULT_FLUSH_INTERVAL_MINUTES = 15;

    private final Path tracesDir;
    private final Object lock = new Object();
    private final Map<String, Map<String, TraceRecord>> tracesByMarker = new HashMap<>();
    private final StackCapture stackCapture = new StackCapture();
    private final ScheduledExecutorService scheduler;
    private volatile boolean isShuttingDown = false;

    public TraceWriter() {
        this.tracesDir = AgentConfig.getTracesPathFromSystemProperty();

        long flushIntervalMinutes = getFlushInterval();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadingHighlighter-Periodic-Flush");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::flushAllOnce,
                flushIntervalMinutes,
                flushIntervalMinutes,
                TimeUnit.MINUTES
        );

        System.out.println("[ThreadingHighlighter] Periodic flush enabled: every " + flushIntervalMinutes + " minutes");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isShuttingDown = true;
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            flushAllOnce();
        }, "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn) {
        long timestamp = System.currentTimeMillis();
        List<StackTraceElement> frames = stackCapture.capture();

        synchronized (lock) {
            Map<String, TraceRecord> traces = tracesByMarker.computeIfAbsent(markerFqn, k -> new LinkedHashMap<>());
            for (StackTraceElement element : frames) {
                TraceRecord record = TraceRecordBuilder.fromStackTraceElement(element, timestamp);
                String key = record.getKey();

                TraceRecord existing = traces.get(key);
                if (existing != null) {
                    // same location seen again: keep one record, only update the timestamp
                    traces.put(key, new TraceRecord(
                            existing.className(),
                            existing.methodName(),
                            existing.fileName(),
                            existing.lineNumber(),
                            timestamp
                    ));
                } else {
                    traces.put(key, record);
                }
            }
        }
    }

    private void flushAllOnce() {
        Map<String, Map<String, TraceRecord>> snapshot;
        synchronized (lock) {
            if (tracesByMarker.isEmpty()) {
                return;
            }
            // drain: detach the buffer under the lock so new record() calls
            // write into fresh inner maps while we do slow I/O outside the lock
            snapshot = new HashMap<>(tracesByMarker);
            tracesByMarker.clear();
        }

        int flushedRecords = 0;
        Map<String, Map<String, TraceRecord>> failed = null;
        for (Map.Entry<String, Map<String, TraceRecord>> entry : snapshot.entrySet()) {
            String markerFqn = entry.getKey();
            Map<String, TraceRecord> traces = entry.getValue();
            if (appendTracesToFile(markerFqn, traces)) {
                flushedRecords += traces.size();
            } else {
                // restore: keep failed data for the next flush instead of losing it
                if (failed == null) {
                    failed = new HashMap<>();
                }
                failed.put(markerFqn, traces);
            }
        }

        if (failed != null) {
            restoreFailed(failed);
        }

        if (!isShuttingDown && flushedRecords > 0) {
            System.out.println("[ThreadingHighlighter] Flushed " + flushedRecords + " trace records for " + snapshot.size() + " markers");
        }
    }

    // Returns failed writes to the buffer for a later retry, merging per key (newest timestamp wins).
    private void restoreFailed(Map<String, Map<String, TraceRecord>> failed) {
        synchronized (lock) {
            for (Map.Entry<String, Map<String, TraceRecord>> entry : failed.entrySet()) {
                tracesByMarker.merge(entry.getKey(), entry.getValue(), (live, restored) -> {
                    restored.forEach((key, restoredRecord) -> live.merge(key, restoredRecord,
                            (liveRecord, oldRecord) ->
                                    liveRecord.lastSeenTimestampEpochMillis() >= oldRecord.lastSeenTimestampEpochMillis()
                                            ? liveRecord : oldRecord));
                    return live;
                });
            }
        }
    }

    private long getFlushInterval() {
        try {
            String property = System.getProperty(FLUSH_INTERVAL_PROPERTY);
            if (property != null) {
                long interval = Long.parseLong(property);
                if (interval > 0) {
                    return interval;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("[ThreadingHighlighter] Invalid flush interval property: " + System.getProperty(FLUSH_INTERVAL_PROPERTY));
        }
        return DEFAULT_FLUSH_INTERVAL_MINUTES;
    }

    // Returns false if the write fails, so the caller can keep the data for a retry.
    private boolean appendTracesToFile(String markerFqn, Map<String, TraceRecord> traces) {
        try {
            String safeFileName = AgentConfig.getTraceFileName(markerFqn);
            Path markerFilePath = tracesDir.resolve(safeFileName);
            Files.createDirectories(tracesDir);

            try (BufferedWriter out = Files.newBufferedWriter(markerFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (TraceRecord record : traces.values()) {
                    out.write(TraceRecordBuilder.toJsonLine(record));
                    out.newLine();
                }
            }
            return true;
        } catch (Throwable e) {
            System.err.println("[TraceWriter] ERROR writing marker " + markerFqn + " to file (data retained for retry):");
            e.printStackTrace(System.err);
            return false;
        }
    }
}
