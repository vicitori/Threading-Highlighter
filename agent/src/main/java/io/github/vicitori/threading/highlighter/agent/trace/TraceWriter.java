package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.common.config.ThreadingHighlighterConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraceWriter {
    private final Path tracesDir;
    private final Object lock = new Object();
    private final Map<String, Map<String, TraceRecord>> tracesByMarker = new HashMap<>();

    public TraceWriter() {
        this.tracesDir = ThreadingHighlighterConfig.getTracesPath();
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAllOnce, "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn, Throwable stackTraceHolder) {
        long timestamp = System.currentTimeMillis();
        StackTraceElement[] stackTrace = stackTraceHolder.getStackTrace();

        synchronized (lock) {
            Map<String, TraceRecord> traces = tracesByMarker.computeIfAbsent(markerFqn, k -> new LinkedHashMap<>());
            for (StackTraceElement element : stackTrace) {
                TraceRecord record = TraceRecord.fromStackTraceElement(element, timestamp);
                String key = record.getKey();

                TraceRecord existing = traces.get(key);
                if (existing != null) existing.updateTimestamp(timestamp);
                else traces.put(key, record);
            }
        }
    }

    private void flushAllOnce() {
        Map<String, Map<String, TraceRecord>> snapshot;
        synchronized (lock) {
            if (tracesByMarker.isEmpty()) {
                return;
            }
            snapshot = new HashMap<>(tracesByMarker);
            tracesByMarker.clear();
        }

        for (Map.Entry<String, Map<String, TraceRecord>> entry : snapshot.entrySet()) {
            String markerFqn = entry.getKey();
            Map<String, TraceRecord> traces = entry.getValue();
            writeMarkerToFile(markerFqn, traces);
        }
    }

    private void writeMarkerToFile(String markerFqn, Map<String, TraceRecord> newTraces) {
        try {
            String safeFileName = markerFqn.replaceAll("[^a-zA-Z0-9._-]", "_") + ".jsonl";
            Path markerFilePath = tracesDir.resolve(safeFileName);
            Files.createDirectories(tracesDir);

            Map<String, TraceRecord> existingTraces = readTracesFromFile(markerFilePath);
            for (Map.Entry<String, TraceRecord> entry : newTraces.entrySet()) {
                String key = entry.getKey();
                TraceRecord newRecord = entry.getValue();

                TraceRecord existing = existingTraces.get(key);
                if (existing != null) {
                    existing.updateTimestamp(newRecord.getLastSeenTimestampMillis());
                } else {
                    existingTraces.put(key, newRecord);
                }
            }

            try (BufferedWriter out = Files.newBufferedWriter(markerFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (TraceRecord record : existingTraces.values()) {
                    out.write(record.toJsonLine());
                    out.newLine();
                }
            }
        } catch (Throwable e) {
            System.err.println("[TraceWriter] ERROR writing marker " + markerFqn + " to file:");
            e.printStackTrace(System.err);
        }
    }

    private Map<String, TraceRecord> readTracesFromFile(Path filePath) {
        Map<String, TraceRecord> traces = new LinkedHashMap<>();

        if (!Files.exists(filePath)) {
            return traces;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                TraceRecord record = parseTraceFromJson(line);
                if (record != null) {
                    traces.put(record.getKey(), record);
                }
            }
        } catch (Throwable e) {
            System.err.println("[TraceWriter] ERROR reading traces from file: " + filePath);
            e.printStackTrace(System.err);
        }

        return traces;
    }

    private TraceRecord parseTraceFromJson(String json) {
        try {
            String className = extractJsonString(json, "className");
            String methodName = extractJsonString(json, "methodName");
            String fileName = extractJsonString(json, "fileName");
            int lineNumber = extractJsonInt(json);
            long timestamp = extractJsonLong(json);

            return new TraceRecord(className, methodName, fileName, lineNumber, timestamp);
        } catch (Exception e) {
            System.err.println("[TraceWriter] ERROR parsing trace JSON: " + json);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        start += pattern.length();

        if (json.substring(start).trim().startsWith("null")) {
            return null;
        }

        start = json.indexOf('"', start);
        if (start == -1) {
            return null;
        }
        start++;

        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && (end == start || json.charAt(end - 1) != '\\')) {
                break;
            }
            end++;
        }
        return json.substring(start, end);
    }

    private int extractJsonInt(String json) {
        String pattern = "\"" + "lineNumber" + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return -1;
        }
        start += pattern.length();

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end).trim());
    }

    private long extractJsonLong(String json) {
        String pattern = "\"" + "lastSeenTimestampEpochMillis" + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return -1;
        }
        start += pattern.length();

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end).trim());
    }

}
