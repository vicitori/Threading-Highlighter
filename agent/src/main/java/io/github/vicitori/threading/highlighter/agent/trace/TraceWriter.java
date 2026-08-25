package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.AgentLog;
import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;
import io.github.vicitori.threading.highlighter.common.trace.TraceJson;
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Collects trace records in memory and writes them to JSONL files.
 *
 * <p>Records are grouped by marker. The same code location (key
 * {@code class#method@line}) is kept only once, with its latest timestamp. A daemon
 * thread writes the buffer to disk on a timer, and a shutdown hook writes the rest
 * when the JVM stops.
 *
 * <p>The instrumented assertions fire from many host threads thousands of times per
 * second, so the buffer avoids a single global lock: markers live in a
 * {@link ConcurrentHashMap}, and only the small per-marker map is synchronized. This
 * keeps the observer from serializing the very threading behavior it measures.
 */
public final class TraceWriter {
    private static final String FLUSH_INTERVAL_PROPERTY = "threading.highlighter.flush.interval.minutes";
    private static final long DEFAULT_FLUSH_INTERVAL_MINUTES = 15;

    // Optional throttle: the smallest gap between full stack captures for one marker.
    // 0 (default) means no throttle, so all data is kept; a bigger value skips some
    // captures to make the hot path lighter when a marker fires very often.
    private static final String MIN_CAPTURE_INTERVAL_PROPERTY = "threading.highlighter.min.capture.interval.millis";
    private static final long DEFAULT_MIN_CAPTURE_INTERVAL_MILLIS = 0;

    // Each JVM run is a fresh session by default: old trace files are removed at
    // startup so the plugin only sees the current code's markers. Set this property
    // to true to instead keep and append to traces from earlier runs (useful for
    // collecting a rare event over several runs, as long as the code does not change).
    private static final String APPEND_SESSION_PROPERTY = "threading.highlighter.append.session";
    private static final String TRACE_FILE_GLOB = "*.jsonl";

    // cap unique locations per marker so a permanently failing disk (records kept via
    // restoreFailed) cannot grow the buffer without bound; oldest entries are dropped
    private static final int MAX_LOCATIONS_PER_MARKER = 10_000;

    private final Path tracesDir;
    private final Map<String, Map<String, TraceRecord>> tracesByMarker = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCaptureByMarker = new ConcurrentHashMap<>();
    private final long minCaptureIntervalMillis = getMinCaptureIntervalMillis();
    private final StackCapture stackCapture = new StackCapture();
    private final ScheduledExecutorService scheduler;
    private volatile boolean isShuttingDown = false;

    public TraceWriter() {
        this.tracesDir = ThreadingHighlighterConfig.getTracesPathFromSystemProperty();

        // fresh session by default: drop traces from previous runs so stale line
        // numbers (after the code was edited) cannot leave gutter icons on wrong lines
        if (!isAppendSession()) {
            clearPreviousSession();
        }

        long flushIntervalMinutes = getFlushInterval();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadingHighlighter-Periodic-Flush");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::flushAllOnce, flushIntervalMinutes, flushIntervalMinutes, TimeUnit.MINUTES);

        AgentLog.info("Periodic flush enabled: every " + flushIntervalMinutes + " minutes");

        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
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
                        },
                        "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn) {
        long timestamp = System.currentTimeMillis();
        if (throttled(markerFqn, timestamp)) {
            return;
        }
        // capture() is the expensive part, so it runs only after the throttle check
        List<StackTraceElement> frames = stackCapture.capture();

        Map<String, TraceRecord> traces = tracesByMarker.computeIfAbsent(markerFqn, k -> new LinkedHashMap<>());
        // lock only this marker's map, not the whole buffer, so different markers and
        // the flush thread do not contend on a single global lock
        synchronized (traces) {
            for (StackTraceElement element : frames) {
                TraceRecord record = StackFrameMapper.toRecord(element, timestamp);
                String key = record.getKey();

                TraceRecord existing = traces.get(key);
                if (existing != null) {
                    // same location seen again: keep one record, only update the timestamp
                    traces.put(
                            key,
                            new TraceRecord(
                                    existing.getClassName(),
                                    existing.getMethodName(),
                                    existing.getFileName(),
                                    existing.getLineNumber(),
                                    timestamp));
                } else if (traces.size() < MAX_LOCATIONS_PER_MARKER) {
                    traces.put(key, record);
                }
                // else: at capacity and this is a new location, skip it (see field doc)
            }
        }
    }

    private void flushAllOnce() {
        int flushedRecords = 0;
        int flushedMarkers = 0;
        for (Map.Entry<String, Map<String, TraceRecord>> entry : tracesByMarker.entrySet()) {
            String markerFqn = entry.getKey();
            Map<String, TraceRecord> markerBuffer = entry.getValue();

            // drain this marker under its own lock, so record() keeps writing into
            // the same map while the slow I/O below runs without holding any lock
            Map<String, TraceRecord> drained;
            synchronized (markerBuffer) {
                if (markerBuffer.isEmpty()) {
                    continue;
                }
                drained = new LinkedHashMap<>(markerBuffer);
                markerBuffer.clear();
            }

            if (appendTracesToFile(markerFqn, drained)) {
                flushedRecords += drained.size();
                flushedMarkers++;
            } else {
                // restore: keep failed data for the next flush instead of losing it
                restoreFailed(markerBuffer, drained);
            }
        }

        if (!isShuttingDown && flushedRecords > 0) {
            AgentLog.debug("Flushed " + flushedRecords + " trace records for " + flushedMarkers + " markers");
        }
    }

    // Returns failed writes to the marker buffer for a later retry, merging per key
    // (newest timestamp wins) so records added during the failed I/O are not lost.
    private void restoreFailed(Map<String, TraceRecord> markerBuffer, Map<String, TraceRecord> drained) {
        synchronized (markerBuffer) {
            drained.forEach((key, oldRecord) -> markerBuffer.merge(
                    key,
                    oldRecord,
                    (liveRecord, restoredRecord) -> liveRecord.getLastSeenTimestampEpochMillis()
                                    >= restoredRecord.getLastSeenTimestampEpochMillis()
                            ? liveRecord
                            : restoredRecord));
            trimToLimit(markerBuffer);
        }
    }

    // Restoring can push a marker over the cap; drop the oldest locations by timestamp.
    private void trimToLimit(Map<String, TraceRecord> markerBuffer) {
        int overflow = markerBuffer.size() - MAX_LOCATIONS_PER_MARKER;
        if (overflow <= 0) {
            return;
        }
        markerBuffer.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getLastSeenTimestampEpochMillis()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(markerBuffer::remove);
        AgentLog.warn("Trace buffer for a marker exceeded " + MAX_LOCATIONS_PER_MARKER + " locations; dropped "
                + overflow + " oldest (disk write failing?)");
    }

    // Returns true if this marker was captured too recently and should be skipped.
    private boolean throttled(String markerFqn, long now) {
        if (minCaptureIntervalMillis <= 0) {
            return false;
        }
        Long last = lastCaptureByMarker.get(markerFqn);
        if (last != null && now - last < minCaptureIntervalMillis) {
            return true;
        }
        lastCaptureByMarker.put(markerFqn, now);
        return false;
    }

    private long getMinCaptureIntervalMillis() {
        try {
            String property = System.getProperty(MIN_CAPTURE_INTERVAL_PROPERTY);
            if (property != null) {
                long interval = Long.parseLong(property.trim());
                if (interval >= 0) {
                    return interval;
                }
            }
        } catch (NumberFormatException e) {
            AgentLog.warn(
                    "Invalid min capture interval property: " + System.getProperty(MIN_CAPTURE_INTERVAL_PROPERTY));
        }
        return DEFAULT_MIN_CAPTURE_INTERVAL_MILLIS;
    }

    // true when the user opted in to keep traces from previous runs (append mode).
    private boolean isAppendSession() {
        return Boolean.getBoolean(APPEND_SESSION_PROPERTY);
    }

    // Removes trace files left by earlier runs so only the current session remains.
    // Best-effort: any failure here is logged but must never crash the host JVM.
    private void clearPreviousSession() {
        try {
            if (!Files.isDirectory(tracesDir)) {
                return; // nothing written yet
            }
            int removed = 0;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(tracesDir, TRACE_FILE_GLOB)) {
                for (Path file : files) {
                    try {
                        if (Files.deleteIfExists(file)) {
                            removed++;
                        }
                    } catch (IOException e) {
                        AgentLog.warn("Could not delete old trace file: " + file);
                    }
                }
            }
            if (removed > 0) {
                AgentLog.info("Fresh session: removed " + removed + " trace file(s) from a previous run");
            }
        } catch (Throwable t) {
            // never let cleanup break agent startup
            AgentLog.warn("Failed to clear previous session traces: " + t.getMessage());
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
            AgentLog.warn("Invalid flush interval property: " + System.getProperty(FLUSH_INTERVAL_PROPERTY));
        }
        return DEFAULT_FLUSH_INTERVAL_MINUTES;
    }

    // Returns false if the write fails, so the caller can keep the data for a retry.
    private boolean appendTracesToFile(String markerFqn, Map<String, TraceRecord> traces) {
        try {
            String safeFileName = ThreadingHighlighterConfig.getTraceFileName(markerFqn);
            Path markerFilePath = tracesDir.resolve(safeFileName);
            Files.createDirectories(tracesDir);

            try (BufferedWriter out = Files.newBufferedWriter(
                    markerFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (TraceRecord record : traces.values()) {
                    out.write(TraceJson.encode(record));
                    out.newLine();
                }
            }
            return true;
        } catch (Throwable e) {
            AgentLog.error("Error writing marker " + markerFqn + " to file (data retained for retry)", e);
            return false;
        }
    }
}
