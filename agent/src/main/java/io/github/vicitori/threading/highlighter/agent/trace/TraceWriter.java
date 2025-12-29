package io.github.vicitori.threading.highlighter.agent.trace;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class TraceWriter {
    private static final String TRACE_DIR_NAME = ".ij-threading-highlighter";
    private static final String TRACE_FILE_NAME = "trace.jsonl";
    private static final String TRACE_DIR_PROPERTY = "threading.highlighter.trace.dir";

    private final Path tracePath;

    public Path getTracePath() {
        return tracePath;
    }

    private final Object lock = new Object();
    private final List<TraceEvent> events = new ArrayList<>();

    public TraceWriter() {
        String traceDirBase = System.getProperty(TRACE_DIR_PROPERTY);
        if (traceDirBase == null || traceDirBase.trim().isEmpty()) {
            traceDirBase = System.getProperty("user.home");
        }

        this.tracePath = Path.of(traceDirBase).resolve(TRACE_DIR_NAME).resolve(TRACE_FILE_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAllOnce, "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn, Throwable stackTraceHolder) {
        System.err.println("[TraceWriter] record() called with markerFqn: " + markerFqn);
        var event = new TraceEvent(markerFqn, System.currentTimeMillis(), stackTraceHolder);
        synchronized (lock) {
            events.add(event);
            System.err.println("[TraceWriter] Event added, total events: " + events.size());
        }
    }

    private void flushAllOnce() {
        List<TraceEvent> snapshot;
        synchronized (lock) {
            if (events.isEmpty()) {
                System.err.println("[TraceWriter] No events to flush");
                return;
            }
            snapshot = new ArrayList<>(events);
            events.clear();
        }
        System.err.println("[TraceWriter] Flushing " + snapshot.size() + " events to " + tracePath);
        writeAllToFile(snapshot);
    }

    private void writeAllToFile(List<TraceEvent> snapshot) {
        try {
            System.err.println("[TraceWriter] Creating directories: " + tracePath.getParent());
            Files.createDirectories(tracePath.getParent());

            System.err.println("[TraceWriter] Writing to file: " + tracePath);
            try (BufferedWriter out = Files.newBufferedWriter(tracePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                for (TraceEvent event : snapshot) {
                    out.write(event.toJsonLine());
                    out.newLine();
                }
            }
            System.err.println("[TraceWriter] Successfully wrote " + snapshot.size() + " events");
        } catch (Throwable e) {
            System.err.println("[TraceWriter] ERROR writing to file:");
            e.printStackTrace(System.err);
        }
    }
}
