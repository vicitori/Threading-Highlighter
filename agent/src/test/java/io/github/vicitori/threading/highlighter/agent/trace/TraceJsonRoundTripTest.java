package io.github.vicitori.threading.highlighter.agent.trace;

import io.github.vicitori.threading.highlighter.common.trace.TraceJson;
import io.github.vicitori.threading.highlighter.common.trace.TraceRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encodes a {@link TraceRecord} and reads it back with the shared {@link TraceJson}
 * codec.
 *
 * <p>The agent (write) and the plugin (read) call the same encode/decode, so this
 * one test checks the whole format for both sides.
 */
class TraceJsonRoundTripTest {

    private static TraceRecord roundTrip(TraceRecord original) {
        return TraceJson.decode(TraceJson.encode(original));
    }

    private static TraceRecord record(String className, String methodName, String fileName) {
        return new TraceRecord(className, methodName, fileName, 42, 12345L);
    }

    @Test
    void roundTripPreservesAllFields() {
        TraceRecord original = record("com.example.Foo", "bar", "Foo.java");

        assertEquals(original, roundTrip(original));
    }

    @Test
    void nullFileNameSurvivesRoundTrip() {
        assertNull(roundTrip(record("com.example.Foo", "bar", null)).getFileName());
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        TraceRecord original = record("com.ex\\ample.\"Foo\"", "ba\\r", "F\"oo.java");

        assertEquals(original, roundTrip(original));
    }

    @Test
    void whitespaceControlCharactersSurviveRoundTrip() {
        TraceRecord original = record("a\tb\nc\rd", "m\be\ft", "file.java");

        assertEquals(original, roundTrip(original));
    }

    @Test
    void lowControlCharactersProduceValidJson() {
        // U+0000-U+001F (e.g. NUL, US) must be escaped as \\uXXXX to stay valid JSON
        TraceRecord original = record("a\u0000b\u001fc", "m\u0007", "file.java");

        String jsonLine = TraceJson.encode(original);

        assertTrue(jsonLine.contains("\\u0000"), jsonLine);
        assertTrue(jsonLine.contains("\\u001f"), jsonLine);
        assertEquals(original, TraceJson.decode(jsonLine));
    }
}
