package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;
import kotlinx.serialization.json.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the agent's hand-written JSON is read back correctly by the same
 * kotlinx.serialization model and library the plugin uses. This guards the contract
 * between the two independently-built sides.
 */
class TraceRecordBuilderTest {

    private static final Json JSON = Json.Default;

    private static TraceRecord decode(String jsonLine) {
        return JSON.decodeFromString(TraceRecord.Companion.serializer(), jsonLine);
    }

    private static TraceRecord roundTrip(TraceRecord original) {
        String jsonLine = TraceRecordBuilder.toJsonLine(toAgentRecord(original));
        return decode(jsonLine);
    }

    private static io.github.vicitori.threading.highlighter.agent.common.TraceRecord toAgentRecord(
            TraceRecord r) {
        return new io.github.vicitori.threading.highlighter.agent.common.TraceRecord(
                r.getClassName(), r.getMethodName(), r.getFileName(),
                r.getLineNumber(), r.getLastSeenTimestampEpochMillis());
    }

    private static TraceRecord record(String className, String methodName, String fileName) {
        return new TraceRecord(className, methodName, fileName, 42, 12345L);
    }

    @Test
    void roundTripPreservesAllFields() {
        TraceRecord original = record("com.example.Foo", "bar", "Foo.java");

        TraceRecord parsed = roundTrip(original);

        assertEquals(original, parsed);
    }

    @Test
    void nullFileNameSurvivesRoundTrip() {
        TraceRecord parsed = roundTrip(record("com.example.Foo", "bar", null));

        assertNull(parsed.getFileName());
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        TraceRecord original = record("com.ex\\ample.\"Foo\"", "ba\\r", "F\"oo.java");

        TraceRecord parsed = roundTrip(original);

        assertEquals(original, parsed);
    }

    @Test
    void whitespaceControlCharactersSurviveRoundTrip() {
        TraceRecord original = record("a\tb\nc\rd", "m\be\ft", "file.java");

        TraceRecord parsed = roundTrip(original);

        assertEquals(original, parsed);
    }

    @Test
    void lowControlCharactersProduceValidJson() {
        // U+0000-U+001F (e.g. NUL, US) must be escaped as \\uXXXX to stay valid JSON
        TraceRecord original = record("a\u0000b\u001fc", "m\u0007", "file.java");

        String jsonLine = TraceRecordBuilder.toJsonLine(toAgentRecord(original));

        assertTrue(jsonLine.contains("\\u0000"), jsonLine);
        assertTrue(jsonLine.contains("\\u001f"), jsonLine);
        assertEquals(original, decode(jsonLine));
    }
}
