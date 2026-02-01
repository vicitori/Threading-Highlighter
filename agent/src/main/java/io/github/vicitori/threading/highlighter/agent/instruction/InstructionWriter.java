package io.github.vicitori.threading.highlighter.agent.instruction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InstructionWriter {
    private static final String INSTRUCTIONS_DIR_NAME = ".ij-threading-highlighter";
    private static final String INSTRUCTIONS_DIR_PROPERTY = "threading.highlighter.instructions.dir";

    private final Path instructionsDir;

    public Path getInstructionsDir() {
        return this.instructionsDir;
    }

    private final Object lock = new Object();
    private final Map<String, Map<String, InstructionRecord>> instructionsByMarker = new HashMap<>();

    public InstructionWriter() {
        String instructionsDirBase = System.getProperty(INSTRUCTIONS_DIR_PROPERTY);
        if (instructionsDirBase == null || instructionsDirBase.trim().isEmpty()) {
            instructionsDirBase = System.getProperty("user.home");
        }

        this.instructionsDir = Path.of(instructionsDirBase).resolve(INSTRUCTIONS_DIR_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAllOnce, "ThreadingHighlighter-Shutdown"));
    }

    public void record(String markerFqn, Throwable stackTraceHolder) {
        System.err.println("[InstructionWriter] record() called with markerFqn: " + markerFqn);
        long timestamp = System.currentTimeMillis();
        StackTraceElement[] stackTrace = stackTraceHolder.getStackTrace();

        synchronized (lock) {
            Map<String, InstructionRecord> instructions = instructionsByMarker.computeIfAbsent(markerFqn, k -> new LinkedHashMap<>());

            for (StackTraceElement element : stackTrace) {
                InstructionRecord record = InstructionRecord.fromStackTraceElement(element, timestamp);
                String key = record.getKey();

                InstructionRecord existing = instructions.get(key);
                if (existing != null) existing.updateTimestamp(timestamp);
                else instructions.put(key, record);
            }

            System.err.println("[InstructionWriter] Processed " + stackTrace.length + " instructions for marker: " + markerFqn);
        }
    }

    private void flushAllOnce() {
        Map<String, Map<String, InstructionRecord>> snapshot;
        synchronized (lock) {
            if (instructionsByMarker.isEmpty()) {
                System.err.println("[InstructionWriter] No instructions to flush");
                return;
            }
            snapshot = new HashMap<>(instructionsByMarker);
            instructionsByMarker.clear();
        }

        System.err.println("[InstructionWriter] Flushing instructions for " + snapshot.size() + " markers");
        for (Map.Entry<String, Map<String, InstructionRecord>> entry : snapshot.entrySet()) {
            String markerFqn = entry.getKey();
            Map<String, InstructionRecord> instructions = entry.getValue();
            writeMarkerToFile(markerFqn, instructions);
        }
    }

    private void writeMarkerToFile(String markerFqn, Map<String, InstructionRecord> newInstructions) {
        try {
            String safeFileName = markerFqn.replace('#', '_').replace('$', '_') + ".jsonl";
            Path markerFilePath = instructionsDir.resolve(safeFileName);

            System.err.println("[InstructionWriter] Creating directories: " + instructionsDir);
            Files.createDirectories(instructionsDir);

            Map<String, InstructionRecord> existingInstructions = readInstructionsFromFile(markerFilePath);

            for (Map.Entry<String, InstructionRecord> entry : newInstructions.entrySet()) {
                String key = entry.getKey();
                InstructionRecord newRecord = entry.getValue();

                InstructionRecord existing = existingInstructions.get(key);
                if (existing != null) {
                    existing.updateTimestamp(newRecord.getLastSeenTimestampMillis());
                } else {
                    existingInstructions.put(key, newRecord);
                }
            }

            System.err.println("[InstructionWriter] Writing " + existingInstructions.size() + " instructions to file: " + markerFilePath);
            try (BufferedWriter out = Files.newBufferedWriter(markerFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (InstructionRecord record : existingInstructions.values()) {
                    out.write(record.toJsonLine());
                    out.newLine();
                }
            }
            System.err.println("[InstructionWriter] Successfully wrote instructions for marker: " + markerFqn);
        } catch (Throwable e) {
            System.err.println("[InstructionWriter] ERROR writing marker " + markerFqn + " to file:");
            e.printStackTrace(System.err);
        }
    }

    private Map<String, InstructionRecord> readInstructionsFromFile(Path filePath) {
        Map<String, InstructionRecord> instructions = new LinkedHashMap<>();

        if (!Files.exists(filePath)) {
            return instructions;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                InstructionRecord record = parseInstructionFromJson(line);
                if (record != null) {
                    instructions.put(record.getKey(), record);
                }
            }
        } catch (Throwable e) {
            System.err.println("[InstructionWriter] ERROR reading instructions from file: " + filePath);
            e.printStackTrace(System.err);
        }

        return instructions;
    }

    private InstructionRecord parseInstructionFromJson(String json) {
        try {
            String className = extractJsonString(json, "className");
            String methodName = extractJsonString(json, "methodName");
            String fileName = extractJsonString(json, "fileName");
            int lineNumber = extractJsonInt(json);
            long timestamp = extractJsonLong(json);

            return new InstructionRecord(className, methodName, fileName, lineNumber, timestamp);
        } catch (Exception e) {
            System.err.println("[InstructionWriter] ERROR parsing instruction JSON: " + json);
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
