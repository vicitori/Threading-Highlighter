package io.github.vicitori.threading.highlighter.agent.trace;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TraceWriter {
    private static final String TRACE_DIR_NAME = ".ij-threading-highlighter";
    private static final String TRACE_DIR_PROPERTY = "threading.highlighter.trace.dir";

    private final Path traceDir;

    public Path getTraceDir() {
        return traceDir;
    }

    private final Object lock = new Object();
    private final Map<String, List<TraceEvent>> eventsByMarker = new HashMap<>();

    public TraceWriter() {
        String traceDirBase = System.getProperty(TRACE_DIR_PROPERTY);
        if (traceDirBase == null || traceDirBase.trim().isEmpty()) {
            traceDirBase = System.getProperty("user.home");
        }

        this.traceDir = Path.of(traceDirBase).resolve(TRACE_DIR_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAllOnce, "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn, Throwable stackTraceHolder) {
        System.err.println("[TraceWriter] record() called with markerFqn: " + markerFqn);
        var event = new TraceEvent(markerFqn, System.currentTimeMillis(), stackTraceHolder);
        synchronized (lock) {
            eventsByMarker.computeIfAbsent(markerFqn, k -> new ArrayList<>()).add(event);
            System.err.println("[TraceWriter] Event added for marker: " + markerFqn);
        }
    }

    private void flushAllOnce() {
        Map<String, List<TraceEvent>> snapshot;
        synchronized (lock) {
            if (eventsByMarker.isEmpty()) {
                System.err.println("[TraceWriter] No events to flush");
                return;
            }
            snapshot = new HashMap<>(eventsByMarker);
            eventsByMarker.clear();
        }
        
        System.err.println("[TraceWriter] Flushing events for " + snapshot.size() + " markers");
        for (Map.Entry<String, List<TraceEvent>> entry : snapshot.entrySet()) {
            String markerFqn = entry.getKey();
            List<TraceEvent> events = entry.getValue();
            writeMarkerToFile(markerFqn, events);
        }
    }

    private void writeMarkerToFile(String markerFqn, List<TraceEvent> events) {
        try {
            String safeFileName = markerFqn.replace('#', '_').replace('$', '_') + ".jsonl";
            Path markerFilePath = traceDir.resolve(safeFileName);
            
            System.err.println("[TraceWriter] Creating directories: " + traceDir);
            Files.createDirectories(traceDir);

            System.err.println("[TraceWriter] Writing " + events.size() + " events to file: " + markerFilePath);
            try (BufferedWriter out = Files.newBufferedWriter(markerFilePath, StandardCharsets.UTF_8, 
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                for (TraceEvent event : events) {
                    out.write(event.toJsonLine());
                    out.newLine();
                }
            }
            System.err.println("[TraceWriter] Successfully wrote " + events.size() + " events for marker: " + markerFqn);
        } catch (Throwable e) {
            System.err.println("[TraceWriter] ERROR writing marker " + markerFqn + " to file:");
            e.printStackTrace(System.err);
        }
    }
}
