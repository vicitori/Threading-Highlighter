package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.agent.common.AgentConfig;
import io.github.vicitori.threading.highlighter.agent.common.AgentLog;
import io.github.vicitori.threading.highlighter.agent.common.TraceRecord;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    private final Path tracesDir;
    private final Map<String, Map<String, TraceRecord>> tracesByMarker = new ConcurrentHashMap<>();
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

        AgentLog.info("Periodic flush enabled: every " + flushIntervalMinutes + " minutes");

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
            drained.forEach((key, oldRecord) -> markerBuffer.merge(key, oldRecord,
                    (liveRecord, restoredRecord) ->
                            liveRecord.lastSeenTimestampEpochMillis() >= restoredRecord.lastSeenTimestampEpochMillis()
                                    ? liveRecord : restoredRecord));
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
            String safeFileName = AgentConfig.getTraceFileName(markerFqn);
            Path markerFilePath = tracesDir.resolve(safeFileName);
            Files.createDirectories(tracesDir);

            try (BufferedWriter out = Files.newBufferedWriter(markerFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (TraceRecord record : traces.values()) {
                    out.write(TraceLineCodec.encode(record));
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
