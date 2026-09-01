package io.github.vicitori.threading.highlighter.common.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Adversarial-input tests for the hand-written {@link TraceJson#decode} parser.
 *
 * <p>The existing round-trip test only feeds decode the output of encode, so it
 * cannot catch parser bugs on inputs the encoder never produces: truncated or
 * malformed lines, missing required fields, unknown fields (the format claims
 * forward compatibility), and edge cases of strings and numbers. These tests target
 * exactly those.
 */
class TraceJsonDecodeTest {

    private static String line(String className, String methodName, String fileName, int lineNumber, long ts) {
        String file = fileName == null ? "null" : "\"" + fileName + "\"";
        return "{\"className\":\"" + className + "\",\"methodName\":\"" + methodName + "\",\"fileName\":" + file
                + ",\"lineNumber\":" + lineNumber + ",\"lastSeenTimestampEpochMillis\":" + ts + "}";
    }

    // --- Well-formed baselines ---------------------------------------------

    @Test
    void decodesWellFormedLine() {
        TraceRecord record = TraceJson.decode(line("com.example.Foo", "bar", "Foo.java", 42, 12345L));

        assertEquals("com.example.Foo", record.getClassName());
        assertEquals("bar", record.getMethodName());
        assertEquals("Foo.java", record.getFileName());
        assertEquals(42, record.getLineNumber());
        assertEquals(12345L, record.getLastSeenTimestampEpochMillis());
    }

    @Test
    void decodesExplicitNullFileName() {
        assertNull(TraceJson.decode(line("com.example.Foo", "bar", null, 1, 1L)).getFileName());
    }

    @Test
    void toleratesWhitespaceBetweenTokens() {
        String spaced = "{ \"className\" : \"C\" , \"methodName\" : \"m\" , \"fileName\" : null ,"
                + " \"lineNumber\" : 7 , \"lastSeenTimestampEpochMillis\" : 9 }";

        assertEquals("C#m@7", TraceJson.decode(spaced).getKey());
    }

    // --- Forward compatibility: unknown fields must be ignored -------------

    @Test
    void ignoresUnknownStringNumberAndNullFields() {
        String withExtra = "{\"className\":\"C\",\"methodName\":\"m\",\"fileName\":\"F.java\","
                + "\"lineNumber\":3,\"lastSeenTimestampEpochMillis\":8,"
                + "\"newStringField\":\"x\",\"newNumberField\":123,\"newNullField\":null}";

        TraceRecord record = TraceJson.decode(withExtra);

        assertEquals("C#m@3", record.getKey());
        assertEquals(8L, record.getLastSeenTimestampEpochMillis());
    }

    // --- Missing required fields -------------------------------------------

    @Test
    void rejectsMissingClassName() {
        String missing = "{\"methodName\":\"m\",\"fileName\":\"F.java\","
                + "\"lineNumber\":3,\"lastSeenTimestampEpochMillis\":8}";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(missing));
    }

    @Test
    void rejectsMissingLineNumber() {
        String missing = "{\"className\":\"C\",\"methodName\":\"m\",\"fileName\":\"F.java\","
                + "\"lastSeenTimestampEpochMillis\":8}";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(missing));
    }

    @Test
    void rejectsMissingTimestamp() {
        String missing = "{\"className\":\"C\",\"methodName\":\"m\",\"fileName\":\"F.java\",\"lineNumber\":3}";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(missing));
    }

    // --- Malformed / truncated input ---------------------------------------

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(""));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode("not json at all"));
    }

    @Test
    void rejectsMissingOpeningBrace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TraceJson.decode("\"className\":\"C\",\"methodName\":\"m\",\"lineNumber\":1,"
                        + "\"lastSeenTimestampEpochMillis\":1}"));
    }

    @Test
    void rejectsTruncatedBeforeClosingBrace() {
        String truncated = "{\"className\":\"C\",\"methodName\":\"m\",\"fileName\":\"F.java\",\"lineNumber\":3";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(truncated));
    }

    @Test
    void rejectsUnterminatedString() {
        String unterminated = "{\"className\":\"C\",\"methodName\":\"m";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(unterminated));
    }

    @Test
    void rejectsNonNumericLineNumber() {
        String badNumber = "{\"className\":\"C\",\"methodName\":\"m\",\"fileName\":\"F.java\","
                + "\"lineNumber\":\"oops\",\"lastSeenTimestampEpochMillis\":8}";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(badNumber));
    }

    @Test
    void rejectsBadEscapeSequence() {
        // \\x is not a valid JSON escape and must be rejected, not silently accepted
        String badEscape = "{\"className\":\"C\\x\",\"methodName\":\"m\",\"fileName\":null,"
                + "\"lineNumber\":1,\"lastSeenTimestampEpochMillis\":1}";

        assertThrows(IllegalArgumentException.class, () -> TraceJson.decode(badEscape));
    }

    // --- Value edge cases ---------------------------------------------------

    @Test
    void decodesUnicodeEscapeInString() {
        String withUnicode = "{\"className\":\"C\\u0041\",\"methodName\":\"m\",\"fileName\":null,"
                + "\"lineNumber\":1,\"lastSeenTimestampEpochMillis\":1}";

        assertEquals("CA", TraceJson.decode(withUnicode).getClassName());
    }

    @Test
    void decodesNegativeLineNumber() {
        // the JVM reports -1 for a frame with no line info; it must survive decode
        assertEquals(-1, TraceJson.decode(line("C", "m", null, -1, 5L)).getLineNumber());
    }
}
