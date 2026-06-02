package io.github.vicitori.threading.highlighter.agent.common;

/**
 * A single stack-trace frame captured by the agent.
 * JSON field names must match the plugin's deserialization contract.
 */
public record TraceRecord(
        String className,
        String methodName,
        String fileName,
        int lineNumber,
        long lastSeenTimestampEpochMillis
) {
    public String getKey() {
        return className + "#" + methodName + "@" + lineNumber;
    }
}
