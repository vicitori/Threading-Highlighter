package io.github.vicitori.threading.highlighter.common.trace;

import java.util.Objects;

/**
 * One stack-trace frame. The agent (writer) and the plugin (reader) share it.
 *
 * <p>Plain Java, so both sides use the same class. JSON reading and writing is in
 * {@link TraceJson}. It has normal getters, so Kotlin code can use property syntax
 * ({@code record.className}).
 */
public final class TraceRecord {
    private final String className;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;
    private final long lastSeenTimestampEpochMillis;

    public TraceRecord(
            String className, String methodName, String fileName, int lineNumber, long lastSeenTimestampEpochMillis) {
        this.className = className;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.lastSeenTimestampEpochMillis = lastSeenTimestampEpochMillis;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    /** May be {@code null} when the JVM did not record a source file for the frame. */
    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public long getLastSeenTimestampEpochMillis() {
        return lastSeenTimestampEpochMillis;
    }

    /** Identity of a code location; the same location is stored once, newest timestamp kept. */
    public String getKey() {
        return className + "#" + methodName + "@" + lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TraceRecord other)) return false;
        return lineNumber == other.lineNumber
                && lastSeenTimestampEpochMillis == other.lastSeenTimestampEpochMillis
                && Objects.equals(className, other.className)
                && Objects.equals(methodName, other.methodName)
                && Objects.equals(fileName, other.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, fileName, lineNumber, lastSeenTimestampEpochMillis);
    }

    @Override
    public String toString() {
        return "TraceRecord{" + getKey() + ", ts=" + lastSeenTimestampEpochMillis + "}";
    }
}
